package com.samuelcruz.ghichu.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.core.S
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SyncState(
    val enabled: Boolean = false,
    val email: String = "",
    val running: Boolean = false,
    /** Last error worded for the user, null when the last run went fine. */
    val error: String? = null,
    /** Google wants the user to sign in again (permission withdrawn). */
    val needsSignIn: Boolean = false,
    val lastSyncAt: Long = 0,
    val attachmentsPending: Int = 0,
    /** Drive files are end-to-end encrypted. */
    val encrypted: Boolean = false,
    /** Drive is encrypted and this phone needs the passphrase before it can sync. */
    val needsPassphrase: Boolean = false,
    /** Files this phone cannot decrypt (another passphrase). */
    val unreadable: Int = 0,
)

/**
 * Runs [SyncEngine] at the right moments: a few seconds after each change, when the app comes to the front,
 * every hour in the background (WorkManager, like a cron job), and when the network comes back after offline
 * edits. One run at a time.
 */
class SyncManager(private val app: GhiChuApp, private val scope: CoroutineScope) {
    private val prefs = app.prefs
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState(prefs.syncEnabled, prefs.syncEmail, lastSyncAt = prefs.lastSyncAt, encrypted = prefs.e2eOn))
    val state: StateFlow<SyncState> = _state

    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Notes open in an editor, with how many editors show each (following a `[[link]]` keeps the first editor
     * under the new one). Sync leaves them alone until they are closed.
     */
    private val openNotes = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun noteOpened(id: String) { openNotes.merge(id, 1, Int::plus) }
    fun noteClosed(id: String) { openNotes.computeIfPresent(id) { _, n -> if (n > 1) n - 1 else null } }
    private fun isOpen(id: String) = openNotes.containsKey(id)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { requests.debounce(3_000).collect { syncNow() } }
        if (prefs.syncEnabled) schedulePeriodic()
    }

    /** After a change: sync in a few seconds (several quick changes = one sync). */
    fun requestSoon() {
        if (prefs.syncEnabled) requests.tryEmit(Unit)
    }

    /** Leaving the app with unsent changes: WorkManager sends them as soon as there is a network. */
    fun flushInBackground() {
        if (!prefs.syncEnabled) return
        WorkManager.getInstance(app).enqueueUniqueWork(
            "sync-pending", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build(),
        )
    }

    private fun schedulePeriodic() {
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            "sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** First connection: [token] comes from the Google screen. */
    suspend fun connect(token: String) {
        val email = Drive(token).email().orEmpty()
        prefs.syncEmail = email
        prefs.syncEnabled = true
        _state.update { it.copy(enabled = true, email = email, needsSignIn = false, error = null) }
        schedulePeriodic()
        syncNow(token)
    }

    /** Stops syncing; the notes stay on the phone and in Drive. Connecting again merges both. */
    suspend fun disconnect() {
        val email = prefs.syncEmail
        prefs.syncEnabled = false
        prefs.syncEmail = ""
        prefs.lastSyncAt = 0
        WorkManager.getInstance(app).cancelUniqueWork("sync-periodic")
        WorkManager.getInstance(app).cancelUniqueWork("sync-pending")
        mutex.withLock {
            // Forget which Drive files rows match: a later connection (maybe another account) matches by file name.
            app.db.notes().forgetRemote()
            app.db.labels().forgetRemote()
            app.db.attachments().forgetRemote()
        }
        GoogleAuth.revoke(app, email)
        prefs.e2eOn = false
        _state.value = SyncState()
    }

    // End-to-end encryption (see Crypto). The key stays on the phone even when encryption is switched off, so files
    // still encrypted on Drive can be read and converted back.

    sealed interface CryptoResult {
        data object Ok : CryptoResult
        data object WrongPassphrase : CryptoResult
        /** Another phone already switched it on: unlock with its passphrase instead. */
        data object AlreadyOn : CryptoResult
        data class Failed(val message: String) : CryptoResult
    }

    private suspend fun withDrive(block: suspend (Drive) -> CryptoResult): CryptoResult {
        if (!prefs.syncEnabled) return CryptoResult.Failed(S.syncNotConnected)
        if (!online(app)) return CryptoResult.Failed(S.syncOffline)
        return try {
            val token = GoogleAuth.silentToken(app, prefs.syncEmail) ?: return CryptoResult.Failed(S.syncNeedsSignIn)
            mutex.withLock { block(Drive(token)) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            CryptoResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun config(drive: Drive): Pair<RemoteFile, Crypto.Config>? {
        val f = drive.list().firstOrNull { it.name == Crypto.CONFIG_FILE } ?: return null
        return f to SyncPlan.json.decodeFromString(Crypto.Config.serializer(), drive.download(f.id).decodeToString())
    }

    /** Switches encryption on with a new passphrase; the next sync encrypts everything already on Drive. */
    suspend fun enableEncryption(passphrase: String): CryptoResult {
        val r = withDrive { drive ->
            if (config(drive) != null) return@withDrive CryptoResult.AlreadyOn
            val salt = Crypto.newSalt()
            val key = withContext(Dispatchers.Default) { Crypto.deriveKey(passphrase, salt) }
            val cfg = Crypto.newConfig(key, salt)
            prefs.e2eKey = Crypto.keyToString(key)
            drive.create(Crypto.CONFIG_FILE, "application/json", SyncPlan.json.encodeToString(Crypto.Config.serializer(), cfg).encodeToByteArray())
            prefs.e2eOn = true
            CryptoResult.Ok
        }
        if (r == CryptoResult.Ok) { _state.update { it.copy(encrypted = true, needsPassphrase = false) }; syncNow() }
        return r
    }

    /** Another phone switched encryption on: derive the same key from its passphrase. */
    suspend fun unlockEncryption(passphrase: String): CryptoResult {
        val r = withDrive { drive ->
            val (_, cfg) = config(drive) ?: return@withDrive CryptoResult.Ok
            val key = withContext(Dispatchers.Default) { Crypto.unlock(passphrase, cfg) } ?: return@withDrive CryptoResult.WrongPassphrase
            prefs.e2eKey = Crypto.keyToString(key)
            prefs.e2eOn = true
            CryptoResult.Ok
        }
        if (r == CryptoResult.Ok) { _state.update { it.copy(needsPassphrase = false, error = null) }; syncNow() }
        return r
    }

    /** Switches encryption off (needs the passphrase); the next syncs store everything plain again. */
    suspend fun disableEncryption(passphrase: String): CryptoResult {
        val r = withDrive { drive ->
            val (file, cfg) = config(drive) ?: return@withDrive CryptoResult.Ok
            val key = withContext(Dispatchers.Default) { Crypto.unlock(passphrase, cfg) } ?: return@withDrive CryptoResult.WrongPassphrase
            prefs.e2eKey = Crypto.keyToString(key)
            drive.delete(file.id)
            prefs.e2eOn = false
            CryptoResult.Ok
        }
        if (r == CryptoResult.Ok) { _state.update { it.copy(encrypted = false, needsPassphrase = false) }; syncNow() }
        return r
    }

    /** Runs a sync now; returns true when it went through. */
    suspend fun syncNow(givenToken: String? = null): Boolean {
        if (!prefs.syncEnabled) return false
        if (!online(app)) {
            _state.update { it.copy(error = S.syncOffline) }
            flushInBackground()
            return false
        }
        return mutex.withLock {
            _state.update { it.copy(running = true) }
            try {
                var token = givenToken ?: GoogleAuth.silentToken(app, prefs.syncEmail)
                if (token == null) {
                    _state.update { it.copy(running = false, needsSignIn = true, error = S.syncNeedsSignIn) }
                    return@withLock false
                }
                val engine = SyncEngine(app, app.db, app.repo, prefs.deviceId, Crypto.keyFromString(prefs.e2eKey))
                val allowed = !prefs.attachmentsWifiOnly || unmetered(app)
                val report = try {
                    engine.run(Drive(token), allowed) { isOpen(it) }
                } catch (e: DriveException) {
                    if (e.code != 401) throw e
                    GoogleAuth.clearToken(app, token)
                    token = GoogleAuth.silentToken(app, prefs.syncEmail) ?: run {
                        _state.update { it.copy(running = false, needsSignIn = true, error = S.syncNeedsSignIn) }
                        return@withLock false
                    }
                    engine.run(Drive(token), allowed) { isOpen(it) }
                }
                val now = System.currentTimeMillis()
                prefs.lastSyncAt = now
                prefs.e2eOn = report.encrypted
                _state.update {
                    it.copy(
                        running = false, error = null, needsSignIn = false, lastSyncAt = now, attachmentsPending = report.attachmentsPending,
                        encrypted = report.encrypted, needsPassphrase = false, unreadable = report.unreadable,
                    )
                }
                app.afterSync(report)
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is NeedsPassphrase) {
                    prefs.e2eOn = true
                    _state.update { it.copy(running = false, encrypted = true, needsPassphrase = true, error = S.syncNeedsPassphrase) }
                    return@withLock false
                }
                val msg = when (e) {
                    is DriveException -> if (e.code == 403) S.syncForbidden else S.syncError("HTTP ${e.code}")
                    is IOException -> S.syncOffline
                    is com.google.android.gms.common.api.ApiException -> S.syncSetupError(e.statusCode)
                    else -> S.syncError(e.message ?: e.javaClass.simpleName)
                }
                _state.update { it.copy(running = false, error = msg) }
                if (e is IOException) flushInBackground()
                false
            }
        }
    }

    /** Google screen needed again (after [SyncState.needsSignIn]): the caller starts it and calls [connect]. */
    fun markSignedIn() = _state.update { it.copy(needsSignIn = false) }

    companion object {
        fun online(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun unmetered(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }
}

/** Background sync (hourly, and after offline edits once the network is back). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GhiChuApp
        if (!app.prefs.syncEnabled) return Result.success()
        return if (app.sync.syncNow() || app.sync.state.value.needsSignIn || app.sync.state.value.needsPassphrase) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}

package com.samuelcruz.ghichu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.samuelcruz.ghichu.core.AppLock
import com.samuelcruz.ghichu.core.I18n
import com.samuelcruz.ghichu.core.Prefs
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.AppDatabase
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.Repo
import com.samuelcruz.ghichu.sync.SyncManager
import com.samuelcruz.ghichu.sync.SyncReport
import com.samuelcruz.ghichu.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A message for the snackbar, optionally with a button (e.g. "Hoàn tác" running [action]). */
data class UiEvent(val message: String, val actionLabel: String? = null, val action: (suspend () -> Unit)? = null)

/** Holds the long-lived objects (like a Rails initializer). No DI library: there are only a few. */
class GhiChuApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val repo: Repo by lazy { Repo(this, db) }
    /** For work that must finish even if the screen that started it closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sync: SyncManager by lazy { SyncManager(this, scope) }

    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)

    /** Observable copies of settings that redraw the whole app when changed. */
    var themeMode by mutableIntStateOf(Prefs.THEME_SYSTEM)
        private set
    var fontSize by mutableIntStateOf(1)
        private set

    /**
     * When locked notes were last unlocked (elapsedRealtime, 0 = not). One fingerprint opens all locked notes
     * until the app has been in the background for [AppLock.GRACE_MILLIS].
     */
    var notesUnlockedAt by mutableLongStateOf(0L)
    private var backgroundSince = 0L

    private var widgetJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        I18n.set(prefs.language)
        themeMode = prefs.theme
        fontSize = prefs.fontSize
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Reminders.CHANNEL, S.reminderChannel, NotificationManager.IMPORTANCE_HIGH),
        )
        repo.onChange = ::notesChangedHere
        repo.onLabelsChange = { sync.requestSoon() }
        sync.start()
        if (prefs.autoBackupDir.isNotEmpty()) com.samuelcruz.ghichu.data.AutoBackup.schedule(this)
        updateShortcuts()
        // Redraw widgets with this version's code: after an app update the home screen may still show pictures
        // drawn by the old version (also runs after MY_PACKAGE_REPLACED, which starts the process).
        refreshWidgets()
        scope.launch {
            runCatching { repo.purgeOldTrash() }
            runCatching { Reminders.updateAll(this@GhiChuApp, db.notes().withReminders()) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (backgroundSince != 0L && SystemClock.elapsedRealtime() - backgroundSince > AppLock.GRACE_MILLIS) {
                    notesUnlockedAt = 0L
                }
                scope.launch { sync.syncNow() }
            }

            override fun onStop(owner: LifecycleOwner) {
                backgroundSince = SystemClock.elapsedRealtime()
                scope.launch {
                    if (db.notes().observeDirtyCount().first() > 0 || db.labels().observeDirtyCount().first() > 0) {
                        sync.flushInBackground()
                    }
                }
            }
        })
    }

    private fun notesChangedHere(notes: List<Note>) {
        notes.forEach { Reminders.update(this, it) }
        refreshWidgets()
        sync.requestSoon()
    }

    /** Called by the sync after it wrote notes from Drive. */
    fun afterSync(report: SyncReport) {
        report.changedNotes.forEach { Reminders.update(this, it) }
        if (report.changedNotes.isNotEmpty() || report.labelsChanged) refreshWidgets()
        if (report.conflicts > 0) events.tryEmit(UiEvent(S.conflictsKept(report.conflicts)))
    }

    /** Widgets redraw at most every second or so (typing in a note changes it many times). */
    fun refreshWidgets() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(1200)
            runCatching { Widgets.updateAll(this@GhiChuApp) }
        }
    }

    fun changeTheme(value: Int) {
        prefs.theme = value
        themeMode = value
    }

    fun changeFontSize(value: Int) {
        prefs.fontSize = value
        fontSize = value
    }

    /** Long-press the launcher icon: today's note / new note / checklist / voice note / photo, in the app's language. */
    fun updateShortcuts() {
        fun shortcut(id: String, label: String, action: String, icon: Int) = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(this, icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(action))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(
            this,
            listOf(
                shortcut("today", S.todayNote, MainActivity.ACTION_TODAY, R.drawable.ic_sc_today),
                shortcut("new_text", S.newNote, MainActivity.ACTION_NEW_TEXT, R.drawable.ic_sc_note),
                shortcut("new_list", S.newChecklist, MainActivity.ACTION_NEW_LIST, R.drawable.ic_sc_list),
                shortcut("new_voice", S.newVoice, MainActivity.ACTION_NEW_VOICE, R.drawable.ic_sc_mic),
                shortcut("new_photo", S.newPhoto, MainActivity.ACTION_NEW_PHOTO, R.drawable.ic_sc_photo),
            ),
        )
    }
}

/** Version name from the installed package (BuildConfig is off in this project). */
object BuildConfigInfo {
    fun versionName(context: android.content.Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}

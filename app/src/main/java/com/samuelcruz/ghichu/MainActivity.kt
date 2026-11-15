package com.samuelcruz.ghichu

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.samuelcruz.ghichu.core.AppLock
import com.samuelcruz.ghichu.core.Prefs
import com.samuelcruz.ghichu.data.NoteKind
import com.samuelcruz.ghichu.ui.AppNav
import com.samuelcruz.ghichu.ui.GhiChuTheme
import com.samuelcruz.ghichu.ui.Launch
import com.samuelcruz.ghichu.ui.LockScreen
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val app by lazy { application as GhiChuApp }
    private var locked by mutableStateOf(false)
    private var launch by mutableStateOf<Launch?>(null)
    /** When the app last went to the background (0 = not since start). */
    private var stoppedAt = 0L

    /** Gives system parts (date picker, fingerprint dialog) the app's language instead of the phone's. */
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(Prefs(base).language))
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = app.prefs.lockEnabled
        if (savedInstanceState == null) launch = read(intent)
        setContent {
            GhiChuTheme(app.themeMode, app.fontSize) {
                Box(Modifier.fillMaxSize()) {
                    AppNav(launch, onLaunchHandled = { launch = null })
                    if (locked) LockScreen(onUnlock = ::unlock)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)?.let { launch = it }
    }

    /** What the intent asks for: open a note, start a new one, or a note from shared text/photos. */
    private fun read(intent: Intent?): Launch? {
        intent ?: return null
        return when (intent.action) {
            ACTION_OPEN -> intent.getStringExtra(EXTRA_NOTE)?.let { Launch.Open(it) }
            ACTION_NEW_TEXT -> Launch.New(NoteKind.TEXT)
            ACTION_NEW_LIST -> Launch.New(NoteKind.CHECKLIST)
            ACTION_NEW_VOICE -> Launch.New(NoteKind.TEXT, "voice")
            ACTION_NEW_PHOTO -> Launch.New(NoteKind.TEXT, "camera")
            ACTION_NEW_RECORD -> Launch.New(NoteKind.TEXT, "record")
            ACTION_SEARCH -> Launch.Search
            ACTION_TODAY -> Launch.Today
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                val images = buildList {
                    if (intent.type?.startsWith("image/") == true) {
                        if (intent.action == Intent.ACTION_SEND) {
                            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { add(it) }
                        } else {
                            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { addAll(it) }
                        }
                    }
                }
                if (text.isBlank() && subject.isBlank() && images.isEmpty()) null
                else Launch.Shared(subject.take(200), text, images)
            }
            else -> null
        }
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (app.prefs.lockEnabled && stoppedAt != 0L && away > AppLock.GRACE_MILLIS) locked = true
    }

    override fun onResume() {
        super.onResume()
        // With the lock on, the recent-apps screen shows a blank card instead of the notes (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!app.prefs.lockEnabled)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun unlock() = AppLock.authenticate(this, onSuccess = {
        locked = false
        // Unlocking the app also opens locked notes for this visit.
        app.notesUnlockedAt = SystemClock.elapsedRealtime()
    })

    companion object {
        const val ACTION_OPEN = "com.samuelcruz.ghichu.OPEN"
        const val ACTION_NEW_TEXT = "com.samuelcruz.ghichu.NEW_TEXT"
        const val ACTION_NEW_LIST = "com.samuelcruz.ghichu.NEW_LIST"
        const val ACTION_NEW_VOICE = "com.samuelcruz.ghichu.NEW_VOICE"
        const val ACTION_NEW_PHOTO = "com.samuelcruz.ghichu.NEW_PHOTO"
        const val ACTION_NEW_RECORD = "com.samuelcruz.ghichu.NEW_RECORD"
        const val ACTION_SEARCH = "com.samuelcruz.ghichu.SEARCH"
        const val ACTION_TODAY = "com.samuelcruz.ghichu.TODAY"
        const val EXTRA_NOTE = "note"
    }
}

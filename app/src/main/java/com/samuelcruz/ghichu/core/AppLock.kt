package com.samuelcruz.ghichu.core

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.edit
import java.util.UUID

/** Small settings kept in SharedPreferences (a key/value file, like a tiny settings table). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = sp.getBoolean("lock_enabled", false)
        set(value) = sp.edit { putBoolean("lock_enabled", value) }

    /** [THEME_SYSTEM], [THEME_LIGHT] or [THEME_DARK]. */
    var theme: Int
        get() = sp.getInt("theme", THEME_SYSTEM)
        set(value) = sp.edit { putInt("theme", value) }

    /** "vi" or "en". */
    var language: String
        get() = sp.getString("language", "vi") ?: "vi"
        set(value) = sp.edit { putString("language", value) }

    /** Notes shown as a two-column grid (true) or a single list. */
    var grid: Boolean
        get() = sp.getBoolean("grid", true)
        set(value) = sp.edit { putBoolean("grid", value) }

    /** A `Sort` constant. */
    var sort: Int
        get() = sp.getInt("sort", 0)
        set(value) = sp.edit { putInt("sort", value) }

    /** Text size of notes: 0 small, 1 normal, 2 large, 3 extra large. */
    var fontSize: Int
        get() = sp.getInt("font_size", 1)
        set(value) = sp.edit { putInt("font_size", value) }

    /** Ticked checklist items move to the bottom. */
    var checkedLast: Boolean
        get() = sp.getBoolean("checked_last", true)
        set(value) = sp.edit { putBoolean("checked_last", value) }

    /** Google Drive sync switched on (the account is remembered by Google Play services). */
    var syncEnabled: Boolean
        get() = sp.getBoolean("sync_enabled", false)
        set(value) = sp.edit { putBoolean("sync_enabled", value) }

    var syncEmail: String
        get() = sp.getString("sync_email", "") ?: ""
        set(value) = sp.edit { putString("sync_email", value) }

    var lastSyncAt: Long
        get() = sp.getLong("last_sync_at", 0)
        set(value) = sp.edit { putLong("last_sync_at", value) }

    /** Photos and recordings are uploaded/downloaded only on Wi-Fi (notes themselves always sync). */
    var attachmentsWifiOnly: Boolean
        get() = sp.getBoolean("attachments_wifi_only", false)
        set(value) = sp.edit { putBoolean("attachments_wifi_only", value) }

    /** End-to-end key for Drive (Base64, "" = none). Derived from the passphrase, which itself is never stored. */
    var e2eKey: String
        get() = sp.getString("e2e_key", "") ?: ""
        set(value) = sp.edit { putString("e2e_key", value) }

    /** Drive was end-to-end encrypted at the last sync (for the Settings screen). */
    var e2eOn: Boolean
        get() = sp.getBoolean("e2e_on", false)
        set(value) = sp.edit { putBoolean("e2e_on", value) }

    /** Random id of this install, used in conflict copies ("from another device"). */
    val deviceId: String
        get() = sp.getString("device_id", null) ?: UUID.randomUUID().toString().take(8).also {
            sp.edit { putString("device_id", it) }
        }

    /** Template used for "today's note" ("" = none, the note starts empty). */
    var dailyTemplate: String
        get() = sp.getString("daily_template", "") ?: ""
        set(value) = sp.edit { putString("daily_template", value) }

    /** Folder (SAF tree uri) for the weekly automatic backup; "" = off. */
    var autoBackupDir: String
        get() = sp.getString("auto_backup_dir", "") ?: ""
        set(value) = sp.edit { putString("auto_backup_dir", value) }

    var autoBackupLast: Long
        get() = sp.getLong("auto_backup_last", 0)
        set(value) = sp.edit { putLong("auto_backup_last", value) }

    /** Last automatic backup failed (folder gone or no longer allowed). */
    var autoBackupFailed: Boolean
        get() = sp.getBoolean("auto_backup_failed", false)
        set(value) = sp.edit { putBoolean("auto_backup_failed", value) }

    /** What a note-list widget shows: "pinned", "all" or "label:<id>". */
    fun widgetList(widgetId: Int): String = sp.getString("widget_list_$widgetId", "pinned") ?: "pinned"
    fun setWidgetList(widgetId: Int, source: String) = sp.edit { putString("widget_list_$widgetId", source) }

    /** Note shown by a home-screen widget. */
    fun widgetNote(widgetId: Int): String? = sp.getString("widget_$widgetId", null)
    fun setWidgetNote(widgetId: Int, noteId: String?) = sp.edit {
        if (noteId == null) remove("widget_$widgetId") else putString("widget_$widgetId", noteId)
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}

/**
 * Fingerprint / face / screen-lock PIN using Android's own prompt (no library). The phone's screen lock is
 * always accepted as a fallback, so the user can never be locked out by a broken sensor.
 */
object AppLock {
    /** Time in the background before the app (and locked notes) ask again. */
    const val GRACE_MILLIS = 30_000L

    /** The phone must have a screen lock, otherwise there is nothing to check against. */
    fun canLock(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(BiometricManager::class.java)
                .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            context.getSystemService(KeyguardManager::class.java).isDeviceSecure
        }

    fun authenticate(activity: Activity, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(S.promptTitle)
            .setSubtitle(S.promptSubtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
            },
        )
    }
}

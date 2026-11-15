package com.samuelcruz.ghichu.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.samuelcruz.ghichu.GhiChuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Weekly automatic backup (like a cron job): the same `.zip` as "Sao lưu ra file", written into a folder the user
 * picked once (Android's folder picker grants lasting access to it). Only the newest [KEEP] automatic backups
 * are kept there; other files in the folder are never touched.
 */
object AutoBackup {
    const val KEEP = 5
    const val PREFIX = "ghichu-auto-"
    private const val WORK = "auto-backup"
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    /** Turns it on for [tree] (from the folder picker), keeping access to the folder after a reboot. */
    fun enable(context: Context, tree: Uri) {
        val app = context.applicationContext as GhiChuApp
        context.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val old = app.prefs.autoBackupDir
        if (old.isNotEmpty() && old != tree.toString()) release(context, old)
        app.prefs.autoBackupDir = tree.toString()
        app.prefs.autoBackupFailed = false
        schedule(context)
    }

    fun disable(context: Context) {
        val app = context.applicationContext as GhiChuApp
        release(context, app.prefs.autoBackupDir)
        app.prefs.autoBackupDir = ""
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    private fun release(context: Context, uri: String) {
        if (uri.isEmpty()) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** Every 7 days, when the battery is not low. KEEP: re-scheduling on each start does not reset the timer. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    /** Folder name to show in Settings. */
    fun folderName(context: Context, uri: String): String =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name }.getOrNull()
            ?: Uri.parse(uri).lastPathSegment?.substringAfterLast(':').orEmpty()

    /** Writes one backup now. Returns the number of notes, or throws (folder gone / no access). */
    suspend fun run(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext as GhiChuApp
        val dirUri = app.prefs.autoBackupDir
        check(dirUri.isNotEmpty()) { "Off" }
        try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(dirUri))
            check(dir != null && dir.canWrite()) { "No access to the folder" }
            val name = PREFIX + LocalDateTime.now().format(stamp) + ".zip"
            val file = dir.createFile("application/zip", name) ?: error("Cannot create $name")
            val count = context.contentResolver.openOutputStream(file.uri)!!.use {
                Backup.write(context, app.db, it, app.prefs.deviceId)
            }
            // Oldest automatic backups beyond KEEP are deleted (names sort by date).
            dir.listFiles().filter { it.name.orEmpty().startsWith(PREFIX) }
                .sortedByDescending { it.name }
                .drop(KEEP)
                .forEach { it.delete() }
            app.prefs.autoBackupLast = System.currentTimeMillis()
            app.prefs.autoBackupFailed = false
            count
        } catch (e: Exception) {
            app.prefs.autoBackupFailed = true
            throw e
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GhiChuApp
        if (app.prefs.autoBackupDir.isEmpty()) return Result.success()
        return runCatching { AutoBackup.run(applicationContext) }.fold({ Result.success() }, { Result.failure() })
    }
}

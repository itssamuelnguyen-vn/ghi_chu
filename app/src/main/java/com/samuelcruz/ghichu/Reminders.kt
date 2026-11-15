package com.samuelcruz.ghichu

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reminder alarms (AlarmManager, exact to the minute, also in power-saving mode) and their notifications with
 * "Xong" / "Báo lại" buttons. Alarms are kept in step with the notes: after every change, after a sync, after a
 * reboot.
 */
object Reminders {
    const val CHANNEL = "reminders"
    private const val ACTION_FIRE = "com.samuelcruz.ghichu.REMINDER"
    private const val ACTION_DONE = "com.samuelcruz.ghichu.REMINDER_DONE"
    private const val ACTION_SNOOZE = "com.samuelcruz.ghichu.REMINDER_SNOOZE"
    private const val EXTRA_ID = "id"
    private const val EXTRA_SNOOZED = "snoozed"
    private const val EXTRA_MINUTES = "minutes"

    private fun uri(id: String) = Uri.parse("ghichu://reminder/$id")

    private fun fireIntent(context: Context, id: String, snoozed: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).setData(uri(id))
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_SNOOZED, snoozed),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Sets or cancels the alarm of [note] to match it. */
    fun update(context: Context, note: Note) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = fireIntent(context, note.id)
        val at = note.reminderAt
        val next = if (at == null || note.deleted || note.inTrash) null
        else Repeat.next(at, note.reminderRepeat, System.currentTimeMillis())
        if (next == null) {
            am.cancel(pi)
            return
        }
        setAlarm(am, next, pi)
    }

    fun updateAll(context: Context, notes: List<Note>) = notes.forEach { update(context, it) }

    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun dismiss(context: Context, id: String) = NotificationManagerCompat.from(context).cancel(id.hashCode())

    internal suspend fun handle(context: Context, intent: Intent) {
        val app = context.applicationContext as GhiChuApp
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        when (intent.action) {
            ACTION_FIRE -> {
                val note = app.db.notes().get(id) ?: return
                if (note.deleted || note.inTrash || note.reminderAt == null) return
                show(context, note)
                // Repeating: set the next one (a minute on, so this very time is not picked again).
                if (note.reminderRepeat != Repeat.NONE && !intent.getBooleanExtra(EXTRA_SNOOZED, false)) {
                    val next = Repeat.next(note.reminderAt, note.reminderRepeat, System.currentTimeMillis() + 60_000)
                    if (next != null) setAlarm(context.getSystemService(AlarmManager::class.java), next, fireIntent(context, id))
                }
            }
            ACTION_DONE -> {
                dismiss(context, id)
                val note = app.db.notes().get(id)
                // A one-time reminder is finished; a repeating one just waits for its next time.
                if (note != null && note.reminderRepeat == Repeat.NONE) app.repo.update(id) { it.copy(reminderAt = null) }
            }
            ACTION_SNOOZE -> {
                dismiss(context, id)
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
                setAlarm(
                    context.getSystemService(AlarmManager::class.java),
                    System.currentTimeMillis() + minutes * 60_000L,
                    fireIntent(context, id, snoozed = true),
                )
            }
        }
    }

    private fun show(context: Context, note: Note) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        fun action(action: String, requestCode: Int, minutes: Int = 0) = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action).setData(uri(note.id))
                .putExtra(EXTRA_ID, note.id).putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, note.id.hashCode(),
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN).setData(uri(note.id))
                .putExtra(MainActivity.EXTRA_NOTE, note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (note.locked) S.lockedNote else Markdown.plain(note.body).take(400)
        val title = note.displayTitle().ifBlank { S.reminderTitle }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setColor(0xFFD97706.toInt())
            .addAction(0, S.done, action(ACTION_DONE, 1))
            .addAction(0, S.snooze10, action(ACTION_SNOOZE, 2, 10))
            .addAction(0, S.snooze60, action(ACTION_SNOOZE, 3, 60))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(note.id.hashCode(), n)
    }
}

/** Alarm went off, or a button on the reminder notification was pressed. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { Reminders.handle(context, intent) } finally { pending.finish() }
        }
    }
}

/** After a reboot or a clock/time-zone change Android forgets alarms: set them all again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as GhiChuApp
                Reminders.updateAll(context, app.db.notes().withReminders())
            } finally { pending.finish() }
        }
    }
}

package com.samuelcruz.ghichu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.core.S
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A ViewModel built with the app; it lives as long as its screen (its navigation entry). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, noinline create: (GhiChuApp) -> VM): VM {
    val app = LocalContext.current.applicationContext as GhiChuApp
    return viewModel(key = key) { create(app) }
}

@Composable
fun app(): GhiChuApp = LocalContext.current.applicationContext as GhiChuApp

private val hm = DateTimeFormatter.ofPattern("HH:mm")

fun toDateTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

/** "Hôm nay, 14:30", "Mai, 08:00", "T2 12/11, 08:00" (with the year when it is not this year). */
fun whenLabel(millis: Long, now: LocalDate = LocalDate.now()): String {
    val dt = toDateTime(millis)
    val d = dt.toLocalDate()
    val time = dt.format(hm)
    return when (d) {
        now -> S.today + ", " + time
        now.plusDays(1) -> S.tomorrow + ", " + time
        now.minusDays(1) -> S.yesterday + ", " + time
        else -> S.shortDate(d, d.year != now.year) + ", " + time
    }
}

/** Reminder chip text: next time for a repeating reminder, plus how it repeats. */
fun reminderLabel(at: Long, repeat: Int): String {
    val next = com.samuelcruz.ghichu.core.Repeat.next(at, repeat, System.currentTimeMillis(), includePast = true) ?: at
    val base = whenLabel(next)
    return if (repeat == com.samuelcruz.ghichu.core.Repeat.NONE) base else base + " · " + S.repeatName(repeat)
}

/** "Đã sửa 14:20" / "Đã sửa 3 thg 11". */
fun editedLabel(millis: Long): String {
    val dt = toDateTime(millis)
    val today = LocalDate.now()
    return if (dt.toLocalDate() == today) S.editedAt(dt.format(hm)) else S.editedAt(S.shortDate(dt.toLocalDate(), dt.year != today.year))
}

fun fullDateTime(millis: Long): String {
    val dt = toDateTime(millis)
    return S.shortDate(dt.toLocalDate(), true) + ", " + dt.format(hm)
}

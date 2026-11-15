package com.samuelcruz.ghichu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Note
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One time a reminder fires. */
private data class Fire(val at: Long, val note: Note)

/**
 * Reminders on a month grid: a dot under each day with reminders (repeating ones on every day they fire), and
 * the list of the chosen day below. Tapping one opens the note.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val app = app()
    val notes by app.db.notes().observeAll().collectAsState(emptyList())
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    val byDay: Map<LocalDate, List<Fire>> = remember(notes, monthText) {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        notes.filter { !it.inTrash && it.reminderAt != null }
            .flatMap { n -> Repeat.between(n.reminderAt!!, n.reminderRepeat, from, to, zone).map { Fire(it, n) } }
            .sortedBy { it.at }
            .groupBy { toDateTime(it.at).toLocalDate() }
    }
    val cs = MaterialTheme.colorScheme

    fun goTo(m: YearMonth) {
        monthText = m.toString()
        selectedText = (if (m == YearMonth.from(today)) today else m.atDay(1)).toString()
    }

    SimpleScaffold(S.calendar, onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { goTo(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, S.prevMonth) }
                Text(
                    S.monthTitle(month.monthValue, month.year),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                if (month != YearMonth.from(today)) TextButton(onClick = { goTo(YearMonth.from(today)) }) { Text(S.today) }
                IconButton(onClick = { goTo(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, S.nextMonth) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (d in 1..7) {
                    Text(
                        S.weekdayShort(d), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            Column(Modifier.padding(horizontal = 12.dp)) {
                for (week in 0 until (cells + 6) / 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (dow in 0 until 7) {
                            val dayNum = week * 7 + dow - offset + 1
                            if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                                Spacer(Modifier.weight(1f))
                                continue
                            }
                            val date = month.atDay(dayNum)
                            val fires = byDay[date].orEmpty()
                            val isSel = date == selected
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(3.dp).clip(CircleShape)
                                    .then(if (isSel) Modifier.background(cs.primary) else Modifier)
                                    .then(if (date == today && !isSel) Modifier.border(1.5.dp, cs.primary, CircleShape) else Modifier)
                                    .clickable { selectedText = date.toString() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayNum.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (fires.isNotEmpty()) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSel) cs.onPrimary else cs.onSurface,
                                )
                                if (fires.isNotEmpty()) {
                                    Row(
                                        Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        repeat(minOf(3, fires.size)) {
                                            Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSel) cs.onPrimary else cs.primary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            val fires = byDay[selected].orEmpty()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        S.shortDate(selected, selected.year != today.year),
                        style = MaterialTheme.typography.labelLarge, color = cs.primary,
                    )
                }
                if (fires.isEmpty()) {
                    item { Text(S.noRemindersDay, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                }
                items(fires, key = { it.note.id + "@" + it.at }) { f ->
                    SoftSurface(Modifier.fillMaxWidth().clickable { onOpen(f.note.id) }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (f.note.reminderRepeat != Repeat.NONE) Icons.Default.Repeat else Icons.Default.Alarm, null,
                                tint = cs.primary, modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(toDateTime(f.at).format(hm), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (f.note.locked && f.note.title.isBlank()) S.lockedNote else f.note.displayTitle().ifBlank { S.untitled },
                                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (f.note.reminderRepeat != Repeat.NONE) {
                                    Text(S.repeatName(f.note.reminderRepeat), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val hm = DateTimeFormatter.ofPattern("HH:mm")

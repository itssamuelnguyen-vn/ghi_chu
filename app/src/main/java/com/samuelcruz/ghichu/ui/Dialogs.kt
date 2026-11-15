package com.samuelcruz.ghichu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.core.fold
import com.samuelcruz.ghichu.data.Label
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Tick the labels of the selected note(s); typing a name that does not exist offers "Tạo nhãn". [onDone] gets
 * the labels to add and to remove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPicker(
    labels: List<Label>,
    initiallyOn: Set<String>,
    onDone: (add: Set<String>, remove: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = app()
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(initiallyOn) }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val shown = labels.filter { q.isEmpty() || it.name.fold().contains(q.fold()) }
    val exact = labels.any { it.name.equals(q, ignoreCase = true) }
    fun finish() = onDone(on - initiallyOn, initiallyOn - on)
    ModalBottomSheet(onDismissRequest = { finish() }) {
        Column(Modifier.padding(horizontal = 20.dp).imePadding()) {
            Text(S.labels, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(S.labelSearchHint) }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (q.isNotEmpty() && !exact) scope.launch { val l = app.repo.createLabel(q); on = on + l.id; query = "" }
                }),
            )
            if (q.isNotEmpty() && !exact) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        scope.launch { val l = app.repo.createLabel(q); on = on + l.id; query = "" }
                    }.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(S.createLabelNamed(q), color = MaterialTheme.colorScheme.primary)
                }
            }
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(shown, key = { it.id }) { l ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { on = if (l.id in on) on - l.id else on + l.id }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Text(l.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Checkbox(checked = l.id in on, onCheckedChange = { c -> on = if (c) on + l.id else on - l.id })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { finish() }) { Text(S.done) }
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

/** Pick when to be reminded: quick choices, or a date + time, and how it repeats. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderDialog(
    initialAt: Long?,
    initialRepeat: Int,
    onSet: (at: Long, repeat: Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    val start = initialAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        ?: now.toLocalDate().plusDays(1).atTime(8, 0)
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    var repeat by remember { mutableStateOf(initialRepeat) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }

    fun at(d: LocalDate, t: LocalTime) = d.atTime(t).atZone(zone).toInstant().toEpochMilli()

    val quick = buildList {
        val today = now.toLocalDate()
        if (now.toLocalTime() < LocalTime.of(17, 0)) add(S.laterToday to (today to LocalTime.of(18, 0)))
        else if (now.toLocalTime() < LocalTime.of(19, 30)) add(S.tonight to (today to LocalTime.of(20, 0)))
        add(S.tomorrowMorning to (today.plusDays(1) to LocalTime.of(8, 0)))
        add(S.nextWeek to (today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) to LocalTime.of(8, 0)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.reminder) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { (label, dt) ->
                        AssistChip(onClick = { onSet(at(dt.first, dt.second), repeat) }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                PickerRow(Icons.Default.CalendarMonth, S.shortDate(date, date.year != now.year)) { pickDate = true }
                PickerRow(Icons.Default.Schedule, time.format(timeFmt)) { pickTime = true }
                Column {
                    PickerRow(Icons.Default.Repeat, S.repeatName(repeat)) { repeatMenu = true }
                    DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                        Repeat.all.forEach { r ->
                            DropdownMenuItem(text = { Text(S.repeatName(r)) }, onClick = { repeat = r; repeatMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(at(date, time), repeat) }) { Text(S.save) } },
        dismissButton = {
            Row {
                if (initialAt != null) TextButton(onClick = onRemove) { Text(S.removeReminder, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(S.cancel) }
            }
        },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(S.ok) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(S.cancel) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text(S.ok) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(S.cancel) } },
        )
    }
}

@Composable
private fun PickerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Simple "type a name" dialog (new / rename label). */
@Composable
fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text) }),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onDone(text) }) { Text(S.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}

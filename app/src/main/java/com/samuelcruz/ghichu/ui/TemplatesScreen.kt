package com.samuelcruz.ghichu.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghichu.UiEvent
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.NoteKind
import kotlinx.coroutines.launch

/** "Mẫu ghi chú": make, edit and delete templates. A template is edited in the normal editor. */
@Composable
fun TemplatesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val templates by app.db.notes().observeTemplates().collectAsState(emptyList())

    fun create(kind: Int) = scope.launch { onOpen(app.repo.create(kind, template = true).id) }

    SimpleScaffold(S.templates, onBack, actions = {
        IconButton(onClick = { create(NoteKind.TEXT) }) { Icon(Icons.Default.Add, S.newTemplate) }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(S.templatesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { create(NoteKind.TEXT) }) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(S.newTemplate)
                    }
                    TextButton(onClick = { create(NoteKind.CHECKLIST) }) {
                        Icon(Icons.Default.CheckBox, null); Spacer(Modifier.width(6.dp)); Text(S.newChecklistTemplate)
                    }
                }
            }
            if (templates.isEmpty()) item { EmptyState(Icons.Outlined.Description, S.noTemplates, S.noTemplatesText) }
            items(templates, key = { it.id }) { t ->
                TemplateRow(t, onClick = { onOpen(t.id) }, trailing = {
                    IconButton(onClick = { onOpen(t.id) }) { Icon(Icons.Default.Edit, S.edit) }
                    IconButton(onClick = {
                        scope.launch {
                            app.repo.trash(listOf(t.id))
                            app.events.emit(UiEvent(S.templateDeleted, S.undo) { app.repo.restore(listOf(t.id)) })
                        }
                    }) { Icon(Icons.Default.Delete, S.delete) }
                })
            }
        }
    }
}

@Composable
private fun TemplateRow(t: Note, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (t.kind == NoteKind.CHECKLIST) Icons.Default.CheckBox else Icons.Outlined.Description, null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.displayTitle().ifBlank { S.untitled }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val preview = Markdown.plain(t.body).lineSequence().filter { it.isNotBlank() }.drop(if (t.title.isBlank()) 1 else 0).firstOrNull()
            if (!preview.isNullOrBlank()) {
                Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** "+" → "Từ mẫu": pick a template; a new note is made from it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePicker(onPick: (Note) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val app = app()
    val templates by app.db.notes().observeTemplates().collectAsState(emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.fromTemplate, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onManage) {
                Icon(Icons.Default.Settings, null); Spacer(Modifier.width(6.dp)); Text(S.manageTemplates)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (templates.isEmpty()) {
                Text(S.noTemplatesText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onManage) { Text(S.newTemplate) }
            }
            templates.take(30).forEach { t -> TemplateRow(t, onClick = { onPick(t) }) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

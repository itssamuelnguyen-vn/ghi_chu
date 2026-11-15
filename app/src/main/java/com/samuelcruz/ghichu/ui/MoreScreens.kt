package com.samuelcruz.ghichu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Label
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.NoteKind
import com.samuelcruz.ghichu.data.Revision
import kotlinx.coroutines.launch
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import com.samuelcruz.ghichu.core.Diff

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        floatingActionButton = fab,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/** Create, rename and delete labels. */
@Composable
fun LabelsScreen(onBack: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val labels by app.db.labels().observeAll().collectAsState(emptyList())
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Label?>(null) }
    var deleting by remember { mutableStateOf<Label?>(null) }
    SimpleScaffold(S.editLabels, onBack, actions = {
        IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, S.createLabel) }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { creating = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(S.createLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (labels.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.Label, S.noLabels, S.noLabelsText) }
            items(labels, key = { it.id }) { l ->
                SoftSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(l.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { renaming = l }) { Icon(Icons.Default.Edit, S.rename) }
                        IconButton(onClick = { deleting = l }) { Icon(Icons.Default.Delete, S.delete) }
                    }
                }
            }
        }
    }
    if (creating) NameDialog(S.createLabel, "", onDone = { n -> scope.launch { app.repo.createLabel(n) }; creating = false }, onDismiss = { creating = false })
    renaming?.let { l -> NameDialog(S.rename, l.name, onDone = { n -> scope.launch { app.repo.renameLabel(l.id, n) }; renaming = null }, onDismiss = { renaming = null }) }
    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(S.deleteLabelTitle(l.name)) },
            text = { Text(S.deleteLabelText) },
            confirmButton = { TextButton(onClick = { scope.launch { app.repo.deleteLabel(l.id) }; deleting = null }) { Text(S.delete) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(S.cancel) } },
        )
    }
}

/** Earlier versions of a note (saved each time it was closed after a change, and before sync overwrote it). */
@Composable
fun HistoryScreen(id: String, onBack: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val revisions by app.db.revisions().observeFor(id).collectAsState(emptyList())
    var preview by remember { mutableStateOf<Revision?>(null) }
    SimpleScaffold(S.history, onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (revisions.isEmpty()) item { EmptyState(Icons.Default.History, S.noHistory, S.noHistoryText) }
            items(revisions, key = { it.id }) { r ->
                SoftSurface(Modifier.fillMaxWidth().clickable { preview = r }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(whenLabel(r.savedAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (r.title.isNotBlank()) Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Markdown.plain(r.body), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    val current by app.db.notes().observe(id).collectAsState(null)
    preview?.let { r ->
        var changes by remember(r.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(whenLabel(r.savedAt)) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !changes, onClick = { changes = false }, label = { Text(S.versionContent) })
                        FilterChip(selected = changes, onClick = { changes = true }, label = { Text(S.versionChanges) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (changes) {
                            val n = current
                            if (n != null) DiffView(versionText(r.title, r.body), versionText(n.title, n.body))
                        } else {
                            if (r.title.isNotBlank()) Text(r.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            MarkdownView(r.body, style = MaterialTheme.typography.bodyMedium, links = false)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.repo.restoreRevision(r); app.events.emit(com.samuelcruz.ghichu.UiEvent(S.versionRestored)) }
                    preview = null
                    onBack()
                }) { Text(S.restoreVersion) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(S.close) } },
        )
    }
}

private fun versionText(title: String, body: String) = if (title.isBlank()) body else title + "\n\n" + body

/** Lines removed (red, −) and added (green, +) going from [old] to [new], with a little context around. */
@Composable
private fun DiffView(old: String, new: String) {
    val lines = remember(old, new) { Diff.lines(old, new) }
    val shown = remember(lines) { Diff.compact(lines) }
    val cs = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val addedBg = if (dark) Color(0x332E7D32) else Color(0x2232A852)
    val removedBg = if (dark) Color(0x33C62828) else Color(0x22E53935)
    if (shown.isEmpty()) {
        Text(S.noChanges, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        return
    }
    val (added, removed) = Diff.counts(lines)
    Text(S.changesCount(added, removed), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    shown.forEach { l ->
        if (l == null) {
            Text("…", style = mono, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
        } else {
            val (sign, bg) = when (l.op) {
                Diff.Op.ADDED -> "+ " to addedBg
                Diff.Op.REMOVED -> "− " to removedBg
                Diff.Op.SAME -> "  " to Color.Transparent
            }
            Text(
                sign + l.text.ifEmpty { " " },
                style = mono.copy(
                    color = if (l.op == Diff.Op.SAME) cs.onSurfaceVariant else cs.onSurface,
                    textDecoration = if (l.op == Diff.Op.REMOVED) TextDecoration.LineThrough else null,
                ),
                modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** Drag notes into the order you like (used by "Sắp xếp: thủ công"). */
@Composable
fun ReorderScreen(onBack: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val list = remember { mutableStateListOf<Note>() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val all = app.db.notes().allLive().filter { !it.inTrash && !it.archived && !it.template }
        list += all.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.position })
        loaded = true
    }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    fun save() {
        if (loaded) scope.launch {
            app.repo.reorder(list.map { it.id })
            app.prefs.sort = Sort.MANUAL
        }
        onBack()
    }
    androidx.activity.compose.BackHandler { save() }
    SimpleScaffold(S.reorderNotes, ::save, actions = { TextButton(onClick = ::save) { Text(S.done) } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S.reorderHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { n ->
                key(n.id) {
                    val dragging = dragId == n.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f; scaleX = if (dragging) 1.02f else 1f; scaleY = scaleX }
                            .onGloballyPositioned { heights[n.id] = it.size.height }
                            .shadow(if (dragging) 10.dp else 0.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(NoteColors.background(n.color))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .pointerInput(n.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragId = n.id; dragOffset = 0f },
                                    onDragEnd = { dragId = null; dragOffset = 0f },
                                    onDragCancel = { dragId = null; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val i = list.indexOfFirst { it.id == n.id }
                                        val j = if (dragOffset > 0) i + 1 else i - 1
                                        val other = list.getOrNull(j) ?: return@detectDragGesturesAfterLongPress
                                        if (other.pinned != n.pinned) return@detectDragGesturesAfterLongPress
                                        val h = (heights[other.id] ?: return@detectDragGesturesAfterLongPress) + 8.dp.toPx()
                                        if (kotlin.math.abs(dragOffset) > h / 2) {
                                            list.add(j, list.removeAt(i))
                                            dragOffset += if (dragOffset > 0) -h else h
                                        }
                                    },
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.DragIndicator, S.dragToMove, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                n.displayTitle().ifBlank { S.untitled }, style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            if (!n.locked && n.title.isNotBlank() && n.body.isNotBlank()) {
                                Text(
                                    Markdown.plain(n.body).lineSequence().firstOrNull().orEmpty(),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (n.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (n.kind == NoteKind.CHECKLIST) Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

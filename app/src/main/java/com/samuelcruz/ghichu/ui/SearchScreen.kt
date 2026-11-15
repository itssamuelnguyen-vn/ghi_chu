package com.samuelcruz.ghichu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.core.matchesAll
import com.samuelcruz.ghichu.core.searchWords
import com.samuelcruz.ghichu.data.NoteKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Search filters besides the text. */
data class SearchFilter(
    val checklist: Boolean = false,
    val images: Boolean = false,
    val audio: Boolean = false,
    val reminder: Boolean = false,
    val locked: Boolean = false,
    val labelId: String? = null,
    val color: Int? = null,
) {
    val active get() = checklist || images || audio || reminder || locked || labelId != null || color != null
}

class SearchViewModel(app: GhiChuApp) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(SearchFilter())
    val labels = app.db.labels().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Results (trash left out, archive included), newest edit first. */
    val results = combine(app.db.notes().observeAll(), labels, app.db.attachments().observeAll(), query, filter) { notes, labels, atts, q, f ->
        val words = searchWords(q)
        if (words.isEmpty() && !f.active) return@combine null
        val byNote = atts.groupBy { it.noteId }
        val hits = notes.filter { n ->
            if (n.inTrash) return@filter false
            val a = byNote[n.id].orEmpty()
            val labelNames = n.labelIds.mapNotNull { id -> labels.firstOrNull { it.id == id }?.name }
            (words.isEmpty() || matchesAll(n.searchText + "\n" + labelNames.joinToString(" ").lowercase(), words)) &&
                (!f.checklist || n.kind == NoteKind.CHECKLIST) &&
                (!f.images || a.any { isImage(it) }) &&
                (!f.audio || a.any { it.kind == com.samuelcruz.ghichu.data.AttachmentKind.AUDIO }) &&
                (!f.reminder || n.reminderAt != null) &&
                (!f.locked || n.locked) &&
                (f.labelId == null || f.labelId in n.labelIds) &&
                (f.color == null || n.color == f.color)
        }.sortedByDescending { it.updatedAt }
        words to buildItems(hits, labels, atts)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = appViewModel { SearchViewModel(it) }
    val app = app()
    var text by rememberSaveable { mutableStateOf("") }
    val filter by vm.filter.collectAsState()
    val labels by vm.labels.collectAsState()
    val results by vm.results.collectAsState()
    val focus = remember { FocusRequester() }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { vm.query.value = text; if (text.isEmpty()) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(56.dp).clip(RoundedCornerShape(28.dp))
                .background(cs.surfaceContainerHigh).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text(S.searchHint, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; vm.query.value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (text.isNotEmpty() || filter.active) {
                IconButton(onClick = { text = ""; vm.query.value = ""; vm.filter.value = SearchFilter() }) { Icon(Icons.Default.Clear, S.clear) }
            }
        }

        // Filter chips
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip(S.checklists, Icons.Default.CheckBox, filter.checklist) { vm.filter.value = filter.copy(checklist = !filter.checklist) } }
            item { Chip(S.photos, Icons.Default.Image, filter.images) { vm.filter.value = filter.copy(images = !filter.images) } }
            item { Chip(S.recordings, Icons.Default.Mic, filter.audio) { vm.filter.value = filter.copy(audio = !filter.audio) } }
            item { Chip(S.reminders, Icons.Default.Alarm, filter.reminder) { vm.filter.value = filter.copy(reminder = !filter.reminder) } }
            item { Chip(S.lockedNotes, Icons.Default.Lock, filter.locked) { vm.filter.value = filter.copy(locked = !filter.locked) } }
            items(labels, key = { it.id }) { l ->
                Chip(l.name, Icons.AutoMirrored.Outlined.Label, filter.labelId == l.id) {
                    vm.filter.value = filter.copy(labelId = if (filter.labelId == l.id) null else l.id)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val r = results
        if (r == null) {
            // Nothing typed: browse by colour.
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                SectionLabel(S.colors)
                Spacer(Modifier.height(8.dp))
                val dark = LocalIsDark.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    (1 until NoteColors.count).forEach { i ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(NoteColors.of(i, dark, cs.surfaceContainer))
                                .border(1.dp, cs.outline, CircleShape).clickable { vm.filter.value = filter.copy(color = i) },
                        )
                    }
                }
            }
        } else if (r.second.isEmpty()) {
            EmptyState(Icons.Default.SearchOff, S.noResults, S.noResultsText)
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(if (app.prefs.grid) 2 else 1),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(S.resultCount(r.second.size), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
                items(r.second, key = { it.note.id }) { item ->
                    NoteCard(item, selected = false, onClick = { onOpen(item.note.id) }, onLongClick = {}, highlight = r.first, checkedLast = app.prefs.checkedLast)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(12.dp),
    )
}

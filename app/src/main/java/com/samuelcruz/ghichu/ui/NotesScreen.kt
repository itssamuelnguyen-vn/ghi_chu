package com.samuelcruz.ghichu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Label
import com.samuelcruz.ghichu.data.NoteKind
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.CalendarMonth

/** What the "+" button can start with. */
enum class NewKind { TEXT, LIST, PHOTO, AUDIO, VOICE, DRAW, TEMPLATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpen: (id: String) -> Unit,
    onNew: (id: String, start: String?) -> Unit,
    onSearch: () -> Unit,
    onLabels: () -> Unit,
    onSettings: () -> Unit,
    onReorder: () -> Unit,
    onTemplates: () -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
) {
    val vm = appViewModel { NotesViewModel(it) }
    val activity = androidx.activity.compose.LocalActivity.current
    var templatePicker by remember { mutableStateOf(false) }
    val app = app()
    val sections by vm.sections.collectAsState()
    val labels by vm.labels.collectAsState()
    val filter by vm.filter.collectAsState()
    val sort by vm.sort.collectAsState()
    val syncState by app.sync.state.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val selecting = vm.selected.isNotEmpty()
    var colorSheet by remember { mutableStateOf(false) }
    var labelSheet by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(selecting) { vm.clearSelection() }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(fabOpen) { fabOpen = false }
    BackHandler(!selecting && filter != Filter.Notes && !drawer.isOpen) { vm.show(Filter.Notes) }

    fun startNew(kind: NewKind) {
        fabOpen = false
        if (kind == NewKind.TEMPLATE) { templatePicker = true; return }
        scope.launch {
            val id = vm.create(if (kind == NewKind.LIST) NoteKind.CHECKLIST else NoteKind.TEXT)
            onNew(id, when (kind) {
                NewKind.PHOTO -> "photo"
                NewKind.AUDIO -> "record"
                NewKind.VOICE -> "voice"
                NewKind.DRAW -> "draw"
                else -> null
            })
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !selecting,
        drawerContent = {
            Drawer(filter, labels, onPick = { f -> vm.show(f); scope.launch { drawer.close() } }, onLabels = {
                scope.launch { drawer.close() }; onLabels()
            }, onSettings = { scope.launch { drawer.close() }; onSettings() }, onTemplates = {
                scope.launch { drawer.close() }; onTemplates()
            }, onToday = { scope.launch { drawer.close() }; onToday() }, onCalendar = {
                scope.launch { drawer.close() }; onCalendar()
            })
        },
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val gridState = rememberLazyStaggeredGridState()
            var refreshing by remember { mutableStateOf(false) }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; vm.refresh { refreshing = false } },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(if (vm.grid) 2 else 1),
                    state = gridState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "top") {
                        Column(Modifier.statusBarsPadding()) {
                            Spacer(Modifier.height(72.dp))
                            if (filter != Filter.Notes) {
                                Text(
                                    filterTitle(filter, labels),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
                                )
                            }
                            if (filter == Filter.Trash && sections.others.isNotEmpty()) {
                                Text(
                                    S.trashHint, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                    if (sections.loaded && sections.all.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "empty") { EmptyFor(filter) }
                    }
                    if (sections.pinned.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "h-pinned") { SectionLabel(S.pinned) }
                        items(sections.pinned, key = { it.note.id }) { item ->
                            SwipeCard(item, vm, filter, onOpen, Modifier.animateItem())
                        }
                        if (sections.others.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "h-others") { SectionLabel(S.others) }
                        }
                    }
                    items(sections.others, key = { it.note.id }) { item ->
                        SwipeCard(item, vm, filter, onOpen, Modifier.animateItem())
                    }
                }
            }

            // Top bar: a floating search pill, or the selection bar.
            AnimatedContent(
                targetState = selecting,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                label = "topbar",
            ) { sel ->
                if (sel) {
                    SelectionBar(
                        count = vm.selected.size,
                        filter = filter,
                        allPinned = sections.all.filter { it.note.id in vm.selected }.all { it.note.pinned },
                        onClose = vm::clearSelection,
                        onPin = vm::pinSelected,
                        onColor = { colorSheet = true },
                        onLabel = { labelSheet = true },
                        onArchive = { vm.archive(vm.selected, filter != Filter.Archive) },
                        onTrash = { vm.trash(vm.selected) },
                        onRestore = vm::restoreSelected,
                        onDeleteForever = { confirmDelete = true },
                        onSelectAll = vm::selectAll,
                        onDuplicate = vm::duplicateSelected,
                        onPdf = { activity?.let { vm.exportPdf(it) } },
                    )
                } else {
                    SearchPill(
                        grid = vm.grid,
                        sync = syncState,
                        onMenu = { scope.launch { drawer.open() } },
                        onSearch = onSearch,
                        onToggleGrid = vm::toggleGrid,
                        onSync = onSettings,
                        onSort = { sortMenu = true },
                        trash = filter == Filter.Trash,
                        canEmptyTrash = sections.all.isNotEmpty(),
                        onEmptyTrash = { confirmEmpty = true },
                    )
                }
            }
            if (templatePicker) {
                TemplatePicker(
                    onPick = { t ->
                        templatePicker = false
                        scope.launch { vm.createFromTemplate(t.id)?.let { onOpen(it) } }
                    },
                    onManage = { templatePicker = false; onTemplates() },
                    onDismiss = { templatePicker = false },
                )
            }
            if (sortMenu) {
                SortSheet(sort, onPick = { vm.changeSort(it); sortMenu = false }, onReorder = { sortMenu = false; onReorder() }, onDismiss = { sortMenu = false })
            }

            if (filter != Filter.Trash && !selecting) {
                AnimatedVisibility(fabOpen, enter = fadeIn(), exit = fadeOut()) {
                    // Dims the list behind the open menu; a tap outside closes it.
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                            .pointerInput(Unit) { detectTapGestures { fabOpen = false } },
                    )
                }
                Fab(fabOpen, onToggle = { fabOpen = !fabOpen }, onPick = ::startNew, modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
    }

    if (colorSheet) {
        ModalBottomSheet(onDismissRequest = { colorSheet = false }) {
            Text(S.color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            ColorRow(-1, onPick = { vm.colorSelected(it); colorSheet = false })
            Spacer(Modifier.height(32.dp))
        }
    }
    if (labelSheet) {
        val chosen = sections.all.filter { it.note.id in vm.selected }.map { it.note }
        LabelPicker(
            labels = labels,
            initiallyOn = labels.filter { l -> chosen.isNotEmpty() && chosen.all { l.id in it.labelIds } }.map { it.id }.toSet(),
            onDone = { add, remove -> vm.labelSelected(add, remove); labelSheet = false },
            onDismiss = { labelSheet = false },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(S.emptyTrashTitle) },
            text = { Text(S.emptyTrashText) },
            confirmButton = { TextButton(onClick = { vm.emptyTrash(); confirmEmpty = false }) { Text(S.emptyTrash) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(S.cancel) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(S.deleteForeverTitle(vm.selected.size)) },
            text = { Text(S.deleteForeverText) },
            confirmButton = { TextButton(onClick = { vm.deleteForeverSelected(); confirmDelete = false }) { Text(S.deleteForever) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(S.cancel) } },
        )
    }
}

fun filterTitle(f: Filter, labels: List<Label>): String = when (f) {
    Filter.Notes -> S.notes
    Filter.Reminders -> S.reminders
    Filter.Archive -> S.archive
    Filter.Trash -> S.trash
    is Filter.WithLabel -> labels.firstOrNull { it.id == f.id }?.name ?: S.labels
}

@Composable
private fun EmptyFor(f: Filter) {
    when (f) {
        Filter.Notes -> EmptyState(Icons.Outlined.Lightbulb, S.emptyNotesTitle, S.emptyNotesText)
        Filter.Reminders -> EmptyState(Icons.Outlined.Notifications, S.emptyRemindersTitle, S.emptyRemindersText)
        Filter.Archive -> EmptyState(Icons.Outlined.Archive, S.emptyArchiveTitle, S.emptyArchiveText)
        Filter.Trash -> EmptyState(Icons.Outlined.Delete, S.emptyTrashTitle2, S.emptyTrashText2)
        is Filter.WithLabel -> EmptyState(Icons.AutoMirrored.Outlined.Label, S.emptyLabelTitle, S.emptyLabelText)
    }
}

/** A card that can be swiped away: right = archive (or back from archive), left = trash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeCard(item: NoteItem, vm: NotesViewModel, filter: Filter, onOpen: (String) -> Unit, modifier: Modifier) {
    val app = app()
    val selecting = vm.selected.isNotEmpty()
    val id = item.note.id
    val card = @Composable {
        NoteCard(
            item,
            selected = id in vm.selected,
            onClick = { if (selecting) vm.toggleSelect(id) else onOpen(id) },
            onLongClick = { vm.toggleSelect(id) },
            checkedLast = app.prefs.checkedLast,
        )
    }
    if (filter == Filter.Trash || selecting) {
        Box(modifier) { card() }
        return
    }
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        onDismiss = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> vm.archive(setOf(id), filter != Filter.Archive)
                SwipeToDismissBoxValue.EndToStart -> vm.trash(setOf(id))
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // The card may stay in this list (e.g. reminders include archived notes): slide it back.
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val dir = state.dismissDirection
            val toTrash = dir == SwipeToDismissBoxValue.EndToStart
            val color = if (toTrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            val progress by animateFloatAsState(if (dir == SwipeToDismissBoxValue.Settled) 0f else 1f, label = "swipe")
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.18f * progress)).padding(horizontal = 20.dp),
                contentAlignment = if (toTrash) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        when {
                            toTrash -> Icons.Default.Delete
                            filter == Filter.Archive -> Icons.Default.Unarchive
                            else -> Icons.Default.Archive
                        },
                        null, tint = color,
                    )
                }
            }
        },
    ) { card() }
}

@Composable
private fun SearchPill(
    grid: Boolean,
    sync: com.samuelcruz.ghichu.sync.SyncState,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onToggleGrid: () -> Unit,
    onSync: () -> Unit,
    onSort: () -> Unit,
    trash: Boolean,
    canEmptyTrash: Boolean,
    onEmptyTrash: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(6.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onSearch)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, S.menu) }
        Text(S.searchHint, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (trash) {
            if (canEmptyTrash) TextButton(onClick = onEmptyTrash) { Text(S.emptyTrash) }
        } else {
            IconButton(onClick = onSort) { Icon(Icons.AutoMirrored.Filled.Sort, S.sortBy) }
            IconButton(onClick = onToggleGrid) {
                Icon(if (grid) Icons.Default.ViewAgenda else Icons.Default.GridView, if (grid) S.listView else S.gridView)
            }
        }
        if (sync.enabled) {
            IconButton(onClick = onSync) {
                val (icon, tint) = when {
                    sync.running -> Icons.Default.CloudSync to cs.primary
                    sync.needsSignIn || (sync.error != null && sync.error != S.syncOffline) -> Icons.Default.SyncProblem to cs.error
                    sync.error != null -> Icons.Default.CloudOff to cs.onSurfaceVariant
                    else -> Icons.Default.CloudDone to cs.tertiary
                }
                Icon(icon, S.sync, tint = tint)
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    filter: Filter,
    allPinned: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onColor: () -> Unit,
    onLabel: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onSelectAll: () -> Unit,
    onDuplicate: () -> Unit,
    onPdf: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var more by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(6.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(cs.primaryContainer)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, S.close) }
        Text("$count", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (filter == Filter.Trash) {
            IconButton(onClick = onRestore) { Icon(Icons.Default.Restore, S.restore) }
            IconButton(onClick = onDeleteForever) { Icon(Icons.Default.DeleteForever, S.deleteForever) }
        } else {
            if (filter != Filter.Archive) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Default.PushPin, if (allPinned) S.unpin else S.pin, Modifier.rotate(if (allPinned) 0f else 45f))
                }
            }
            IconButton(onClick = onColor) { Icon(Icons.Default.Palette, S.color) }
            IconButton(onClick = onLabel) { Icon(Icons.AutoMirrored.Filled.Label, S.labels) }
            IconButton(onClick = onArchive) {
                Icon(if (filter == Filter.Archive) Icons.Default.Unarchive else Icons.Default.Archive, S.archive)
            }
        }
        Box {
            IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, S.more) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(text = { Text(S.selectAll) }, leadingIcon = { Icon(Icons.Default.SelectAll, null) }, onClick = { more = false; onSelectAll() })
                if (filter != Filter.Trash) {
                    DropdownMenuItem(text = { Text(S.duplicate) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { more = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(S.exportPdf) }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { more = false; onPdf() })
                    DropdownMenuItem(text = { Text(S.delete) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { more = false; onTrash() })
                }
            }
        }
    }
}

@Composable
private fun Fab(open: Boolean, onToggle: () -> Unit, onPick: (NewKind) -> Unit, modifier: Modifier) {
    val rotation by animateFloatAsState(if (open) 45f else 0f, label = "fab")
    Column(
        modifier.navigationBarsPadding().padding(end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val options = listOf(
            Triple(NewKind.TEMPLATE, Icons.Outlined.Dashboard, S.fromTemplate),
            Triple(NewKind.DRAW, Icons.Default.Brush, S.newDrawing),
            Triple(NewKind.VOICE, Icons.Default.RecordVoiceOver, S.newVoice),
            Triple(NewKind.AUDIO, Icons.Default.Mic, S.newRecording),
            Triple(NewKind.PHOTO, Icons.Default.Image, S.newPhoto),
            Triple(NewKind.LIST, Icons.Default.CheckBox, S.newChecklist),
            Triple(NewKind.TEXT, Icons.Default.Edit, S.newNote),
        )
        options.forEachIndexed { i, (kind, icon, label) ->
            AnimatedVisibility(
                open,
                enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.6f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .shadow(3.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    SmallFloatingActionButton(
                        onClick = { onPick(kind) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = 4.dp),
                    ) { Icon(icon, label) }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (open) onPick(NewKind.TEXT) else onToggle() },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(64.dp),
        ) {
            if (open) Icon(Icons.Default.Edit, S.newNote) else Icon(Icons.Default.Add, S.newNote, Modifier.rotate(rotation).size(30.dp))
        }
        if (open) {
            Text(
                S.close, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(sort: Int, onPick: (Int) -> Unit, onReorder: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(S.sortBy, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        listOf(Sort.EDITED to S.sortEdited, Sort.CREATED to S.sortCreated, Sort.TITLE to S.sortTitle, Sort.MANUAL to S.sortManual)
            .forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(value) }.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sort == value, onClick = { onPick(value) })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onReorder).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(S.reorderNotes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Drawer(
    current: Filter,
    labels: List<Label>,
    onPick: (Filter) -> Unit,
    onLabels: () -> Unit,
    onSettings: () -> Unit,
    onTemplates: () -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(BrandBrush), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lightbulb, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(S.appName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            }
            @Composable
            fun item(f: Filter, icon: ImageVector, label: String) = NavigationDrawerItem(
                icon = { Icon(icon, null) },
                label = { Text(label) },
                selected = current == f,
                onClick = { onPick(f) },
                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
            )
            item(Filter.Notes, Icons.Default.Lightbulb, S.notes)
            NavigationDrawerItem(icon = { Icon(Icons.Default.Today, null) }, label = { Text(S.todayNote) }, selected = false, onClick = onToday)
            item(Filter.Reminders, Icons.Default.Notifications, S.reminders)
            NavigationDrawerItem(icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text(S.calendar) }, selected = false, onClick = onCalendar)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.labels.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onLabels) { Text(S.edit) }
            }
            labels.forEach { l -> item(Filter.WithLabel(l.id), Icons.AutoMirrored.Outlined.Label, l.name) }
            NavigationDrawerItem(
                icon = { Icon(if (labels.isEmpty()) Icons.AutoMirrored.Filled.LabelOff else Icons.Default.Add, null) },
                label = { Text(if (labels.isEmpty()) S.createLabel else S.editLabels) },
                selected = false,
                onClick = onLabels,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            item(Filter.Archive, Icons.Default.Archive, S.archive)
            item(Filter.Trash, Icons.Default.Delete, S.trash)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(icon = { Icon(Icons.Outlined.Description, null) }, label = { Text(S.templates) }, selected = false, onClick = onTemplates)
            NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(S.settings) }, selected = false, onClick = onSettings)
            Spacer(Modifier.height(24.dp))
        }
    }
}

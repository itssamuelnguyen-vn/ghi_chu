package com.samuelcruz.ghichu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.UiEvent
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.core.fold
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.AttachmentKind
import com.samuelcruz.ghichu.data.Label
import com.samuelcruz.ghichu.data.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which notes the main screen shows (chosen in the side menu). */
sealed interface Filter {
    data object Notes : Filter
    data object Reminders : Filter
    data object Archive : Filter
    data object Trash : Filter
    data class WithLabel(val id: String) : Filter
}

object Sort {
    const val EDITED = 0
    const val CREATED = 1
    const val TITLE = 2
    const val MANUAL = 3
}

/** Notes split into the pinned ones and the rest, already filtered and sorted. */
data class NoteSections(val pinned: List<NoteItem> = emptyList(), val others: List<NoteItem> = emptyList(), val loaded: Boolean = false) {
    val all get() = pinned + others
}

/** Joins notes with their labels and attachments for cards (shared by the main list and search). */
fun buildItems(notes: List<Note>, labels: List<Label>, atts: List<Attachment>): List<NoteItem> {
    val labelName = labels.associate { it.id to it.name }
    val byNote = atts.groupBy { it.noteId }
    return notes.map { n ->
        val a = byNote[n.id].orEmpty()
        NoteItem(
            n, n.labelIds.mapNotNull { labelName[it] },
            a.filter { AttachmentKind.isPicture(it.kind) }, a.count { it.kind == AttachmentKind.AUDIO },
        )
    }
}

class NotesViewModel(private val app: GhiChuApp) : ViewModel() {
    private val repo = app.repo
    val filter = MutableStateFlow<Filter>(Filter.Notes)
    val sort = MutableStateFlow(app.prefs.sort)
    var grid by mutableStateOf(app.prefs.grid)
        private set
    var selected by mutableStateOf(emptySet<String>())
        private set

    val labels: StateFlow<List<Label>> = app.db.labels().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sections: StateFlow<NoteSections> = combine(
        app.db.notes().observeAll(), labels, app.db.attachments().observeAll(), filter, sort,
    ) { notes, labels, atts, f, s ->
        val shown = notes.filter { n ->
            when (f) {
                Filter.Notes -> !n.inTrash && !n.archived
                Filter.Reminders -> !n.inTrash && n.reminderAt != null
                Filter.Archive -> !n.inTrash && n.archived
                Filter.Trash -> n.inTrash
                is Filter.WithLabel -> !n.inTrash && f.id in n.labelIds
            }
        }
        val now = System.currentTimeMillis()
        val sorted = when {
            f == Filter.Reminders -> shown.sortedBy { Repeat.next(it.reminderAt!!, it.reminderRepeat, now, includePast = true) }
            f == Filter.Trash -> shown.sortedByDescending { it.trashedAt }
            s == Sort.CREATED -> shown.sortedByDescending { it.createdAt }
            s == Sort.TITLE -> shown.sortedBy { it.displayTitle().fold() }
            s == Sort.MANUAL -> shown.sortedByDescending { it.position }
            else -> shown.sortedByDescending { it.updatedAt }
        }
        val items = buildItems(sorted, labels, atts)
        val pinnable = f == Filter.Notes || f is Filter.WithLabel
        if (pinnable) NoteSections(items.filter { it.note.pinned }, items.filter { !it.note.pinned }, true)
        else NoteSections(emptyList(), items, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NoteSections())

    fun show(f: Filter) {
        filter.value = f
        selected = emptySet()
    }

    fun toggleGrid() {
        grid = !grid
        app.prefs.grid = grid
    }

    fun changeSort(value: Int) {
        sort.value = value
        app.prefs.sort = value
    }

    // Selection (long-press)

    fun toggleSelect(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clearSelection() { selected = emptySet() }

    fun selectAll() { selected = sections.value.all.map { it.note.id }.toSet() }

    private fun withSelection(block: suspend (Set<String>) -> Unit) {
        val ids = selected
        selected = emptySet()
        viewModelScope.launch { block(ids) }
    }

    suspend fun create(kind: Int): String {
        val f = filter.value
        val n = repo.create(kind, labelIds = if (f is Filter.WithLabel) listOf(f.id) else emptyList())
        return n.id
    }

    fun pinSelected() = withSelection { ids ->
        val all = sections.value.all.filter { it.note.id in ids }
        repo.setPinned(ids, !all.all { it.note.pinned })
    }

    fun colorSelected(color: Int) = withSelection { repo.setColor(it, color) }

    fun labelSelected(add: Set<String>, remove: Set<String>) = withSelection { repo.setLabels(it, add, remove) }

    fun archive(ids: Set<String>, archived: Boolean) {
        selected = emptySet()
        viewModelScope.launch {
            repo.setArchived(ids, archived)
            app.events.emit(
                UiEvent(if (archived) S.archived(ids.size) else S.unarchived(ids.size), S.undo) { repo.setArchived(ids, !archived) },
            )
        }
    }

    fun trash(ids: Set<String>) {
        selected = emptySet()
        viewModelScope.launch {
            repo.trash(ids)
            app.events.emit(UiEvent(S.trashed(ids.size), S.undo) { repo.restore(ids) })
        }
    }

    fun restoreSelected() = withSelection { ids ->
        repo.restore(ids)
        app.events.emit(UiEvent(S.restored(ids.size)))
    }

    fun deleteForeverSelected() = withSelection { repo.deleteForever(it) }

    fun emptyTrash() = viewModelScope.launch { repo.emptyTrash() }

    fun duplicateSelected() = withSelection { ids -> ids.forEach { repo.duplicate(it) } }

    /** "+" → from template: a new note (in the label being shown, if any); returns its id. */
    suspend fun createFromTemplate(templateId: String): String? {
        val f = filter.value
        return repo.createFromTemplate(templateId, if (f is Filter.WithLabel) listOf(f.id) else emptyList())?.id
    }

    /** Selected notes as one PDF (print screen → "Save as PDF"), in the order they are shown. */
    fun exportPdf(activity: android.app.Activity) {
        val order = sections.value.all.map { it.note.id }
        val ids = selected.toSet().let { sel -> order.filter { it in sel } }
        clearSelection()
        viewModelScope.launch {
            runCatching {
                val unlocked = app.notesUnlockedAt != 0L
                val (title, html) = PdfExport.html(app, ids, unlocked)
                PdfExport.print(activity, title, html)
            }.onFailure { app.events.emit(UiEvent(S.failed(it.message))) }
        }
    }

    fun refresh(done: () -> Unit) = viewModelScope.launch {
        if (!app.prefs.syncEnabled) app.events.emit(UiEvent(S.syncNotConnected))
        else app.sync.syncNow()
        done()
    }
}

package com.samuelcruz.ghichu.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuelcruz.ghichu.GhiChuApp
import com.samuelcruz.ghichu.UiEvent
import com.samuelcruz.ghichu.core.CheckItem
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.Links
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.MdEdit
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.Files
import com.samuelcruz.ghichu.data.Repo
import com.samuelcruz.ghichu.data.Label
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.NoteKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One checklist row while editing; [key] stays the same when rows move, so focus and drag follow it. */
class EditItem(val key: Long, text: String, checked: Boolean) {
    var text by mutableStateOf(TextFieldValue(text))
    var checked by mutableStateOf(checked)
}

/** A point in the undo history. */
private data class Snapshot(val title: String, val body: String, val kind: Int, val cursor: Int)

/**
 * The open note. Text is kept in Compose state while typing and saved to the database half a second after the
 * last key press (and when leaving), so nothing is lost if the app is killed.
 */
class EditorViewModel(private val app: GhiChuApp, val id: String) : ViewModel() {
    private val repo = app.repo
    var note by mutableStateOf<Note?>(null)
        private set
    /** The note as it was when opened (kept as a history version if it changes). */
    private var original: Note? = null

    var title by mutableStateOf(TextFieldValue(""))
        private set
    var body by mutableStateOf(TextFieldValue(""))
        private set
    val items = mutableStateListOf<EditItem>()
    private var nextKey = 0L

    /** Reading mode: Markdown drawn, links clickable, tasks tappable. */
    var reading by mutableStateOf(false)

    /** Item to focus after adding/splitting (its key). */
    var focusKey by mutableStateOf<Long?>(null)

    val attachments: StateFlow<List<Attachment>> = app.db.attachments().observeFor(id)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val labels: StateFlow<List<Label>> = app.db.labels().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Every note, for `[[links]]`: suggestions while typing, opening a link, notes that link here. */
    val allNotes: StateFlow<List<Note>> = app.db.notes().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var lastSnapshotAt = 0L

    private var saveJob: Job? = null

    init {
        app.sync.noteOpened(id)
        viewModelScope.launch {
            val n = app.db.notes().get(id) ?: return@launch
            note = n
            original = n
            title = TextFieldValue(n.title)
            loadBody(n.kind, n.body)
            reading = false
        }
        // Keep fields that other places change (widget ticks, reminder "Done", sync) in step.
        viewModelScope.launch {
            app.db.notes().observe(id).collect { fresh ->
                val cur = note
                if (fresh != null && cur != null && fresh.updatedAt != cur.updatedAt && saveJob?.isActive != true) {
                    note = fresh
                    if (fresh.title != currentTitle() || fresh.body != currentBody() || fresh.kind != cur.kind) {
                        title = TextFieldValue(fresh.title, TextRange(fresh.title.length))
                        loadBody(fresh.kind, fresh.body)
                    }
                }
            }
        }
    }

    private fun loadBody(kind: Int, text: String) {
        if (kind == NoteKind.CHECKLIST) {
            items.clear()
            Checklist.parse(text).forEach { items += EditItem(nextKey++, it.text, it.checked) }
            body = TextFieldValue("")
        } else {
            items.clear()
            body = TextFieldValue(text, TextRange(text.length))
        }
    }

    val kind get() = note?.kind ?: NoteKind.TEXT
    val isChecklist get() = kind == NoteKind.CHECKLIST

    private fun currentTitle() = title.text
    private fun currentBody(): String =
        if (isChecklist) Checklist.format(items.map { CheckItem(it.text.text, it.checked) }) else body.text

    // Undo / redo: a snapshot before a burst of typing (a pause of more than a second starts a new step).

    private fun remember(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSnapshotAt < 1000) { lastSnapshotAt = now; return }
        lastSnapshotAt = now
        val s = Snapshot(currentTitle(), currentBody(), kind, body.selection.start)
        if (undo.lastOrNull() == s) return
        undo.addLast(s)
        if (undo.size > 200) undo.removeFirst()
        redo.clear()
        canUndo = true
        canRedo = false
    }

    private fun current() = Snapshot(currentTitle(), currentBody(), kind, body.selection.start)

    private fun restore(s: Snapshot) {
        title = TextFieldValue(s.title, TextRange(s.title.length))
        if (s.kind != kind) note = note?.copy(kind = s.kind)
        if (s.kind == NoteKind.CHECKLIST) loadBody(s.kind, s.body)
        else body = TextFieldValue(s.body, TextRange(s.cursor.coerceIn(0, s.body.length)))
        lastSnapshotAt = 0
        scheduleSave()
    }

    fun undo() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(current())
        restore(s)
        canUndo = undo.isNotEmpty(); canRedo = true
    }

    fun redo() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(current())
        restore(s)
        canUndo = true; canRedo = redo.isNotEmpty()
    }

    // Editing

    fun onTitle(v: TextFieldValue) {
        if (v.text != title.text) remember()
        title = v.copy(text = v.text.replace('\n', ' '))
        scheduleSave()
    }

    fun onBody(v: TextFieldValue) {
        val old = body
        if (v.text == old.text) { body = v; return }
        remember(force = v.text.length < old.text.length - 1 || v.text.length > old.text.length + 1)
        // A newline typed on a list line continues the list.
        val typedNewline = v.text.length == old.text.length + 1 && v.selection.collapsed && v.selection.start > 0 &&
            v.text[v.selection.start - 1] == '\n'
        val cont = if (typedNewline) MdEdit.afterNewline(v.text, v.selection.start) else null
        body = if (cont != null) TextFieldValue(cont.text, TextRange(cont.selStart)) else v
        scheduleSave()
    }

    private fun applyEdit(r: MdEdit.Result) {
        remember(force = true)
        body = TextFieldValue(r.text, TextRange(r.selStart, r.selEnd))
        scheduleSave()
    }

    fun wrap(marker: String) = applyEdit(MdEdit.wrap(body.text, body.selection.min, body.selection.max, marker))
    fun toggleLines(kind: MdEdit.LineKind) = applyEdit(MdEdit.toggleLines(body.text, body.selection.min, body.selection.max, kind))
    fun insertLink() = applyEdit(MdEdit.link(body.text, body.selection.min, body.selection.max))

    // Links between notes: [[Title]]

    /** "[[" + selection + "]]", cursor before the "]]" so suggestions show up. */
    fun insertNoteLink() {
        val sel = body.selection
        val inner = body.text.substring(sel.min, sel.max)
        val t = body.text.substring(0, sel.min) + "[[" + inner + "]]" + body.text.substring(sel.max)
        applyEdit(MdEdit.Result(t, sel.min + 2 + inner.length))
    }

    /** Picks a suggestion for the `[[` being typed. */
    fun completeLink(start: Int, title: String) {
        val (t, cursor) = Links.complete(body.text, start, body.selection.start, title)
        applyEdit(MdEdit.Result(t, cursor))
    }

    /** Notes whose title matches what is typed after `[[` (this note left out). */
    fun linkSuggestions(query: String): List<Note> {
        val q = Links.key(query)
        return allNotes.value.asSequence()
            .filter { it.id != id && !it.inTrash && it.displayTitle().isNotBlank() }
            .filter { q.isEmpty() || Links.key(it.displayTitle()).contains(q) }
            .sortedWith(compareBy<Note> { !Links.key(it.displayTitle()).startsWith(q) }.thenByDescending { it.updatedAt })
            .take(8).toList()
    }

    /** Opens the note a `[[link]]` points to; if there is none yet, makes it (with that title). */
    fun openLink(title: String, open: (String) -> Unit) = viewModelScope.launch {
        flush()
        val k = Links.key(title)
        val target = allNotes.value.filter { it.id != id && Links.key(it.displayTitle()) == k }
            .sortedWith(compareBy<Note> { it.inTrash }.thenBy { it.archived }.thenByDescending { it.updatedAt })
            .firstOrNull()
        if (target != null) open(target.id)
        else {
            val n = repo.create(NoteKind.TEXT, title = title.trim())
            app.events.emit(UiEvent(S.linkCreated(title.trim())))
            open(n.id)
        }
    }

    /** Notes in [all] whose text has a `[[link]]` to this note's title (newest first). */
    fun backlinksIn(all: List<Note>, titleNow: String): List<Note> {
        val t = titleNow.ifBlank { note?.displayTitle().orEmpty() }
        if (t.isBlank()) return emptyList()
        return all.filter { it.id != id && !it.inTrash && Links.linksTo(it.body, t) }.sortedByDescending { it.updatedAt }
    }

    val isTemplate get() = note?.template == true

    /** PDF of this note through the print screen ("Save as PDF"). The note is open, so it is shown unlocked. */
    fun exportPdf(activity: android.app.Activity) = viewModelScope.launch {
        flush()
        runCatching {
            val (title, html) = PdfExport.html(app, listOf(id), unlocked = true)
            PdfExport.print(activity, title, html)
        }.onFailure { app.events.emit(UiEvent(S.failed(it.message))) }
    }

    fun saveAsTemplate() = viewModelScope.launch {
        flush()
        if (repo.saveAsTemplate(id) != null) app.events.emit(UiEvent(S.templateSaved))
    }

    /** Inserts dictated text at the cursor (or into the focused / a new checklist item). */
    fun insertText(text: String) {
        if (text.isBlank()) return
        remember(force = true)
        if (isChecklist) {
            items += EditItem(nextKey++, text.trim(), false)
        } else {
            val sel = body.selection
            val before = body.text.substring(0, sel.min)
            val sep = if (before.isNotEmpty() && !before.endsWith(' ') && !before.endsWith('\n')) " " else ""
            val t = before + sep + text.trim() + body.text.substring(sel.max)
            body = TextFieldValue(t, TextRange(before.length + sep.length + text.trim().length))
        }
        scheduleSave()
    }

    /** A small empty table on its own lines at the cursor, its first header cell selected. */
    fun insertTable() {
        remember(force = true)
        val sel = body.selection
        val before = body.text.substring(0, sel.min)
        val after = body.text.substring(sel.max)
        val lead = if (before.isEmpty() || before.endsWith("\n\n")) "" else if (before.endsWith("\n")) "\n" else "\n\n"
        val trail = if (after.startsWith("\n")) "" else "\n"
        val table = Markdown.tableTemplate(S.tableColumn)
        val start = before.length + lead.length + 2
        body = TextFieldValue(before + lead + table + trail + after, TextRange(start, start + S.tableColumn.length + 2))
        scheduleSave()
    }

    /** Adds text at the end (OCR): a new paragraph, or one checklist item per line. */
    private fun appendText(text: String) {
        remember(force = true)
        if (isChecklist) {
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { items += EditItem(nextKey++, it, false) }
        } else {
            val cur = body.text.trimEnd()
            val t = if (cur.isEmpty()) text else cur + "\n\n" + text
            body = TextFieldValue(t, TextRange(t.length))
        }
        scheduleSave()
    }

    fun toggleTaskInReading(lineIndex: Int) {
        remember(force = true)
        val t = MdEdit.toggleTask(body.text, lineIndex)
        body = TextFieldValue(t, TextRange(body.selection.start.coerceAtMost(t.length)))
        scheduleSave()
    }

    // Checklist

    fun onItemText(item: EditItem, v: TextFieldValue) {
        if (v.text.contains('\n')) {
            // Enter splits the item: text after the cursor goes to a new item below.
            remember(force = true)
            val i = items.indexOf(item)
            val parts = v.text.split('\n')
            item.text = TextFieldValue(parts.first(), TextRange(parts.first().length))
            var at = i + 1
            var last: EditItem? = null
            for (p in parts.drop(1)) {
                val ni = EditItem(nextKey++, p, false)
                items.add(at++, ni)
                last = ni
            }
            focusKey = last?.key
        } else {
            if (v.text != item.text.text) remember()
            item.text = v
        }
        scheduleSave()
    }

    fun toggleItem(item: EditItem) {
        remember(force = true)
        item.checked = !item.checked
        scheduleSave()
    }

    fun addItem(): Long {
        remember(force = true)
        val ni = EditItem(nextKey++, "", false)
        // New items go after the last unchecked one, so they stay above the done ones.
        val lastOpen = items.indexOfLast { !it.checked }
        items.add(lastOpen + 1, ni)
        focusKey = ni.key
        scheduleSave()
        return ni.key
    }

    /** Backspace in an empty item / its X: removes it and focuses the item above. */
    fun removeItem(item: EditItem) {
        remember(force = true)
        val i = items.indexOf(item)
        if (i < 0) return
        items.removeAt(i)
        focusKey = items.getOrNull(i - 1)?.key ?: items.getOrNull(0)?.key
        scheduleSave()
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        scheduleSave()
    }

    fun uncheckAll() {
        remember(force = true)
        items.forEach { it.checked = false }
        scheduleSave()
    }

    fun deleteChecked() {
        remember(force = true)
        items.removeAll { it.checked }
        scheduleSave()
    }

    fun convertKind() {
        val n = note ?: return
        remember(force = true)
        if (isChecklist) {
            val text = Checklist.toText(items.map { CheckItem(it.text.text, it.checked) })
            note = n.copy(kind = NoteKind.TEXT)
            loadBody(NoteKind.TEXT, text)
        } else {
            val list = Checklist.fromText(body.text)
            note = n.copy(kind = NoteKind.CHECKLIST)
            loadBody(NoteKind.CHECKLIST, Checklist.format(list))
            if (items.isEmpty()) addItem()
        }
        reading = false
        saveNow()
    }

    // Note properties (saved at once)

    private fun change(block: (Note) -> Note) {
        val n = note ?: return
        note = block(n)
        saveNow()
    }

    fun setColor(c: Int) = change { it.copy(color = c) }
    fun togglePin() = change { it.copy(pinned = !it.pinned, archived = if (!it.pinned) false else it.archived) }
    fun setReminder(at: Long?, repeat: Int) = change { it.copy(reminderAt = at, reminderRepeat = if (at == null) 0 else repeat) }
    fun setLabels(add: Set<String>, remove: Set<String>) =
        change { it.copy(labels = Note.labelsOf((it.labelIds.toSet() + add - remove).toList())) }
    fun removeLabel(labelId: String) = change { it.copy(labels = Note.labelsOf(it.labelIds - labelId)) }
    fun setLocked(locked: Boolean) = change { it.copy(locked = locked) }

    fun toggleArchive(onDone: () -> Unit) {
        val n = note ?: return
        val archived = !n.archived
        change { it.copy(archived = archived, pinned = if (archived) false else it.pinned) }
        app.events.tryEmit(UiEvent(if (archived) S.archived(1) else S.unarchived(1), S.undo) { repo.setArchived(listOf(id), !archived) })
        onDone()
    }

    fun trash(onDone: () -> Unit) {
        viewModelScope.launch {
            flush()
            repo.trash(listOf(id))
            app.events.emit(UiEvent(S.trashed(1), S.undo) { repo.restore(listOf(id)) })
            onDone()
        }
    }

    fun restoreFromTrash() = viewModelScope.launch { repo.restore(listOf(id)); note = app.db.notes().get(id) }

    fun duplicate(onCopy: (String) -> Unit) = viewModelScope.launch {
        flush()
        repo.duplicate(id)?.let { onCopy(it.id) }
    }

    // Attachments

    fun addImage(uri: Uri) = viewModelScope.launch {
        flush()
        runCatching { repo.addImage(id, uri) }.onFailure { app.events.emit(UiEvent(S.photoFailed)) }
        note = app.db.notes().get(id)
    }

    fun addAudio(file: File, durationMs: Long) = viewModelScope.launch {
        flush()
        repo.addAudio(id, file, durationMs)
        note = app.db.notes().get(id)
    }

    fun addFile(uri: Uri) = viewModelScope.launch {
        flush()
        runCatching { repo.addFile(id, uri) }.onFailure {
            app.events.emit(UiEvent(if (it is Repo.TooLarge) S.fileTooLarge else S.fileFailed))
        }
        note = app.db.notes().get(id)
    }

    /** Pages from the document scanner become photos; the snackbar offers to copy their text into the note. */
    fun addScannedPages(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        flush()
        val added = uris.mapNotNull { runCatching { repo.addImage(id, it) }.getOrNull() }
        note = app.db.notes().get(id)
        if (added.isEmpty()) { app.events.emit(UiEvent(S.photoFailed)); return@launch }
        app.events.emit(UiEvent(S.scannedPages(added.size), S.extractTextAll) { extractText(added) })
    }

    /** Text recognised in [pictures] is added at the end of the note. */
    fun extractText(pictures: List<Attachment>) = viewModelScope.launch {
        app.events.emit(UiEvent(S.readingText))
        var error: Throwable? = null
        val texts = pictures.mapNotNull { a ->
            runCatching { Ocr.read(app, Files.file(app, a)) }.onFailure { error = it }.getOrNull()
        }.filter { it.isNotBlank() }
        when {
            texts.isNotEmpty() -> { appendText(texts.joinToString("\n\n")); app.events.emit(UiEvent(S.textAdded)) }
            error != null -> app.events.emit(UiEvent(S.failed(error.message)))
            else -> app.events.emit(UiEvent(S.noTextFound))
        }
    }

    // Read aloud

    var speaking by mutableStateOf(false)
        private set
    private var speaker: Speaker? = null

    fun toggleReadAloud() {
        if (speaking) { speaker?.stop(); return }
        val t = currentTitle().trim()
        val b = if (isChecklist) items.joinToString("\n") { it.text.text } else Markdown.plain(currentBody())
        val text = listOf(t, b).filter { it.isNotBlank() }.joinToString(".\n")
        if (text.isBlank()) { app.events.tryEmit(UiEvent(S.nothingToRead)); return }
        val s = speaker ?: Speaker(app) { speaking = it }.also { speaker = it }
        s.speak(text) { msg -> speaking = false; app.events.tryEmit(UiEvent(msg)) }
    }

    fun removeAttachment(a: Attachment) = viewModelScope.launch {
        flush()
        repo.removeAttachment(a)
        note = app.db.notes().get(id)
        app.events.emit(UiEvent(S.attachmentRemoved))
    }

    // Saving

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            flush()
        }
    }

    private fun saveNow() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { flush() }
    }

    /** Writes the note if anything differs from the database. */
    private suspend fun flush() {
        val n = note ?: return
        val stored = app.db.notes().get(id) ?: return
        val edited = n.copy(title = currentTitle().trim().let { if (it.isEmpty()) "" else currentTitle() }, body = currentBody())
        val same = stored.title == edited.title && stored.body == edited.body && stored.sameContent(edited) &&
            stored.color == edited.color && stored.pinned == edited.pinned
        if (same) return
        note = repo.save(edited.copy(trashedAt = stored.trashedAt, position = stored.position))
    }

    /** Leaving the editor: save, keep the old text as a history version, drop the note if it stayed empty. */
    fun close() {
        app.scope.launch {
            withContext(NonCancellable) {
                flush()
                val saved = app.db.notes().get(id)
                val orig = original
                if (saved != null && orig != null && (saved.title != orig.title || saved.body != orig.body)) repo.snapshot(orig)
                if (repo.discardIfEmpty(id)) app.events.emit(UiEvent(S.emptyDiscarded))
                app.sync.noteClosed(id)
                app.sync.requestSoon()
            }
        }
    }

    override fun onCleared() {
        speaker?.shutdown()
        close()
        super.onCleared()
    }

    /** Plain text for sharing / copying. */
    fun shareText(): String {
        val t = currentTitle().trim()
        val b = if (isChecklist) items.joinToString("\n") { (if (it.checked) "✓ " else "☐ ") + it.text.text } else currentBody()
        return listOf(t, b).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun markdown(labelNames: List<String>): String = com.samuelcruz.ghichu.data.Export.markdownOf(
        note?.copy(title = currentTitle(), body = currentBody()) ?: return "", labelNames, attachments.value,
    )
}

package com.samuelcruz.ghichu.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import android.provider.OpenableColumns
import com.samuelcruz.ghichu.core.Daily
import com.samuelcruz.ghichu.core.Links
import com.samuelcruz.ghichu.core.Templates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Every change to notes goes through here (like Rails service objects), so each one stamps [Note.updatedAt],
 * marks the row for upload and tells the app ([onChange]: reminders, widgets, sync).
 */
class Repo(private val context: Context, val db: AppDatabase) {
    /** Called after notes changed here (not by sync), with the notes as they are now. */
    var onChange: (List<Note>) -> Unit = {}
    /** Called after labels changed here. */
    var onLabelsChange: () -> Unit = {}

    private val notes get() = db.notes()

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun create(
        kind: Int = NoteKind.TEXT,
        title: String = "",
        body: String = "",
        labelIds: Collection<String> = emptyList(),
        color: Int = 0,
        id: String = newId(),
        template: Boolean = false,
    ): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = id, title = title, body = body, kind = kind, color = color, createdAt = now, updatedAt = now,
            position = notes.maxPosition() + 1, labels = Note.labelsOf(labelIds), template = template,
        ).indexed()
        notes.upsert(note)
        onChange(listOf(note))
        return note
    }

    /** Saves an edited note: new [Note.updatedAt], marked for upload. */
    suspend fun save(note: Note): Note {
        val saved = note.copy(updatedAt = System.currentTimeMillis(), dirty = true).indexed()
        notes.upsert(saved)
        onChange(listOf(saved))
        return saved
    }

    suspend fun update(id: String, change: (Note) -> Note): Note? {
        val n = notes.get(id) ?: return null
        if (n.deleted) return null
        val changed = change(n)
        if (changed == n) return n
        return save(changed)
    }

    suspend fun updateMany(ids: Collection<String>, change: (Note) -> Note) {
        val now = System.currentTimeMillis()
        val changed = db.withTransaction {
            ids.mapNotNull { notes.get(it) }.filter { !it.deleted }.mapNotNull { n ->
                val c = change(n)
                if (c == n) null else c.copy(updatedAt = now, dirty = true).indexed().also { notes.upsert(it) }
            }
        }
        if (changed.isNotEmpty()) onChange(changed)
    }

    suspend fun setPinned(ids: Collection<String>, pinned: Boolean) = updateMany(ids) { it.copy(pinned = pinned) }
    suspend fun setColor(ids: Collection<String>, color: Int) = updateMany(ids) { it.copy(color = color) }

    suspend fun setArchived(ids: Collection<String>, archived: Boolean) =
        updateMany(ids) { it.copy(archived = archived, pinned = if (archived) false else it.pinned) }

    suspend fun trash(ids: Collection<String>) =
        updateMany(ids) { it.copy(trashedAt = System.currentTimeMillis(), pinned = false) }

    suspend fun restore(ids: Collection<String>) = updateMany(ids) { it.copy(trashedAt = null) }

    /** Gone for good: the row becomes an empty tombstone (so other devices delete it too), files are removed. */
    suspend fun deleteForever(ids: Collection<String>) {
        val now = System.currentTimeMillis()
        val gone = db.withTransaction {
            ids.mapNotNull { notes.get(it) }.map { n ->
                for (a in db.attachments().forNote(n.id)) {
                    Files.file(context, a).delete()
                    db.attachments().delete(a.id)
                }
                db.revisions().deleteFor(n.id)
                tombstone(n, now).also { notes.upsert(it) }
            }
        }
        if (gone.isNotEmpty()) onChange(gone)
    }

    suspend fun emptyTrash() = deleteForever(notes.allLive().filter { it.inTrash }.map { it.id })

    /** Notes in the trash for more than 30 days are deleted (run on start). */
    suspend fun purgeOldTrash() {
        val old = notes.trashedBefore(System.currentTimeMillis() - TRASH_DAYS * 24 * 3600 * 1000L)
        if (old.isNotEmpty()) deleteForever(old.map { it.id })
    }

    /** A note left empty (no text, no attachment) disappears when its editor closes. */
    suspend fun discardIfEmpty(id: String): Boolean {
        val n = notes.get(id) ?: return false
        if (!n.isEmpty || n.deleted || db.attachments().forNote(id).isNotEmpty()) return false
        if (n.remoteId == null) {
            notes.deleteRow(id)
            onChange(listOf(n.copy(deleted = true)))
        } else {
            deleteForever(listOf(id))
        }
        return true
    }

    /** A copy of the note (not pinned, archived or with a reminder, like a fresh note). */
    suspend fun duplicate(id: String): Note? =
        copyOf(id) { it.copy(pinned = false, archived = false, reminderAt = null, reminderRepeat = 0) }

    /** Saves a copy of the note as a template (Settings → templates, or "+" → from template). */
    suspend fun saveAsTemplate(id: String): Note? =
        copyOf(id) { it.copy(template = true, reminderAt = null, reminderRepeat = 0, pinned = false, archived = false, locked = false) }

    /** A new note from a template: {{date}}, {{time}}… filled in (see `Templates.fill`). */
    suspend fun createFromTemplate(templateId: String, labelIds: Collection<String> = emptyList()): Note? {
        val now = System.currentTimeMillis()
        return copyOf(templateId) { t ->
            t.copy(
                title = Templates.fill(t.title, now), body = Templates.fill(t.body, now), template = false,
                labels = Note.labelsOf((t.labelIds + labelIds).distinct()),
            )
        }
    }

    /**
     * Today's note ("Ghi chú hôm nay"): the live note titled like today's daily title, or a new one made from
     * [templateId] (or empty). See [Daily].
     */
    suspend fun dailyNote(templateId: String?, now: Long = System.currentTimeMillis()): Note {
        val template = templateId?.let { notes.get(it) }?.takeIf { it.template && !it.deleted && !it.inTrash }
        val title = Daily.title(template?.title, now)
        val key = Links.key(title)
        notes.allLive().filter { !it.template && !it.inTrash && Links.key(it.title) == key }
            .maxByOrNull { it.createdAt }?.let { return it }
        val made = template?.let { t ->
            copyOf(t.id) { it.copy(title = title, body = Templates.fill(it.body, now), template = false) }
        }
        return made ?: create(NoteKind.TEXT, title = title)
    }

    /** A new note with the content of [id] changed by [change], photos and recordings copied too. */
    private suspend fun copyOf(id: String, change: (Note) -> Note): Note? {
        val n = notes.get(id) ?: return null
        if (n.deleted) return null
        val c = change(n)
        val now = System.currentTimeMillis()
        val copy = c.copy(
            id = newId(), createdAt = now, updatedAt = now, position = notes.maxPosition() + 1, trashedAt = null,
            dirty = true, remoteId = null, remoteVersion = 0,
        ).indexed()
        notes.upsert(copy)
        for (a in db.attachments().forNote(id)) {
            val newA = a.copy(id = newId(), noteId = copy.id, remoteId = null)
            val src = Files.file(context, a)
            if (src.exists()) src.copyTo(Files.file(context, newA), overwrite = true)
            db.attachments().insert(newA)
        }
        onChange(listOf(copy))
        return copy
    }

    /** Manual order: the first id ends up on top. */
    suspend fun reorder(idsTopFirst: List<String>) {
        val count = idsTopFirst.size
        val pos = idsTopFirst.withIndex().associate { (i, id) -> id to (count - i).toDouble() }
        updateMany(idsTopFirst) { n -> pos[n.id]?.let { n.copy(position = it) } ?: n }
    }

    // Labels

    suspend fun createLabel(name: String): Label {
        val existing = db.labels().all().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        if (existing != null) return existing
        val l = Label(newId(), name.trim(), System.currentTimeMillis())
        db.labels().upsert(l)
        onLabelsChange()
        return l
    }

    suspend fun renameLabel(id: String, name: String) {
        val l = db.labels().get(id) ?: return
        db.labels().upsert(l.copy(name = name.trim(), updatedAt = System.currentTimeMillis(), dirty = true))
        onLabelsChange()
    }

    /** Removes the label from every note and keeps a tombstone for sync. */
    suspend fun deleteLabel(id: String) {
        val l = db.labels().get(id) ?: return
        db.labels().upsert(l.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
        updateMany(notes.withLabel(id).map { it.id }) { n -> n.copy(labels = Note.labelsOf(n.labelIds - id)) }
        onLabelsChange()
    }

    suspend fun setLabels(ids: Collection<String>, add: Set<String>, remove: Set<String>) =
        updateMany(ids) { n -> n.copy(labels = Note.labelsOf((n.labelIds.toSet() + add - remove).toList())) }

    // Attachments (part of the note: changing them marks the note for upload)

    suspend fun addImage(noteId: String, source: Uri): Attachment {
        val a = Attachment(newId(), noteId, AttachmentKind.IMAGE, "jpg", System.currentTimeMillis())
        Files.saveImage(context, source, a.id)
        db.attachments().insert(a)
        notes.get(noteId)?.let { save(it) }
        return a
    }

    /** A finished recording at [file] (moved into the attachment folder). */
    suspend fun addAudio(noteId: String, file: File, durationMs: Long): Attachment {
        val a = Attachment(newId(), noteId, AttachmentKind.AUDIO, "m4a", System.currentTimeMillis(), durationMs)
        val target = Files.file(context, a)
        if (!file.renameTo(target)) { file.copyTo(target, overwrite = true); file.delete() }
        db.attachments().insert(a)
        notes.get(noteId)?.let { save(it) }
        return a
    }

    class TooLarge : Exception()

    /** Any file picked by the user, copied into the note (kept as is, with its name). */
    suspend fun addFile(noteId: String, source: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = ""
        var size = -1L
        resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0).orEmpty()
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size > AttachmentKind.MAX_FILE_BYTES) throw TooLarge()
        if (name.isBlank()) name = source.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "file" }
        val a = Attachment(newId(), noteId, AttachmentKind.FILE, AttachmentKind.extOf(name), System.currentTimeMillis(), name = name)
        val target = Files.file(context, a)
        val input = resolver.openInputStream(source) ?: error("Cannot read the file")
        input.use { inp ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val r = inp.read(buf)
                    if (r < 0) break
                    total += r
                    if (total > AttachmentKind.MAX_FILE_BYTES) { out.close(); target.delete(); throw TooLarge() }
                    out.write(buf, 0, r)
                }
            }
        }
        db.attachments().insert(a)
        notes.get(noteId)?.let { save(it) }
        a
    }

    /**
     * Saves a drawing (PNG bytes). Editing an existing drawing makes a new attachment in its place (attachments
     * never change, so each one is uploaded once), keeping its place in the note.
     */
    suspend fun saveDrawing(noteId: String, png: ByteArray, replaces: Attachment?): Attachment {
        val a = Attachment(newId(), noteId, AttachmentKind.DRAWING, "png", replaces?.createdAt ?: System.currentTimeMillis())
        Files.file(context, a).writeBytes(png)
        db.withTransaction {
            if (replaces != null) db.attachments().delete(replaces.id)
            db.attachments().insert(a)
        }
        if (replaces != null) { Files.file(context, replaces).delete(); Files.forget(replaces.fileName) }
        notes.get(noteId)?.let { save(it) }
        return a
    }

    suspend fun removeAttachment(a: Attachment) {
        db.attachments().delete(a.id)
        Files.file(context, a).delete()
        Files.forget(a.fileName)
        notes.get(a.noteId)?.let { save(it) }
    }

    // History

    /** Keeps [old] as a revision unless the newest revision already has the same text. */
    suspend fun snapshot(old: Note) {
        if (old.isEmpty || old.deleted) return
        val last = db.revisions().latest(old.id)
        if (last != null && last.title == old.title && last.body == old.body && last.kind == old.kind) return
        db.revisions().insert(Revision(noteId = old.id, title = old.title, body = old.body, kind = old.kind, savedAt = old.updatedAt))
        db.revisions().trim(old.id)
    }

    suspend fun restoreRevision(r: Revision) {
        val n = notes.get(r.noteId) ?: return
        snapshot(n)
        save(n.copy(title = r.title, body = r.body, kind = r.kind))
    }

    companion object {
        const val TRASH_DAYS = 30

        fun tombstone(n: Note, now: Long) = n.copy(
            title = "", body = "", labels = "", searchText = "", reminderAt = null, reminderRepeat = 0,
            pinned = false, archived = false, trashedAt = null, locked = false,
            deleted = true, dirty = true, updatedAt = now,
        )
    }
}

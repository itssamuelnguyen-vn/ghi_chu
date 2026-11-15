package com.samuelcruz.ghichu.sync

import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.Label
import com.samuelcruz.ghichu.data.Note
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What to do with one note (or label) during a sync, from its local and remote bookkeeping only. Plain Kotlin so
 * it is unit-tested.
 *
 * - Same Drive version as last time: upload if changed here, else nothing.
 * - Drive has a newer version: download, unless it also changed here → conflict (both versions are kept).
 * - Only here: upload. Only on Drive: download. A local tombstone Drive never had is simply dropped.
 */
object SyncPlan {
    enum class Action { NONE, UPLOAD, DOWNLOAD, CONFLICT, DROP_LOCAL }

    data class Local(val dirty: Boolean, val remoteId: String?, val remoteVersion: Long, val tombstone: Boolean)
    data class Remote(val id: String, val version: Long)

    fun decide(l: Local?, r: Remote?): Action = when {
        l == null && r == null -> Action.NONE
        l == null -> Action.DOWNLOAD
        r == null -> if (l.tombstone) Action.DROP_LOCAL else Action.UPLOAD
        l.remoteId == r.id && l.remoteVersion == r.version -> if (l.dirty) Action.UPLOAD else Action.NONE
        !l.dirty -> Action.DOWNLOAD
        else -> Action.CONFLICT
    }

    fun local(n: Note) = Local(n.dirty, n.remoteId, n.remoteVersion, n.deleted)
    fun local(l: Label) = Local(l.dirty, l.remoteId, l.remoteVersion, l.deleted)

    const val NOTE_PREFIX = "n_"
    const val LABEL_PREFIX = "l_"
    const val ATTACHMENT_PREFIX = "a_"

    fun noteFile(id: String) = "$NOTE_PREFIX$id.json"
    fun labelFile(id: String) = "$LABEL_PREFIX$id.json"
    fun attachmentFile(a: Attachment) = "$ATTACHMENT_PREFIX${a.fileName}"

    /** "n_<id>.json" → "<id>" for the given prefix, or null. */
    fun idOf(name: String, prefix: String): String? =
        if (name.startsWith(prefix) && name.endsWith(".json")) name.substring(prefix.length, name.length - 5) else null

    /** "a_<id>.<ext>" → "<id>". */
    fun attachmentIdOf(name: String): String? =
        if (name.startsWith(ATTACHMENT_PREFIX)) name.substring(ATTACHMENT_PREFIX.length).substringBeforeLast('.') else null

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}

/** A note as stored in Drive (`n_<id>.json`). Bookkeeping fields stay on the phone. */
@Serializable
data class NoteDto(
    val v: Int = 1,
    val id: String,
    val title: String = "",
    val body: String = "",
    val kind: Int = 0,
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val position: Double = 0.0,
    val reminderAt: Long? = null,
    val reminderRepeat: Int = 0,
    val locked: Boolean = false,
    val labels: List<String> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
    val deleted: Boolean = false,
    /** Device that wrote it (for "copy from another device" titles). */
    val device: String = "",
    val template: Boolean = false,
) {
    fun toNote(remote: RemoteFile?): Note = Note(
        id = id, title = title, body = body, kind = kind, color = color, pinned = pinned, archived = archived,
        trashedAt = trashedAt, createdAt = createdAt, updatedAt = updatedAt, position = position,
        reminderAt = reminderAt, reminderRepeat = reminderRepeat, locked = locked, labels = Note.labelsOf(labels),
        deleted = deleted, dirty = false, remoteId = remote?.id, remoteVersion = remote?.version ?: 0,
        template = template,
    ).indexed()

    companion object {
        fun of(n: Note, attachments: List<Attachment>, device: String) = NoteDto(
            id = n.id, title = n.title, body = n.body, kind = n.kind, color = n.color, pinned = n.pinned,
            archived = n.archived, trashedAt = n.trashedAt, createdAt = n.createdAt, updatedAt = n.updatedAt,
            position = n.position, reminderAt = n.reminderAt, reminderRepeat = n.reminderRepeat, locked = n.locked,
            labels = n.labelIds, attachments = attachments.map { AttachmentDto.of(it) }, deleted = n.deleted,
            device = device, template = n.template,
        )
    }
}

@Serializable
data class AttachmentDto(
    val id: String,
    val kind: Int,
    val ext: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    /** Original file name (files only; missing in files written before 0.3.0). */
    val name: String = "",
) {
    fun toAttachment(noteId: String, remoteId: String?) = Attachment(id, noteId, kind, ext, createdAt, durationMs, remoteId, name)

    companion object {
        fun of(a: Attachment) = AttachmentDto(a.id, a.kind, a.ext, a.createdAt, a.durationMs, a.name)
    }
}

@Serializable
data class LabelDto(val v: Int = 1, val id: String, val name: String = "", val updatedAt: Long = 0, val deleted: Boolean = false) {
    fun toLabel(remote: RemoteFile) = Label(id, name, updatedAt, deleted, dirty = false, remoteId = remote.id, remoteVersion = remote.version)

    companion object {
        fun of(l: Label) = LabelDto(id = l.id, name = l.name, updatedAt = l.updatedAt, deleted = l.deleted)
    }
}

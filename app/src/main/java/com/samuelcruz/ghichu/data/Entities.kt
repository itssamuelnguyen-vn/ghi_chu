package com.samuelcruz.ghichu.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.fold

object NoteKind {
    const val TEXT = 0
    const val CHECKLIST = 1
}

/**
 * A note (like an ActiveRecord model). Ids are random UUID strings, so notes made on two phones never clash when
 * they meet in Google Drive.
 *
 * Sync bookkeeping (never shown): [dirty] = changed here since the last upload; [remoteId]/[remoteVersion] = the
 * Drive file and the version of it this row matches. A deleted note stays as a tombstone ([deleted], content
 * emptied) so the deletion reaches the other devices.
 */
@Entity(tableName = "note", indices = [Index("deleted"), Index("trashedAt")])
data class Note(
    @PrimaryKey val id: String,
    val title: String = "",
    /** Markdown text; for a checklist, one task line per item (see [Checklist]). */
    val body: String = "",
    val kind: Int = NoteKind.TEXT,
    /** Index into `NoteColors` (0 = no colour). */
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** When it was moved to the trash (emptied after 30 days); null = not in the trash. */
    val trashedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Manual order: bigger = higher up. */
    val position: Double = 0.0,
    /** First time of the reminder (see `Repeat`); null = no reminder. */
    val reminderAt: Long? = null,
    val reminderRepeat: Int = 0,
    /** Needs the fingerprint to open; its text is hidden from lists, search and widgets. */
    val locked: Boolean = false,
    /** Label ids as ",id1,id2," so a label can be matched with LIKE '%,id,%'. */
    val labels: String = "",
    /** Lower-cased accent-free title + text, for search. */
    val searchText: String = "",
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    val remoteId: String? = null,
    val remoteVersion: Long = 0,
    /** A template ("Mẫu ghi chú"): shown only in the templates screen, copied into new notes. */
    @ColumnInfo(defaultValue = "0") val template: Boolean = false,
) {
    val labelIds: List<String> get() = labels.split(',').filter { it.isNotEmpty() }
    val inTrash: Boolean get() = trashedAt != null
    val isEmpty: Boolean get() = title.isBlank() &&
        (body.isBlank() || kind == NoteKind.CHECKLIST && Checklist.parse(body).all { it.text.isBlank() })
    val items get() = Checklist.parse(body)

    /** Title, or the first line of the text when there is no title. */
    fun displayTitle(): String = title.ifBlank { Markdown.plain(body).lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "" }

    /** Fields that are synced, to tell whether two versions really differ (times and bookkeeping ignored). */
    fun sameContent(other: Note): Boolean =
        title == other.title && body == other.body && kind == other.kind && color == other.color &&
            pinned == other.pinned && archived == other.archived && (trashedAt == null) == (other.trashedAt == null) &&
            reminderAt == other.reminderAt && reminderRepeat == other.reminderRepeat && locked == other.locked &&
            labelIds.toSet() == other.labelIds.toSet() && deleted == other.deleted && template == other.template

    /** Fills [searchText] from the content (call before every save). */
    fun indexed(): Note = copy(searchText = (title + "\n" + if (locked) "" else body).fold())

    companion object {
        fun labelsOf(ids: Collection<String>): String = if (ids.isEmpty()) "" else ids.joinToString(",", ",", ",")
    }
}

@Entity(tableName = "label")
data class Label(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    val remoteId: String? = null,
    val remoteVersion: Long = 0,
)

object AttachmentKind {
    const val IMAGE = 0
    const val AUDIO = 1
    /** A drawing / handwriting made in the app: a PNG, shown like a photo, can be opened again to draw more. */
    const val DRAWING = 2

    /** Any other file (PDF, Word, zip…): kept as is, opened with another app. [Attachment.name] = its name. */
    const val FILE = 3

    /** Photos and drawings are both pictures (cards, sharing, export). */
    fun isPicture(kind: Int) = kind == IMAGE || kind == DRAWING

    /** Largest file that can be attached (whole files go through memory when synced/encrypted). */
    const val MAX_FILE_BYTES = 20L * 1024 * 1024

    /** Extension to store a file under: "Báo cáo.PDF" → "pdf"; odd or missing ones → "bin". */
    fun extOf(fileName: String): String {
        val e = fileName.substringAfterLast('.', "").lowercase()
        return if (e.length in 1..8 && e.all { it in 'a'..'z' || it in '0'..'9' }) e else "bin"
    }
}

/**
 * A photo, drawing, voice recording or file of a note. The file is `filesDir/att/<id>.<ext>` and never changes (a new photo
 * is a new attachment), so it is uploaded to Drive once. [remoteId] = the Drive file once uploaded.
 */
@Entity(tableName = "attachment", indices = [Index("noteId")])
data class Attachment(
    @PrimaryKey val id: String,
    val noteId: String,
    val kind: Int,
    val ext: String,
    val createdAt: Long,
    /** Length of a recording. */
    val durationMs: Long = 0,
    val remoteId: String? = null,
    /** Original name of an attached file (DB v3); "" for photos and recordings. */
    @ColumnInfo(defaultValue = "''") val name: String = "",
) {
    val fileName: String get() = "$id.$ext"
    /** What to show / suggest when saving: the original name, else the stored one. */
    val displayName: String get() = name.ifBlank { fileName }
}

/** An earlier version of a note (kept on this phone only, at most [MAX_PER_NOTE] per note). */
@Entity(tableName = "revision", indices = [Index("noteId")])
data class Revision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val title: String,
    val body: String,
    val kind: Int,
    val savedAt: Long,
) {
    companion object {
        const val MAX_PER_NOTE = 50
    }
}

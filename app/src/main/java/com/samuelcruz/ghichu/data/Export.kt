package com.samuelcruz.ghichu.data

import android.content.Context
import androidx.room.withTransaction
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.fileNameOf
import com.samuelcruz.ghichu.sync.LabelDto
import com.samuelcruz.ghichu.sync.NoteDto
import com.samuelcruz.ghichu.sync.SyncPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Markdown export: one `.md` file per note with a small front matter (works in Obsidian, Typora, GitHub…). */
object Export {
    private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneId.systemDefault())

    private fun yaml(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun markdownOf(n: Note, labelNames: List<String>, attachments: List<Attachment>, attachmentDir: String = "attachments"): String =
        buildString {
            append("---\n")
            if (n.title.isNotBlank()) append("title: ").append(yaml(n.title)).append('\n')
            append("created: ").append(iso.format(Instant.ofEpochMilli(n.createdAt))).append('\n')
            append("updated: ").append(iso.format(Instant.ofEpochMilli(n.updatedAt))).append('\n')
            if (labelNames.isNotEmpty()) append("labels: [").append(labelNames.joinToString(", ") { yaml(it) }).append("]\n")
            if (n.pinned) append("pinned: true\n")
            if (n.archived) append("archived: true\n")
            n.reminderAt?.let {
                append("reminder: ").append(iso.format(Instant.ofEpochMilli(it))).append('\n')
                if (n.reminderRepeat != Repeat.NONE) append("repeat: ").append(n.reminderRepeat).append('\n')
            }
            append("---\n\n")
            if (n.title.isNotBlank()) append("# ").append(n.title).append("\n\n")
            append(n.body.trimEnd()).append('\n')
            for (a in attachments) {
                append('\n')
                if (AttachmentKind.isPicture(a.kind)) append("![](").append(attachmentDir).append('/').append(a.fileName).append(")\n")
                else append(if (a.kind == AttachmentKind.FILE) "[📎 " else "[🎤 ").append(a.displayName)
                    .append("](").append(attachmentDir).append('/').append(a.fileName).append(")\n")
            }
        }

    /** All notes (not the trash) as a `.zip` of `.md` files plus an `attachments` folder. Returns the count. */
    suspend fun allToZip(context: Context, db: AppDatabase, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val notes = db.notes().allLive().filter { !it.inTrash && !it.template }
        val labels = db.labels().all().associate { it.id to it.name }
        val atts = db.attachments().all().groupBy { it.noteId }
        val used = mutableSetOf<String>()
        ZipOutputStream(out).use { zip ->
            for (n in notes.sortedByDescending { it.updatedAt }) {
                var name = fileNameOf(n.displayTitle(), "note-" + n.id.take(8))
                if (n.archived) name = "Archive/$name"
                var unique = name
                var k = 2
                while (!used.add(unique.lowercase())) unique = "$name ($k)".also { k++ }
                val a = atts[n.id].orEmpty()
                val dir = if (n.archived) "../attachments" else "attachments"
                zip.putNextEntry(ZipEntry("$unique.md"))
                zip.write(markdownOf(n, n.labelIds.mapNotNull { labels[it] }, a, dir).toByteArray())
                zip.closeEntry()
                for (att in a) {
                    val f = Files.file(context, att)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry("attachments/" + att.fileName))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        notes.size
    }
}

/** The backup file: `backup.json` (notes and labels, trash included) and the photos/recordings in `att/`. */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val notes: List<NoteDto> = emptyList(),
    val labels: List<LabelDto> = emptyList(),
)

object Backup {
    suspend fun write(context: Context, db: AppDatabase, out: OutputStream, device: String): Int = withContext(Dispatchers.IO) {
        val notes = db.notes().allLive()
        val atts = db.attachments().all()
        val byNote = atts.groupBy { it.noteId }
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            notes = notes.map { NoteDto.of(it, byNote[it.id].orEmpty(), device) },
            labels = db.labels().all().map { LabelDto.of(it) },
        )
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(SyncPlan.json.encodeToString(BackupFile.serializer(), file).toByteArray())
            zip.closeEntry()
            val live = notes.map { it.id }.toSet()
            for (a in atts) {
                if (a.noteId !in live) continue
                val f = Files.file(context, a)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("att/" + a.fileName))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        notes.size
    }

    /**
     * Restores a backup by merging: notes in the file replace the same notes here (and are uploaded again),
     * notes made after the backup are kept. Returns the number of notes restored.
     */
    suspend fun restore(context: Context, db: AppDatabase, input: InputStream): Int = withContext(Dispatchers.IO) {
        var file: BackupFile? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == "backup.json" -> file = SyncPlan.json.decodeFromString(BackupFile.serializer(), zip.readBytes().decodeToString())
                    e.name.startsWith("att/") && !e.isDirectory -> {
                        val name = e.name.substringAfterLast('/')
                        val target = Files.file(context, name)
                        if (!target.exists()) target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val b = file ?: error("Not a backup file")
        val now = System.currentTimeMillis()
        var count = 0
        db.withTransaction {
            for (l in b.labels) {
                val local = db.labels().get(l.id)
                db.labels().upsert(
                    Label(l.id, l.name, now, l.deleted, dirty = true, remoteId = local?.remoteId, remoteVersion = local?.remoteVersion ?: 0),
                )
            }
            for (dto in b.notes) {
                if (dto.deleted) continue
                val local = db.notes().get(dto.id)
                val incoming = dto.toNote(null)
                if (local != null && !local.deleted && local.sameContent(incoming)) continue
                db.notes().upsert(
                    incoming.copy(dirty = true, updatedAt = now, remoteId = local?.remoteId, remoteVersion = local?.remoteVersion ?: 0),
                )
                val have = db.attachments().forNote(dto.id).map { it.id }.toSet()
                for (a in dto.attachments) if (a.id !in have) db.attachments().insert(a.toAttachment(dto.id, null))
                count++
            }
        }
        count
    }
}

package com.samuelcruz.ghichu.data

import android.content.Context
import android.net.Uri
import com.samuelcruz.ghichu.core.CheckItem
import com.samuelcruz.ghichu.core.Checklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** One note read from a Google Keep export (Google Takeout → Keep, one `.json` per note). */
data class KeepNote(
    val title: String,
    val text: String,
    val items: List<CheckItem>?,
    val color: Int,
    val pinned: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val labels: List<String>,
    /** File names of photos / recordings next to the `.json` file, with their MIME type. */
    val attachments: List<Pair<String, String>>,
    val createdAt: Long,
    val editedAt: Long,
)

/**
 * Imports Google Keep notes from the Takeout `.zip` (takeout.google.com → only "Keep"). Titles, text,
 * checklists, colours, pins, archive, labels, photos and recordings come along. Importing the same export twice
 * updates the same notes instead of making copies.
 */
object KeepImport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val colors = mapOf(
        "RED" to 1, "ORANGE" to 2, "YELLOW" to 3, "GREEN" to 4, "TEAL" to 5, "BLUE" to 6,
        "CERULEAN" to 7, "PURPLE" to 8, "PINK" to 9, "BROWN" to 10, "GRAY" to 11,
    )

    fun parse(text: String): KeepNote? {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        if (!o.containsKey("textContent") && !o.containsKey("listContent") && !o.containsKey("title")) return null
        fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun bool(k: String) = o[k]?.jsonPrimitive?.booleanOrNull ?: false
        fun usec(k: String) = (o[k]?.jsonPrimitive?.longOrNull ?: 0L) / 1000
        val items = (o["listContent"] as? JsonArray)?.map {
            val i = it.jsonObject
            CheckItem(i["text"]?.jsonPrimitive?.contentOrNull.orEmpty(), i["isChecked"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        val links = (o["annotations"] as? JsonArray)?.mapNotNull { a ->
            val url = a.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val t = a.jsonObject["title"]?.jsonPrimitive?.contentOrNull
            if (t.isNullOrBlank()) url else "[$t]($url)"
        }.orEmpty()
        var body = str("textContent")
        val extra = links.filter { !body.contains(it) && !body.contains(it.substringAfter("](").removeSuffix(")")) }
        if (extra.isNotEmpty()) body = (body.trimEnd() + "\n\n" + extra.joinToString("\n")).trim()
        val edited = usec("userEditedTimestampUsec")
        return KeepNote(
            title = str("title"),
            text = body,
            items = items,
            color = colors[str("color")] ?: 0,
            pinned = bool("isPinned"),
            archived = bool("isArchived"),
            trashed = bool("isTrashed"),
            labels = (o["labels"] as? JsonArray)?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.orEmpty(),
            attachments = (o["attachments"] as? JsonArray)?.mapNotNull {
                val a = it.jsonObject
                val path = a["filePath"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                path to (a["mimetype"]?.jsonPrimitive?.contentOrNull ?: "")
            }.orEmpty(),
            createdAt = usec("createdTimestampUsec").takeIf { it > 0 } ?: edited,
            editedAt = edited,
        )
    }

    data class Result(val notes: Int, val skippedTrash: Int)

    suspend fun import(context: Context, repo: Repo, db: AppDatabase, uris: List<Uri>): Result = withContext(Dispatchers.IO) {
        var count = 0
        var trashed = 0
        val labelIds = mutableMapOf<String, String>()
        for (uri in uris) {
            // ZipFile needs a real file to jump between the notes and their pictures.
            val tmp = File(context.cacheDir, "keep-import.zip")
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: continue
            try {
                ZipFile(tmp).use { zip ->
                    val entries = zip.entries().toList()
                    val byName = entries.associateBy { it.name.substringAfterLast('/') }
                    val byBase = entries.associateBy { it.name.substringAfterLast('/').substringBeforeLast('.') }
                    for (e in entries) {
                        if (e.isDirectory || !e.name.endsWith(".json") || !e.name.contains("Keep/")) continue
                        val k = parse(zip.getInputStream(e).bufferedReader().readText()) ?: continue
                        if (k.trashed) { trashed++; continue }
                        val id = UUID.nameUUIDFromBytes(("keep:" + e.name + ":" + k.createdAt).toByteArray()).toString()
                        val ids = k.labels.map { name -> labelIds.getOrPut(name.lowercase()) { repo.createLabel(name).id } }
                        val isList = k.items != null
                        val now = System.currentTimeMillis()
                        val old = db.notes().get(id)
                        val note = Note(
                            id = id,
                            title = k.title,
                            body = if (isList) Checklist.format(k.items) else k.text,
                            kind = if (isList) NoteKind.CHECKLIST else NoteKind.TEXT,
                            color = k.color, pinned = k.pinned, archived = k.archived,
                            createdAt = k.createdAt.takeIf { it > 0 } ?: now,
                            updatedAt = k.editedAt.takeIf { it > 0 } ?: now,
                            position = db.notes().maxPosition() + 1,
                            labels = Note.labelsOf(ids),
                            remoteId = old?.remoteId, remoteVersion = old?.remoteVersion ?: 0,
                        ).indexed()
                        db.notes().upsert(note)
                        val have = db.attachments().forNote(id).size
                        if (have == 0) {
                            for ((path, mime) in k.attachments) {
                                val name = path.substringAfterLast('/')
                                val entry = byName[name] ?: byBase[name.substringBeforeLast('.')] ?: continue
                                val image = mime.startsWith("image") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                                val aid = UUID.nameUUIDFromBytes(("keep:" + id + ":" + name).toByteArray()).toString()
                                val a = if (image) {
                                    val att = Attachment(aid, id, AttachmentKind.IMAGE, "jpg", note.updatedAt)
                                    runCatching { Files.saveImage({ zip.getInputStream(entry) }, Files.file(context, att)) }.getOrNull() ?: continue
                                    att
                                } else {
                                    val ext = entry.name.substringAfterLast('.', "3gp").lowercase()
                                    val att = Attachment(aid, id, AttachmentKind.AUDIO, ext, note.updatedAt)
                                    zip.getInputStream(entry).use { input -> Files.file(context, att).outputStream().use { input.copyTo(it) } }
                                    att
                                }
                                db.attachments().insert(a)
                            }
                        }
                        count++
                    }
                }
            } finally {
                tmp.delete()
            }
        }
        Result(count, trashed)
    }
}

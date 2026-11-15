package com.samuelcruz.ghichu.sync

import android.content.Context
import androidx.room.withTransaction
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.AppDatabase
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.Files
import com.samuelcruz.ghichu.data.Label
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.data.Repo
import com.samuelcruz.ghichu.sync.SyncPlan.Action

/** What one sync run did, for the status line and the after-sync hooks. */
data class SyncReport(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val conflicts: Int = 0,
    /** Notes changed by the download (reminders and widgets must follow). */
    val changedNotes: List<Note> = emptyList(),
    val labelsChanged: Boolean = false,
    /** Photos/recordings still waiting (no Wi-Fi with "Wi-Fi only", or not uploaded yet by the other phone). */
    val attachmentsPending: Int = 0,
    /** Drive is end-to-end encrypted (it has `crypto.json`). */
    val encrypted: Boolean = false,
    /** Drive files this phone cannot decrypt (encrypted with a passphrase it does not have). */
    val unreadable: Int = 0,
)

/** Drive is encrypted but this phone has no key, or a key for another passphrase: nothing is synced. */
class NeedsPassphrase : Exception("Encryption passphrase needed")

/**
 * One sync pass against the hidden Drive folder. Every note and label is one small JSON file
 * (`n_<id>.json`, `l_<id>.json`); every photo/recording is one file (`a_<id>.<ext>`) uploaded once.
 * Order: labels, attachment uploads, notes, attachment downloads, clean-up.
 *
 * End-to-end encryption: when Drive has `crypto.json`, every upload is encrypted with [key] (see [Crypto]) and
 * tagged `enc = 1`; otherwise uploads are plain. Files whose tag does not match (made before encryption was
 * switched on or off) are uploaded again from this phone's copy, so Drive ends up all one way.
 */
class SyncEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val repo: Repo,
    private val device: String,
    /** The end-to-end key of this phone (null = none). */
    private val key: ByteArray? = null,
) {
    private val json = SyncPlan.json
    /** Upload encrypted (decided at the start of [run] from `crypto.json`). */
    private var encrypt = false

    private fun seal(bytes: ByteArray): ByteArray = if (encrypt) Crypto.encrypt(key!!, bytes) else bytes

    /** Downloads a JSON file and decrypts it if needed; null when this phone has not the key for it. */
    private suspend fun fetchText(drive: Drive, id: String): String? =
        try { Crypto.decrypt(key, drive.download(id)).decodeToString() } catch (e: Crypto.WrongKey) { null }

    suspend fun run(
        drive: Drive,
        attachmentsAllowed: Boolean,
        /** A note open in the editor is not overwritten now (the next sync will). */
        isOpen: (String) -> Boolean,
    ): SyncReport {
        var up = 0
        var down = 0
        var conflicts = 0
        val changed = mutableListOf<Note>()

        val all = dedupe(drive, drive.list())
        val configFile = all.firstOrNull { it.name == Crypto.CONFIG_FILE }
        if (configFile != null) {
            val config = json.decodeFromString<Crypto.Config>(drive.download(configFile.id).decodeToString())
            if (key == null || !Crypto.matches(key, config)) throw NeedsPassphrase()
        }
        encrypt = configFile != null
        var unreadable = 0
        val remoteNotes = all.mapNotNull { f -> SyncPlan.idOf(f.name, SyncPlan.NOTE_PREFIX)?.let { it to f } }.toMap()
        val remoteLabels = all.mapNotNull { f -> SyncPlan.idOf(f.name, SyncPlan.LABEL_PREFIX)?.let { it to f } }.toMap()
        val remoteAtts = all.mapNotNull { f -> SyncPlan.attachmentIdOf(f.name)?.let { it to f } }.toMap()

        // 1. Labels: last writer wins (a label is only a name).
        var labelsChanged = false
        val localLabels = db.labels().everything().associateBy { it.id }
        for (id in localLabels.keys + remoteLabels.keys) {
            val l = localLabels[id]
            val r = remoteLabels[id]
            when (SyncPlan.decide(l?.let { SyncPlan.local(it) }, r?.let { SyncPlan.Remote(it.id, it.version) })) {
                Action.NONE -> if (l != null && r != null && r.encrypted != encrypt) { uploadLabel(drive, l, r); up++ }
                Action.DROP_LOCAL -> Unit // label tombstones are tiny; keep them
                Action.UPLOAD -> { uploadLabel(drive, l!!, r); up++ }
                Action.DOWNLOAD -> {
                    val text = fetchText(drive, r!!.id)
                    if (text == null) unreadable++ else { db.labels().upsert(json.decodeFromString<LabelDto>(text).toLabel(r)); down++; labelsChanged = true }
                }
                Action.CONFLICT -> {
                    val text = fetchText(drive, r!!.id)
                    if (text == null) { unreadable++; continue }
                    val remote = json.decodeFromString<LabelDto>(text)
                    if (remote.updatedAt > l!!.updatedAt) {
                        db.labels().upsert(remote.toLabel(r)); down++; labelsChanged = true
                    } else {
                        uploadLabel(drive, l, r); up++
                    }
                }
            }
        }

        // 2. Upload new photos/recordings before the notes that point to them.
        var pending = 0
        val localAtts = db.attachments().all()
        val liveNoteIds = db.notes().allLive().map { it.id }.toSet()
        for (a in localAtts) {
            if (a.remoteId != null || a.noteId !in liveNoteIds) continue
            val existing = remoteAtts[a.id]
            if (existing != null) { db.attachments().setRemote(a.id, existing.id); continue }
            val file = Files.file(context, a)
            if (!file.exists()) continue
            if (!attachmentsAllowed) { pending++; continue }
            val rf = uploadAttachment(drive, a, file)
            db.attachments().setRemote(a.id, rf.id)
        }
        // Encryption switched on or off: replace Drive's copy of each photo/recording this phone has.
        for (a in localAtts) {
            val rf = remoteAtts[a.id] ?: continue
            if (rf.encrypted == encrypt || a.noteId !in liveNoteIds) continue
            val file = Files.file(context, a)
            if (!file.exists()) continue
            if (!attachmentsAllowed) { pending++; continue }
            drive.delete(rf.id)
            db.attachments().setRemote(a.id, uploadAttachment(drive, a, file).id)
        }

        // 3. Notes.
        var deferred = false
        val localNotes = db.notes().everything().associateBy { it.id }
        for (id in localNotes.keys + remoteNotes.keys) {
            val l = localNotes[id]
            val r = remoteNotes[id]
            when (SyncPlan.decide(l?.let { SyncPlan.local(it) }, r?.let { SyncPlan.Remote(it.id, it.version) })) {
                Action.NONE -> if (l != null && r != null && r.encrypted != encrypt) { uploadNote(drive, l, r); up++ }
                Action.DROP_LOCAL -> db.notes().deleteRow(id)
                Action.UPLOAD -> { uploadNote(drive, l!!, r); up++ }
                Action.DOWNLOAD -> {
                    if (isOpen(id)) { deferred = true; continue }
                    val text = fetchText(drive, r!!.id)
                    if (text == null) { unreadable++; deferred = true; continue }
                    val dto = json.decodeFromString<NoteDto>(text)
                    if (apply(dto, r, expectLocal = l, remoteAtts)) { down++; changed += dto.toNote(r) } else deferred = true
                }
                Action.CONFLICT -> {
                    if (isOpen(id)) { deferred = true; continue }
                    val text = fetchText(drive, r!!.id)
                    if (text == null) { unreadable++; deferred = true; continue }
                    val dto = json.decodeFromString<NoteDto>(text)
                    val remote = dto.toNote(r)
                    val sameAttachments = db.attachments().forNote(id).map { it.id }.toSet() == dto.attachments.map { it.id }.toSet()
                    if (l!!.sameContent(remote) && sameAttachments) {
                        // Both sides made the same change (e.g. a reminder ticked on two phones).
                        db.notes().upsert(l.copy(dirty = false, remoteId = r.id, remoteVersion = r.version))
                    } else if (l.deleted && !dto.deleted && l.updatedAt >= dto.updatedAt) {
                        // Deleted here after the last edit on Drive (e.g. while sync was off): the deletion wins.
                        uploadNote(drive, l, r); up++
                    } else {
                        if (!l.deleted) { conflicts++; changed += keepLocalCopy(l, dto) }
                        if (apply(dto, r, expectLocal = null, remoteAtts)) { down++; changed += remote }
                    }
                }
            }
        }

        // 4. Photos/recordings of downloaded notes.
        for (a in db.attachments().all()) {
            val file = Files.file(context, a)
            if (file.exists()) continue
            val rf = remoteAtts[a.id]
            if (rf == null || !attachmentsAllowed) { pending++; continue }
            if (!downloadAttachment(drive, rf, file)) { unreadable++; pending++; continue }
            if (a.remoteId != rf.id) db.attachments().setRemote(a.id, rf.id)
        }

        // 5. Drive files of photos no note uses any more (a day later, in case another phone is mid-upload).
        if (!deferred) {
            val used = db.attachments().all().map { it.id }.toSet()
            val dayAgo = System.currentTimeMillis() - 24 * 3600 * 1000L
            for ((id, rf) in remoteAtts) {
                if (id !in used && rf.modifiedAt in 1 until dayAgo) runCatching { drive.delete(rf.id) }
            }
        }

        return SyncReport(up, down, conflicts, changed, labelsChanged, pending, encrypt, unreadable)
    }

    private suspend fun uploadAttachment(drive: Drive, a: Attachment, file: java.io.File): RemoteFile =
        if (encrypt) drive.create(SyncPlan.attachmentFile(a), "application/octet-stream", seal(file.readBytes()), encrypted = true)
        else drive.createFromFile(SyncPlan.attachmentFile(a), mime(a), file)

    /** Downloads a photo/recording, decrypting it if needed; false when this phone has not the key for it. */
    private suspend fun downloadAttachment(drive: Drive, rf: RemoteFile, target: java.io.File): Boolean {
        val tmp = java.io.File(target.parentFile, target.name + ".dl")
        drive.downloadTo(rf.id, tmp)
        val data = tmp.readBytes()
        if (Crypto.isEncrypted(data)) {
            val plain = try { Crypto.decrypt(key, data) } catch (e: Crypto.WrongKey) { tmp.delete(); return false }
            target.writeBytes(plain)
            tmp.delete()
        } else if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true); tmp.delete()
        }
        return true
    }

    /** Two files with one name (an upload that was cut off before its id was saved): keep the newest. */
    private suspend fun dedupe(drive: Drive, files: List<RemoteFile>): List<RemoteFile> =
        files.groupBy { it.name }.map { (_, same) ->
            val keep = same.maxBy { it.version }
            for (f in same) if (f !== keep) runCatching { drive.delete(f.id) }
            keep
        }

    private suspend fun uploadLabel(drive: Drive, l: Label, r: RemoteFile?) {
        val bytes = seal(json.encodeToString(LabelDto.serializer(), LabelDto.of(l)).encodeToByteArray())
        val rf = if (r == null) drive.create(SyncPlan.labelFile(l.id), JSON, bytes, encrypt) else drive.update(r.id, JSON, bytes, encrypt)
        val now = db.labels().get(l.id) ?: return
        db.labels().upsert(now.copy(dirty = now.updatedAt != l.updatedAt, remoteId = rf.id, remoteVersion = rf.version))
    }

    private suspend fun uploadNote(drive: Drive, n: Note, r: RemoteFile?) {
        val atts = db.attachments().forNote(n.id)
        val bytes = seal(json.encodeToString(NoteDto.serializer(), NoteDto.of(n, atts, device)).encodeToByteArray())
        val rf = if (r == null) drive.create(SyncPlan.noteFile(n.id), JSON, bytes, encrypt) else drive.update(r.id, JSON, bytes, encrypt)
        // Edited again while uploading? Then it stays marked and goes up next time.
        val now = db.notes().get(n.id) ?: return
        db.notes().upsert(now.copy(dirty = now.updatedAt != n.updatedAt, remoteId = rf.id, remoteVersion = rf.version))
    }

    /**
     * Writes the Drive version of a note here. Returns false (and does nothing) if the note was edited here since
     * [expectLocal] was read, so that edit is never lost.
     */
    private suspend fun apply(dto: NoteDto, r: RemoteFile, expectLocal: Note?, remoteAtts: Map<String, RemoteFile>): Boolean {
        val removedFiles = mutableListOf<Attachment>()
        val ok = db.withTransaction {
            val current = db.notes().get(dto.id)
            if (expectLocal != null && current != null && current.updatedAt != expectLocal.updatedAt) return@withTransaction false
            if (current != null && !current.deleted && !dto.deleted && current.body != dto.body) repo.snapshot(current)
            val incoming = dto.toNote(r)
            db.notes().upsert(incoming)
            val have = db.attachments().forNote(dto.id).associateBy { it.id }
            val want = if (dto.deleted) emptyMap() else dto.attachments.associateBy { it.id }
            for ((id, a) in have) if (id !in want) { db.attachments().delete(id); removedFiles += a }
            for ((id, a) in want) if (id !in have) db.attachments().insert(a.toAttachment(dto.id, remoteAtts[id]?.id))
            if (dto.deleted) db.revisions().deleteFor(dto.id)
            true
        }
        for (a in removedFiles) { Files.file(context, a).delete(); Files.forget(a.fileName) }
        return ok
    }

    /** Conflict: this phone's version is saved as a new note so nothing is lost; the original takes Drive's. */
    private suspend fun keepLocalCopy(l: Note, remote: NoteDto): Note {
        val copy = l.copy(
            id = repo.newId(),
            title = (l.title.ifBlank { l.displayTitle() } + " (" + S.conflictCopy + ")").trim(),
            dirty = true, remoteId = null, remoteVersion = 0, updatedAt = System.currentTimeMillis(),
        ).indexed()
        db.withTransaction {
            db.notes().upsert(copy)
            val remoteIds = remote.attachments.map { it.id }.toSet()
            for (a in db.attachments().forNote(l.id)) if (a.id !in remoteIds) db.attachments().move(a.id, copy.id)
        }
        return copy
    }

    private fun mime(a: Attachment) = when (a.ext) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "m4a" -> "audio/mp4"
        "3gp" -> "audio/3gpp"
        "amr" -> "audio/amr"
        else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(a.ext) ?: "application/octet-stream"
    }

    private companion object {
        const val JSON = "application/json"
    }
}

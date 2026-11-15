package com.samuelcruz.ghichu

import com.samuelcruz.ghichu.data.KeepImport
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.sync.NoteDto
import com.samuelcruz.ghichu.sync.RemoteFile
import com.samuelcruz.ghichu.sync.SyncPlan
import com.samuelcruz.ghichu.sync.SyncPlan.Action
import com.samuelcruz.ghichu.sync.SyncPlan.Local
import com.samuelcruz.ghichu.sync.SyncPlan.Remote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlanTest {
    private fun local(dirty: Boolean, remoteId: String? = "f1", version: Long = 3, tombstone: Boolean = false) =
        Local(dirty, remoteId, version, tombstone)

    @Test fun decisions() {
        assertEquals(Action.NONE, SyncPlan.decide(null, null))
        assertEquals(Action.DOWNLOAD, SyncPlan.decide(null, Remote("f1", 1)))
        assertEquals(Action.UPLOAD, SyncPlan.decide(local(true, remoteId = null, version = 0), null))
        // Drive file vanished (hidden data cleared): upload again rather than lose the note.
        assertEquals(Action.UPLOAD, SyncPlan.decide(local(false), null))
        assertEquals(Action.DROP_LOCAL, SyncPlan.decide(local(false, tombstone = true), null))
        assertEquals(Action.NONE, SyncPlan.decide(local(false), Remote("f1", 3)))
        assertEquals(Action.UPLOAD, SyncPlan.decide(local(true), Remote("f1", 3)))
        assertEquals(Action.DOWNLOAD, SyncPlan.decide(local(false), Remote("f1", 4)))
        assertEquals(Action.CONFLICT, SyncPlan.decide(local(true), Remote("f1", 4)))
        // Matched by name after reconnecting (no remote id yet) and edited here: compare as a conflict.
        assertEquals(Action.CONFLICT, SyncPlan.decide(local(true, remoteId = null, version = 0), Remote("f9", 1)))
    }

    @Test fun fileNames() {
        assertEquals("abc", SyncPlan.idOf(SyncPlan.noteFile("abc"), SyncPlan.NOTE_PREFIX))
        assertNull(SyncPlan.idOf(SyncPlan.labelFile("abc"), SyncPlan.NOTE_PREFIX))
        assertEquals("abc", SyncPlan.attachmentIdOf("a_abc.jpg"))
    }

    @Test fun noteJsonRoundTrip() {
        val n = Note(
            id = "n1", title = "Đi chợ", body = "- [ ] sữa", kind = 1, color = 3, pinned = true,
            createdAt = 1, updatedAt = 2, position = 5.0, reminderAt = 100, reminderRepeat = 1, labels = ",l1,l2,",
        )
        val text = SyncPlan.json.encodeToString(NoteDto.serializer(), NoteDto.of(n, emptyList(), "dev"))
        val back = SyncPlan.json.decodeFromString(NoteDto.serializer(), text).toNote(RemoteFile("f", "n_n1.json", 7))
        assertTrue(n.sameContent(back))
        assertEquals(7, back.remoteVersion)
        assertFalse(back.dirty)
        assertEquals("di cho\n- [ ] sua", back.searchText)
    }

    @Test fun driveVersionIsReadFromAString() {
        val f = SyncPlan.json.decodeFromString(RemoteFile.serializer(), """{"id":"x","name":"n_1.json","version":"42","modifiedTime":"2026-11-08T03:00:00.000Z"}""")
        assertEquals(42, f.version)
        assertTrue(f.modifiedAt > 0)
    }
}

class KeepImportTest {
    @Test fun textNote() {
        val k = KeepImport.parse(
            """{"color":"YELLOW","isTrashed":false,"isPinned":true,"isArchived":false,"textContent":"Mật khẩu wifi",
               "title":"Nhà","userEditedTimestampUsec":1700000000000000,"createdTimestampUsec":1690000000000000,
               "labels":[{"name":"Gia đình"}],"annotations":[{"url":"https://vnexpress.net","title":"VnExpress"}]}""",
        )!!
        assertEquals("Nhà", k.title)
        assertEquals(3, k.color)
        assertTrue(k.pinned)
        assertEquals(listOf("Gia đình"), k.labels)
        assertEquals(1_700_000_000_000L, k.editedAt)
        assertTrue(k.text.contains("[VnExpress](https://vnexpress.net)"))
        assertNull(k.items)
    }

    @Test fun checklistNoteWithPhoto() {
        val k = KeepImport.parse(
            """{"color":"DEFAULT","title":"","listContent":[{"text":"Sữa","isChecked":false},{"text":"Trứng","isChecked":true}],
               "attachments":[{"filePath":"abc.jpeg","mimetype":"image/jpeg"}],"isTrashed":true}""",
        )!!
        val items = k.items
        assertNotNull(items)
        assertEquals(2, items!!.size)
        assertTrue(items[1].checked)
        assertTrue(k.trashed)
        assertEquals("abc.jpeg" to "image/jpeg", k.attachments.single())
    }

    @Test fun otherJsonIsIgnored() {
        assertNull(KeepImport.parse("""{"something":"else"}"""))
        assertNull(KeepImport.parse("not json"))
    }
}

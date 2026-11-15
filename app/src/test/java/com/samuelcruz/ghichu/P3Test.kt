package com.samuelcruz.ghichu

import com.samuelcruz.ghichu.core.CheckItem
import com.samuelcruz.ghichu.core.Html
import com.samuelcruz.ghichu.core.Links
import com.samuelcruz.ghichu.core.Templates
import com.samuelcruz.ghichu.data.Note
import com.samuelcruz.ghichu.sync.Crypto
import com.samuelcruz.ghichu.sync.NoteDto
import com.samuelcruz.ghichu.sync.SyncPlan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LinksTest {
    @Test fun targetsIgnoreAccentsAndRepeats() {
        assertEquals(listOf("Đi chợ", "Việc nhà"), Links.targets("xem [[Đi chợ]] và [[di cho]], rồi [[ Việc nhà ]]"))
        assertTrue(Links.linksTo("mua theo [[Đi Chợ]]", "di  cho"))
        assertFalse(Links.linksTo("mua theo [[Đi Chợ]]", "Đi"))
        assertFalse(Links.linksTo("không có link", "Đi chợ"))
    }

    @Test fun queryOnlyWhileTypingOnTheSameLine() {
        val t = "abc [[Đi c"
        assertEquals(4 to "Đi c", Links.queryAt(t, t.length))
        assertNull(Links.queryAt("[[xong]] ", 9))
        assertNull(Links.queryAt("[[ab\ncd", 7))
    }

    @Test fun completeReplacesTheOpenLinkAndClosingBrackets() {
        val (t, cursor) = Links.complete("x [[đi]] y", 2, 6, "Đi chợ")
        assertEquals("x [[Đi chợ]] y", t)
        assertEquals(12, cursor)
        val (t2, c2) = Links.complete("x [[đi", 2, 6, "Đi chợ")
        assertEquals("x [[Đi chợ]]", t2)
        assertEquals(t2.length, c2)
    }
}

class TemplatesTest {
    @Test fun placeholdersAreFilled() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val at = LocalDateTime.of(2026, 11, 14, 8, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Nhật ký 14/11/2026 08:05 Thứ Bảy", Templates.fill("Nhật ký {{date}} {{TIME}} {{weekday}}", at, zone))
        assertEquals("không đổi", Templates.fill("không đổi", at, zone))
    }
}

class CryptoTest {
    private val salt = ByteArray(16) { it.toByte() }

    @Test fun encryptsAndDecrypts() {
        val key = Crypto.deriveKey("mật khẩu dài", salt, 1000)
        val plain = "Ghi chú bí mật".encodeToByteArray()
        val sealed = Crypto.encrypt(key, plain)
        assertTrue(Crypto.isEncrypted(sealed))
        assertFalse(sealed.decodeToString().contains("bí mật"))
        assertArrayEquals(plain, Crypto.decrypt(key, sealed))
        // Two encryptions of the same text differ (random nonce).
        assertFalse(sealed.contentEquals(Crypto.encrypt(key, plain)))
    }

    @Test fun plainDataPassesThrough() {
        val plain = "{\"id\":\"x\"}".encodeToByteArray()
        assertFalse(Crypto.isEncrypted(plain))
        assertArrayEquals(plain, Crypto.decrypt(null, plain))
    }

    @Test fun wrongPassphraseIsRefused() {
        val key = Crypto.deriveKey("đúng rồi", salt, 1000)
        val config = Crypto.newConfig(key, salt, 1000)
        assertNotNull(Crypto.unlock("đúng rồi", config))
        assertNull(Crypto.unlock("sai rồi", config))
        val other = Crypto.deriveKey("sai rồi", salt, 1000)
        val sealed = Crypto.encrypt(key, byteArrayOf(1, 2, 3))
        assertTrue(runCatching { Crypto.decrypt(other, sealed) }.exceptionOrNull() is Crypto.WrongKey)
        assertTrue(runCatching { Crypto.decrypt(null, sealed) }.exceptionOrNull() is Crypto.WrongKey)
    }

    @Test fun keyStringRoundTrip() {
        val key = Crypto.deriveKey("abc", salt, 1000)
        assertArrayEquals(key, Crypto.keyFromString(Crypto.keyToString(key)))
        assertNull(Crypto.keyFromString("not base64 !!"))
    }
}

class HtmlTest {
    @Test fun textIsEscaped() {
        val html = Html.body("<script>alert(1)</script> **đậm**")
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("đậm"))
    }

    @Test fun checklistAndDocument() {
        val list = Html.checklist(listOf(CheckItem("Sữa", false), CheckItem("Trứng", true), CheckItem(" ", false)))
        assertEquals(2, Regex("<li").findAll(list).count())
        val doc = Html.document("A & B", listOf(Html.Page("Tiêu đề", "chữ", null, emptyList(), "Ghi Chú")))
        assertTrue(doc.contains("<title>A &amp; B</title>"))
        assertTrue(doc.contains("Tiêu đề"))
    }
}

class TemplateSyncTest {
    @Test fun templateFlagSurvivesDrive() {
        val n = Note(id = "t1", title = "Họp {{date}}", createdAt = 1, updatedAt = 2, template = true)
        val json = SyncPlan.json.encodeToString(NoteDto.serializer(), NoteDto.of(n, emptyList(), "dev"))
        val back = SyncPlan.json.decodeFromString(NoteDto.serializer(), json).toNote(null)
        assertTrue(back.template)
        // Files written before templates existed have no "template" field: a normal note.
        val old = SyncPlan.json.decodeFromString(NoteDto.serializer(), """{"id":"x","title":"a"}""").toNote(null)
        assertFalse(old.template)
    }
}

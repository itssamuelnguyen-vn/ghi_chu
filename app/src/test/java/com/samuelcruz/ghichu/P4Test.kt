package com.samuelcruz.ghichu

import com.samuelcruz.ghichu.core.Daily
import com.samuelcruz.ghichu.core.Diff
import com.samuelcruz.ghichu.core.Html
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.data.Attachment
import com.samuelcruz.ghichu.data.AttachmentKind
import com.samuelcruz.ghichu.sync.AttachmentDto
import com.samuelcruz.ghichu.sync.SyncPlan
import com.samuelcruz.ghichu.widget.ListSource
import com.samuelcruz.ghichu.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DiffTest {
    @Test fun changedLinesOnly() {
        val d = Diff.lines("a\nb\nc\nd", "a\nB\nc\nd\ne")
        assertEquals(
            listOf(
                Diff.Line(Diff.Op.SAME, "a"), Diff.Line(Diff.Op.REMOVED, "b"), Diff.Line(Diff.Op.ADDED, "B"),
                Diff.Line(Diff.Op.SAME, "c"), Diff.Line(Diff.Op.SAME, "d"), Diff.Line(Diff.Op.ADDED, "e"),
            ),
            d,
        )
        assertEquals(2 to 1, Diff.counts(d))
    }

    @Test fun compactKeepsContext() {
        val old = (1..20).joinToString("\n")
        val new = old.replace("\n10\n", "\nmười\n")
        val c = Diff.compact(Diff.lines(old, new), context = 1)
        // "…", 9, -10, +mười, 11, "…"
        assertEquals(6, c.size)
        assertNull(c.first())
        assertNull(c.last())
        assertTrue(Diff.compact(Diff.lines("x", "x")).isEmpty())
    }
}

class TableTest {
    private val text = "Trước\n| Món | Giá |\n|:---|---:|\n| Phở | 50k |\n| Bún \\| chả | 45k |\nSau"

    @Test fun parsesHeaderAlignAndRows() {
        val lines = text.split('\n')
        assertNull(Markdown.table(lines, 0))
        val t = Markdown.table(lines, 1)!!
        assertEquals(listOf("Món", "Giá"), t.header)
        assertEquals(listOf(Markdown.Align.START, Markdown.Align.END), t.align)
        assertEquals(listOf(listOf("Phở", "50k"), listOf("Bún | chả", "45k")), t.rows)
        assertEquals(4, t.lineCount)
    }

    @Test fun notATableWithoutSeparator() {
        assertNull(Markdown.table(listOf("a | b", "c | d"), 0))
        assertNull(Markdown.table(listOf("tiêu đề", "---"), 0))
    }

    @Test fun plainAndHtml() {
        val plain = Markdown.plain(text)
        assertFalse(plain.contains("---"))
        assertTrue(plain.contains("Phở · 50k"))
        val html = Html.body(text)
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<th>Món</th>"))
        assertTrue(html.contains("<td style=\"text-align:right\">50k</td>"))
        assertTrue(html.contains("<p>Sau</p>"))
    }

    @Test fun templateIsATable() {
        val t = Markdown.tableTemplate("Cột").split('\n')
        val table = Markdown.table(t, 0)!!
        assertEquals(listOf("Cột 1", "Cột 2"), table.header)
        assertEquals(2, table.rows.size)
    }
}

class RepeatBetweenTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(y: Int, m: Int, d: Int, h: Int = 8) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun firesInAMonth() {
        val nov = at(2026, 11, 1, 0)
        val dec = at(2026, 12, 1, 0)
        assertEquals(30, Repeat.between(at(2026, 10, 5), Repeat.DAILY, nov, dec, zone).size)
        assertEquals(listOf(at(2026, 11, 2), at(2026, 11, 9), at(2026, 11, 16), at(2026, 11, 23), at(2026, 11, 30)),
            Repeat.between(at(2026, 10, 5), Repeat.WEEKLY, nov, dec, zone))
        assertEquals(21, Repeat.between(at(2026, 10, 5), Repeat.WEEKDAYS, nov, dec, zone).size)
        assertEquals(listOf(at(2026, 11, 14)), Repeat.between(at(2026, 11, 14), Repeat.NONE, nov, dec, zone))
        assertTrue(Repeat.between(at(2026, 12, 14), Repeat.MONTHLY, nov, dec, zone).isEmpty())
    }
}

class DailyAndFilesTest {
    @Test fun dailyTitle() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val t = LocalDateTime.of(2026, 11, 14, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("14/11/2026", Daily.title(null, t, zone))
        assertEquals("14/11/2026", Daily.title("Nhật ký", t, zone))
        assertEquals("Nhật ký 14/11/2026", Daily.title("Nhật ký {{date}}", t, zone))
    }

    @Test fun fileExtensions() {
        assertEquals("pdf", AttachmentKind.extOf("Báo cáo.PDF"))
        assertEquals("docx", AttachmentKind.extOf("a.b.docx"))
        assertEquals("bin", AttachmentKind.extOf("README"))
        assertEquals("bin", AttachmentKind.extOf("x.tên lạ"))
    }

    @Test fun fileNameSurvivesSyncAndOldFilesStillRead() {
        val a = Attachment("a1", "n1", AttachmentKind.FILE, "pdf", 5, name = "Hợp đồng.pdf")
        val json = SyncPlan.json.encodeToString(AttachmentDto.serializer(), AttachmentDto.of(a))
        assertEquals("Hợp đồng.pdf", SyncPlan.json.decodeFromString(AttachmentDto.serializer(), json).toAttachment("n1", null).displayName)
        val old = SyncPlan.json.decodeFromString(AttachmentDto.serializer(), """{"id":"p","kind":0,"ext":"jpg","createdAt":1}""")
        assertEquals("p.jpg", old.toAttachment("n1", null).displayName)
    }

    @Test fun listWidgetSources() {
        val notes = listOf(
            Note("1", "a", createdAt = 1, updatedAt = 3, pinned = true),
            Note("2", "b", createdAt = 1, updatedAt = 5, labels = ",L,"),
            Note("3", "c", createdAt = 1, updatedAt = 9, archived = true, pinned = true),
            Note("4", "d", createdAt = 1, updatedAt = 7, trashedAt = 1),
        )
        assertEquals(listOf("1"), ListSource.notes(ListSource.PINNED, notes).map { it.id })
        assertEquals(listOf("2"), ListSource.notes(ListSource.label("L"), notes).map { it.id })
        assertEquals(listOf("2", "1"), ListSource.notes(ListSource.ALL, notes).map { it.id })
    }
}

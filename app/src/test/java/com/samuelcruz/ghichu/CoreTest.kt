package com.samuelcruz.ghichu

import com.samuelcruz.ghichu.core.CheckItem
import com.samuelcruz.ghichu.core.Checklist
import com.samuelcruz.ghichu.core.Markdown
import com.samuelcruz.ghichu.core.MdEdit
import com.samuelcruz.ghichu.core.Repeat
import com.samuelcruz.ghichu.core.fileNameOf
import com.samuelcruz.ghichu.core.fold
import com.samuelcruz.ghichu.core.matchRanges
import com.samuelcruz.ghichu.core.matchesAll
import com.samuelcruz.ghichu.core.searchWords
import com.samuelcruz.ghichu.core.wordCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TextTest {
    @Test fun foldKeepsLengthAndDropsAccents() {
        val s = "Cà phê Đen ở Huế"
        assertEquals(s.length, s.fold().length)
        assertEquals("ca phe den o hue", s.fold())
        assertEquals("nguyen", "Nguyễn".fold())
    }

    @Test fun searchMatchesEveryWordInAnyOrder() {
        val text = "Mua sữa và trứng ở chợ".fold()
        assertTrue(matchesAll(text, searchWords("cho sua")))
        assertFalse(matchesAll(text, searchWords("sua banh")))
    }

    @Test fun highlightRangesPointIntoTheOriginal() {
        val text = "Đi chợ mua Sữa"
        val r = matchRanges(text, searchWords("sua"))
        assertEquals(1, r.size)
        assertEquals("Sữa", text.substring(r[0].first, r[0].last + 1))
    }

    @Test fun wordsAndFileNames() {
        assertEquals(4, wordCount("Hôm nay trời đẹp"))
        assertEquals(0, wordCount("  - * "))
        assertEquals("Đi chợ - Tết", fileNameOf("Đi chợ / Tết", "x"))
        assertEquals("x", fileNameOf("  ", "x"))
    }
}

class MarkdownTest {
    @Test fun lineKinds() {
        assertEquals(Markdown.Block.H1, Markdown.line("# Tiêu đề").block)
        assertEquals(Markdown.Block.H3, Markdown.line("#### nhỏ").block)
        assertEquals(Markdown.Block.PARAGRAPH, Markdown.line("#không phải").block)
        val task = Markdown.line("  - [x] xong")
        assertEquals(Markdown.Block.TASK, task.block)
        assertTrue(task.checked)
        assertEquals(2, task.indent)
        assertEquals(8, task.prefix)
        assertEquals(Markdown.Block.BULLET, Markdown.line("* mục").block)
        assertEquals(12, Markdown.line("12. mười hai").number)
        assertEquals(Markdown.Block.RULE, Markdown.line("---").block)
        assertEquals(Markdown.Block.QUOTE, Markdown.line("> trích").block)
    }

    @Test fun codeFencesHideMarkdownInside() {
        val l = Markdown.lines("```\n# not a heading\n```\n# heading")
        assertEquals(Markdown.Block.FENCE, l[0].second.block)
        assertEquals(Markdown.Block.CODE, l[1].second.block)
        assertEquals(Markdown.Block.H1, l[3].second.block)
    }

    @Test fun inlineSpans() {
        val s = Markdown.inline("a **đậm _nghiêng_** và ~~gạch~~ `mã *không*` [web](https://x.vn) www.google.com.")
        val styles = s.map { it.style }
        assertTrue(Markdown.Style.BOLD in styles)
        assertTrue(Markdown.Style.ITALIC in styles)
        assertTrue(Markdown.Style.STRIKE in styles)
        assertEquals(1, s.count { it.style == Markdown.Style.ITALIC }) // the * inside code is not italic
        assertEquals("https://x.vn", s.first { it.style == Markdown.Style.LINK }.url)
        val url = s.first { it.style == Markdown.Style.URL }
        assertEquals("https://www.google.com", url.url) // trailing dot not part of the link
    }

    @Test fun snakeCaseIsNotItalic() {
        assertTrue(Markdown.inline("file_name_here").none { it.style == Markdown.Style.ITALIC })
    }

    @Test fun plainRemovesMarkers() {
        assertEquals("Tiêu đề\n• đậm\n☐ việc\n✓ xong", Markdown.plain("# Tiêu đề\n- **đậm**\n- [ ] việc\n- [x] xong"))
    }
}

class MdEditTest {
    @Test fun enterContinuesLists() {
        val r = MdEdit.afterNewline("- sữa\n", 6)!!
        assertEquals("- sữa\n- ", r.text)
        assertEquals(8, r.selStart)
        assertEquals("1. a\n2. ", MdEdit.afterNewline("1. a\n", 5)!!.text)
        assertEquals("- [x] a\n- [ ] ", MdEdit.afterNewline("- [x] a\n", 8)!!.text)
        assertNull(MdEdit.afterNewline("plain\n", 6))
    }

    @Test fun enterOnEmptyItemEndsTheList() {
        val r = MdEdit.afterNewline("- a\n- \nrest", 7)!!
        assertEquals("- a\nrest", r.text)
        assertEquals(4, r.selStart)
    }

    @Test fun wrapAndUnwrap() {
        val w = MdEdit.wrap("hello world", 6, 11, "**")
        assertEquals("hello **world**", w.text)
        assertEquals(8, w.selStart)
        val u = MdEdit.wrap(w.text, w.selStart, w.selEnd, "**")
        assertEquals("hello world", u.text)
        val empty = MdEdit.wrap("ab", 1, 1, "*")
        assertEquals("a**b", empty.text)
        assertEquals(2, empty.selStart)
    }

    @Test fun toggleLines() {
        assertEquals("- a\n- b", MdEdit.toggleLines("a\nb", 0, 3, MdEdit.LineKind.BULLET).text)
        assertEquals("a\nb", MdEdit.toggleLines("- a\n- b", 0, 7, MdEdit.LineKind.BULLET).text)
        assertEquals("1. a\n2. b", MdEdit.toggleLines("- a\n- b", 0, 7, MdEdit.LineKind.NUMBERED).text)
        val h = MdEdit.toggleLines("Tên", 1, 1, MdEdit.LineKind.HEADING)
        assertEquals("# Tên", h.text)
        assertEquals(3, h.selStart)
        assertEquals("## Tên", MdEdit.toggleLines(h.text, 3, 3, MdEdit.LineKind.HEADING).text)
    }

    @Test fun toggleTaskWhileReading() {
        assertEquals("x\n- [x] a", MdEdit.toggleTask("x\n- [ ] a", 1))
        assertEquals("x\n- [ ] a", MdEdit.toggleTask("x\n- [x] a", 1))
        assertEquals("x\nplain", MdEdit.toggleTask("x\nplain", 1))
    }
}

class ChecklistTest {
    @Test fun roundTrip() {
        val items = listOf(CheckItem("sữa"), CheckItem("trứng", true), CheckItem(""))
        assertEquals(items, Checklist.parse(Checklist.format(items)))
    }

    @Test fun textToChecklistAndBack() {
        val list = Checklist.fromText("Đi chợ\n\n- rau\n2. thịt\n- [x] gạo")
        assertEquals(listOf(CheckItem("Đi chợ"), CheckItem("rau"), CheckItem("thịt"), CheckItem("gạo", true)), list)
        assertEquals("- [ ] Đi chợ\n- [ ] rau\n- [ ] thịt\n- [x] gạo", Checklist.toText(list))
    }

    @Test fun checkedGoLast() {
        val items = listOf(CheckItem("a", true), CheckItem("b"), CheckItem("c", true), CheckItem("d"))
        assertEquals(listOf(1, 3, 0, 2), Checklist.displayOrder(items, true))
        assertEquals(listOf(0, 1, 2, 3), Checklist.displayOrder(items, false))
    }
}

class RepeatTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun ms(y: Int, mo: Int, d: Int, h: Int = 8, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun oneTime() {
        assertEquals(ms(2026, 11, 10), Repeat.next(ms(2026, 11, 10), Repeat.NONE, ms(2026, 11, 8), zone))
        assertNull(Repeat.next(ms(2026, 11, 1), Repeat.NONE, ms(2026, 11, 8), zone))
        assertEquals(ms(2026, 11, 1), Repeat.next(ms(2026, 11, 1), Repeat.NONE, ms(2026, 11, 8), zone, includePast = true))
    }

    @Test fun dailyAndWeekly() {
        assertEquals(ms(2026, 11, 9), Repeat.next(ms(2020, 1, 1), Repeat.DAILY, ms(2026, 11, 8, 9), zone))
        assertEquals(ms(2026, 11, 8), Repeat.next(ms(2020, 1, 1), Repeat.DAILY, ms(2026, 11, 8, 7), zone))
        // 2 Nov 2026 is a Monday.
        assertEquals(ms(2026, 11, 9), Repeat.next(ms(2026, 11, 2), Repeat.WEEKLY, ms(2026, 11, 3), zone))
    }

    @Test fun monthlyHasNoDrift() {
        val first = ms(2026, 1, 31)
        assertEquals(ms(2026, 2, 28), Repeat.next(first, Repeat.MONTHLY, ms(2026, 2, 1), zone))
        assertEquals(ms(2026, 3, 31), Repeat.next(first, Repeat.MONTHLY, ms(2026, 3, 1), zone))
    }

    @Test fun weekdaysSkipTheWeekend() {
        // Friday 6 Nov 2026, 08:00 → asked on Friday 10:00 → Monday 9 Nov.
        assertEquals(ms(2026, 11, 9), Repeat.next(ms(2026, 11, 6), Repeat.WEEKDAYS, ms(2026, 11, 6, 10), zone))
        // Starting on a Saturday waits for Monday.
        assertEquals(ms(2026, 11, 9), Repeat.next(ms(2026, 11, 7), Repeat.WEEKDAYS, ms(2026, 11, 1), zone))
    }
}

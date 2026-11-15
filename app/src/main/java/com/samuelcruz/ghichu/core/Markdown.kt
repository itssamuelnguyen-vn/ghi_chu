package com.samuelcruz.ghichu.core

import java.util.regex.Pattern

/**
 * The small Markdown dialect notes are written in (no library): headings `#`, `##`, `###`, lists `-` / `1.`,
 * tasks `- [ ]` / `- [x]`, quotes `>`, code fences ```, rules `---`; inline **bold**, *italic*, ~~strike~~, `code`,
 * [links](url), bare web addresses and `[[links to other notes]]`; tables (`| a | b |` + `|---|---|`). Only parsing lives here (plain Kotlin, unit-tested); `ui/MarkdownStyle.kt`
 * turns the result into styled text for the editor and for reading.
 */
object Markdown {
    enum class Block { PARAGRAPH, H1, H2, H3, BULLET, NUMBERED, TASK, QUOTE, FENCE, CODE, RULE }

    /**
     * One line: its [block] kind, the length of the marker at its start ([prefix], e.g. 2 for "- ", 6 for
     * "- [ ] "), the leading spaces ([indent]), whether a task is [checked] and the [number] of a numbered item.
     */
    data class Line(
        val block: Block,
        val prefix: Int = 0,
        val indent: Int = 0,
        val checked: Boolean = false,
        val number: Int = 0,
    )

    private val heading = Regex("^(#{1,6}) ")
    private val task = Regex("^( *)[-*+] \\[([ xX])] ")
    private val bullet = Regex("^( *)[-*+] ")
    private val numbered = Regex("^( *)(\\d{1,4})[.)] ")
    private val quote = Regex("^> ?")
    private val rule = Regex("^ {0,3}([-*_])( *\\1){2,} *$")

    /** Kind of [line]; [inCode] = the line is inside a ``` fence. */
    fun line(line: String, inCode: Boolean = false): Line {
        if (line.trimStart().startsWith("```")) return Line(Block.FENCE, prefix = line.length)
        if (inCode) return Line(Block.CODE)
        heading.find(line)?.let {
            val level = it.groupValues[1].length
            return Line(if (level == 1) Block.H1 else if (level == 2) Block.H2 else Block.H3, it.value.length)
        }
        task.find(line)?.let {
            return Line(Block.TASK, it.value.length, it.groupValues[1].length, checked = it.groupValues[2] != " ")
        }
        if (rule.matches(line)) return Line(Block.RULE, prefix = line.length)
        bullet.find(line)?.let { return Line(Block.BULLET, it.value.length, it.groupValues[1].length) }
        numbered.find(line)?.let {
            return Line(Block.NUMBERED, it.value.length, it.groupValues[1].length, number = it.groupValues[2].toInt())
        }
        quote.find(line)?.let { return Line(Block.QUOTE, it.value.length) }
        return Line(Block.PARAGRAPH)
    }

    /** [line] for every line of [text], following code fences. */
    fun lines(text: String): List<Pair<String, Line>> {
        var inCode = false
        return text.split('\n').map { l ->
            val info = line(l, inCode)
            if (info.block == Block.FENCE) inCode = !inCode
            l to info
        }
    }

    /** NOTE_LINK = `[[Title]]`, a link to another note (see [Links]). */
    enum class Style { BOLD, ITALIC, STRIKE, CODE, LINK, URL, NOTE_LINK }

    /**
     * An inline piece: the whole match is [start] until [end]; the visible text is [contentStart] until
     * [contentEnd] (the rest are markers like `**`). [url] for links and bare addresses.
     */
    data class Span(
        val style: Style,
        val start: Int,
        val end: Int,
        val contentStart: Int,
        val contentEnd: Int,
        val url: String? = null,
    )

    private class Rule(val style: Style, val pattern: Pattern, val nested: Boolean)

    private val rules = listOf(
        Rule(Style.CODE, Pattern.compile("`([^`\\n]+)`"), false),
        Rule(Style.NOTE_LINK, Pattern.compile("\\[\\[([^\\[\\]\\n]+)]]"), false),
        Rule(Style.LINK, Pattern.compile("\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)"), true),
        Rule(Style.URL, Pattern.compile("(?<![\\w@/])(?:https?://|www\\.)[^\\s<>()]+"), false),
        Rule(Style.BOLD, Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*"), true),
        Rule(Style.BOLD, Pattern.compile("(?<!\\w)__(?=\\S)(.+?)(?<=\\S)__(?!\\w)"), true),
        Rule(Style.STRIKE, Pattern.compile("~~(?=\\S)(.+?)(?<=\\S)~~"), true),
        Rule(Style.ITALIC, Pattern.compile("\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*"), true),
        Rule(Style.ITALIC, Pattern.compile("(?<!\\w)_(?=\\S)(.+?)(?<=\\S)_(?!\\w)"), true),
    )

    /** Inline spans of [text] (usually one line), outer spans before the spans nested inside them. */
    fun inline(text: String): List<Span> {
        val out = mutableListOf<Span>()
        scan(text, 0, text.length, out)
        return out
    }

    private fun scan(text: String, from: Int, to: Int, out: MutableList<Span>) {
        val matchers = rules.map { r -> r.pattern.matcher(text).also { it.useTransparentBounds(true) } }
        var i = from
        while (i < to) {
            val c = text[i]
            if (c != '`' && c != '[' && c != '*' && c != '_' && c != '~' && c != 'h' && c != 'w') { i++; continue }
            var matched = false
            for ((k, rule) in rules.withIndex()) {
                val m = matchers[k]
                m.region(i, to)
                if (!m.lookingAt()) continue
                var end = m.end()
                val span = when (rule.style) {
                    Style.URL -> {
                        while (end > i && text[end - 1] in ".,;:!?'\"") end--
                        val raw = text.substring(i, end)
                        Span(Style.URL, i, end, i, end, if (raw.startsWith("www.")) "https://$raw" else raw)
                    }
                    Style.LINK -> Span(Style.LINK, i, end, m.start(1), m.end(1), m.group(2))
                    Style.NOTE_LINK -> Span(Style.NOTE_LINK, i, end, m.start(1), m.end(1), m.group(1).trim())
                    else -> Span(rule.style, i, end, m.start(1), m.end(1))
                }
                out += span
                if (rule.nested) scan(text, span.contentStart, span.contentEnd, out)
                i = end
                matched = true
                break
            }
            if (!matched) i++
        }
    }

    /** Text without any Markdown markers, e.g. for a one-line preview or a notification. */
    fun plain(text: String): String = lines(text).joinToString("\n") { (l, info) ->
        when (info.block) {
            Block.FENCE, Block.RULE -> ""
            Block.TASK -> (if (info.checked) "✓ " else "☐ ") + inlinePlain(l.substring(info.prefix))
            Block.BULLET -> "• " + inlinePlain(l.substring(info.prefix))
            Block.CODE -> l
            Block.PARAGRAPH -> when {
                tableSeparator.matches(l) -> ""
                l.trimStart().startsWith("|") -> cells(l).joinToString(" · ") { inlinePlain(it) }
                else -> inlinePlain(l)
            }
            else -> inlinePlain(l.substring(info.prefix))
        }
    }.replace(Regex("\n{3,}"), "\n\n").trim()

    // Tables (GitHub style): a header row, a separator row, then rows. Outer pipes are optional.

    enum class Align { START, CENTER, END }

    /** A table found at some line: [lineCount] lines long (header + separator + rows). */
    data class Table(val header: List<String>, val align: List<Align>, val rows: List<List<String>>, val lineCount: Int) {
        val columns get() = header.size
    }

    private val tableSeparator = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")

    /** The cells of a table row: split on `|` (not `\\|`), outer pipes dropped, trimmed. */
    fun cells(row: String): List<String> {
        var r = row.trim()
        if (r.startsWith("|")) r = r.substring(1)
        if (r.endsWith("|") && !r.endsWith("\\|")) r = r.substring(0, r.length - 1)
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var k = 0
        while (k < r.length) {
            val c = r[k]
            if (c == '\\' && k + 1 < r.length && r[k + 1] == '|') { cur.append('|'); k += 2; continue }
            if (c == '|') { out += cur.toString().trim(); cur.clear() } else cur.append(c)
            k++
        }
        out += cur.toString().trim()
        return out
    }

    /** The table starting at line [start] of [lines], or null if none starts there. */
    fun table(lines: List<String>, start: Int): Table? {
        if (start + 1 >= lines.size) return null
        val head = lines[start]
        val sep = lines[start + 1]
        if (!head.contains('|') || !sep.contains('|') || !tableSeparator.matches(sep)) return null
        val header = cells(head)
        val seps = cells(sep)
        val n = header.size
        val align = List(n) { k ->
            val s = seps.getOrNull(k).orEmpty()
            when {
                s.startsWith(":") && s.endsWith(":") -> Align.CENTER
                s.endsWith(":") -> Align.END
                else -> Align.START
            }
        }
        val rows = mutableListOf<List<String>>()
        var k = start + 2
        while (k < lines.size && lines[k].contains('|') && lines[k].isNotBlank()) {
            val c = cells(lines[k])
            rows += List(n) { c.getOrElse(it) { "" } }
            k++
        }
        return Table(header, align, rows, k - start)
    }

    /** An empty table to start from (format bar). */
    fun tableTemplate(col: String, columns: Int = 2, rows: Int = 2): String = buildString {
        append((1..columns).joinToString(" | ", "| ", " |") { "$col $it" }).append('\n')
        append((1..columns).joinToString(" | ", "| ", " |") { "---" }).append('\n')
        repeat(rows) { r ->
            append((1..columns).joinToString(" | ", "| ", " |") { " " })
            if (r < rows - 1) append('\n')
        }
    }

    private fun inlinePlain(text: String): String {
        val spans = inline(text)
        if (spans.isEmpty()) return text
        val hidden = BooleanArray(text.length)
        for (s in spans) {
            for (k in s.start until s.contentStart) hidden[k] = true
            for (k in s.contentEnd until s.end) hidden[k] = true
        }
        return buildString { text.forEachIndexed { k, ch -> if (!hidden[k]) append(ch) } }
    }
}

/** Editing helpers for the Markdown toolbar and list continuation. Text in, text + selection out. */
object MdEdit {
    data class Result(val text: String, val selStart: Int, val selEnd: Int = selStart)

    /**
     * Called after a newline was typed at [cursor] - 1. On a list line it continues the list ("- " / "2. " /
     * "- [ ] "); on an empty list item it ends the list instead. Null = nothing to do.
     */
    fun afterNewline(text: String, cursor: Int): Result? {
        if (cursor <= 0 || cursor > text.length || text[cursor - 1] != '\n') return null
        val prevStart = text.lastIndexOf('\n', cursor - 2).let { if (cursor < 2) 0 else it + 1 }
        val prev = text.substring(prevStart, cursor - 1)
        val info = Markdown.line(prev)
        val marker = when (info.block) {
            Markdown.Block.BULLET -> prev.substring(0, info.prefix)
            Markdown.Block.TASK -> " ".repeat(info.indent) + prev.trimStart()[0] + " [ ] "
            Markdown.Block.NUMBERED -> " ".repeat(info.indent) + (info.number + 1) + prev.substring(info.indent).let {
                it[it.indexOfFirst { ch -> !ch.isDigit() }] + " "
            }
            Markdown.Block.QUOTE -> "> "
            else -> return null
        }
        if (prev.substring(info.prefix).isBlank()) {
            // Enter on an empty item: drop its marker and stay on that (now empty) line.
            val t = text.substring(0, prevStart) + text.substring(cursor)
            return Result(t, prevStart)
        }
        val t = text.substring(0, cursor) + marker + text.substring(cursor)
        return Result(t, cursor + marker.length)
    }

    /** Puts [marker] around the selection (or removes it if already there); with no selection, inserts a pair. */
    fun wrap(text: String, start: Int, end: Int, marker: String): Result {
        val m = marker.length
        if (start >= m && end + m <= text.length &&
            text.substring(start - m, start) == marker && text.substring(end, end + m) == marker
        ) {
            val t = text.substring(0, start - m) + text.substring(start, end) + text.substring(end + m)
            return Result(t, start - m, end - m)
        }
        val sel = text.substring(start, end)
        if (sel.length > 2 * m && sel.startsWith(marker) && sel.endsWith(marker)) {
            val inner = sel.substring(m, sel.length - m)
            return Result(text.substring(0, start) + inner + text.substring(end), start, start + inner.length)
        }
        val t = text.substring(0, start) + marker + sel + marker + text.substring(end)
        return Result(t, start + m, end + m)
    }

    /** "[selection](https://)" with the cursor ready to type the address. */
    fun link(text: String, start: Int, end: Int): Result {
        val sel = text.substring(start, end)
        val label = sel.ifEmpty { "link" }
        val before = text.substring(0, start) + "[" + label + "]("
        val url = "https://"
        val t = before + url + ")" + text.substring(end)
        return Result(t, before.length + url.length)
    }

    enum class LineKind { BULLET, NUMBERED, TASK, QUOTE, HEADING }

    /**
     * Turns every line touched by the selection into [kind], or back into plain text if all of them already are.
     * HEADING cycles # → ## → ### → plain.
     */
    fun toggleLines(text: String, start: Int, end: Int, kind: LineKind): Result {
        val first = text.lastIndexOf('\n', start - 1) + 1
        var last = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        if (last < first) last = first
        val block = text.substring(first, last)
        val lines = block.split('\n')
        val infos = lines.map { Markdown.line(it) }
        val target = when (kind) {
            LineKind.BULLET -> Markdown.Block.BULLET
            LineKind.NUMBERED -> Markdown.Block.NUMBERED
            LineKind.TASK -> Markdown.Block.TASK
            LineKind.QUOTE -> Markdown.Block.QUOTE
            LineKind.HEADING -> null
        }
        val allAlready = target != null && infos.all { it.block == target }
        var n = 0
        val changed = lines.mapIndexed { i, l ->
            val info = infos[i]
            val content = l.substring(info.prefix)
            if (kind == LineKind.HEADING) {
                when (info.block) {
                    Markdown.Block.H1 -> "## $content"
                    Markdown.Block.H2 -> "### $content"
                    Markdown.Block.H3 -> content
                    else -> "# $content"
                }
            } else if (allAlready) {
                content
            } else {
                n++
                when (kind) {
                    LineKind.BULLET -> "- $content"
                    LineKind.NUMBERED -> "$n. $content"
                    LineKind.TASK -> "- [ ] $content"
                    else -> "> $content"
                }
            }
        }.joinToString("\n")
        val t = text.substring(0, first) + changed + text.substring(last)
        val delta = changed.length - block.length
        return if (start == end) {
            // Keep the cursor at the same place in the line's content.
            val lineIdx = text.substring(first, start).count { it == '\n' }
            val oldPrefix = infos[lineIdx].prefix
            val newPrefix = Markdown.line(changed.split('\n')[lineIdx]).prefix
            val c = (start + newPrefix - oldPrefix).coerceIn(first, first + changed.length)
            Result(t, c)
        } else {
            Result(t, first, last + delta)
        }
    }

    /** Toggles the task on the line starting at [lineStart] ("- [ ]" ↔ "- [x]"), used when reading a note. */
    fun toggleTask(text: String, lineIndex: Int): String {
        val lines = text.split('\n').toMutableList()
        if (lineIndex !in lines.indices) return text
        val l = lines[lineIndex]
        val info = Markdown.line(l)
        if (info.block != Markdown.Block.TASK) return text
        val box = l.indexOf('[', info.indent)
        lines[lineIndex] = l.substring(0, box + 1) + (if (info.checked) " " else "x") + l.substring(box + 2)
        return lines.joinToString("\n")
    }
}

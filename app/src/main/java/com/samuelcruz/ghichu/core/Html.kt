package com.samuelcruz.ghichu.core

/**
 * Notes as HTML, for PDF export (Android prints the HTML page; "Save as PDF" is one of its printers). Plain
 * Kotlin, unit-tested. Pictures come in as ready `src` values (data: URIs).
 */
object Html {
    /** One note to put in the PDF. [items] = checklist rows (null for a text note). */
    data class Page(
        val title: String,
        val markdown: String,
        val items: List<CheckItem>?,
        val pictures: List<String>,
        val footer: String,
        /** Background colour of the note as `#rrggbb`, null = white. */
        val color: String? = null,
    )

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    /** Inline Markdown of one line: bold, italic, strike, code, links; `[[note links]]` as plain bold text. */
    fun inline(text: String): String {
        val spans = Markdown.inline(text)
        if (spans.isEmpty()) return escape(text)
        val opens = HashMap<Int, StringBuilder>()
        val closes = HashMap<Int, StringBuilder>()
        val hidden = BooleanArray(text.length)
        for (s in spans) {
            for (k in s.start until s.contentStart) hidden[k] = true
            for (k in s.contentEnd until s.end) hidden[k] = true
            val (open, close) = when (s.style) {
                Markdown.Style.BOLD -> "<b>" to "</b>"
                Markdown.Style.ITALIC -> "<i>" to "</i>"
                Markdown.Style.STRIKE -> "<s>" to "</s>"
                Markdown.Style.CODE -> "<code>" to "</code>"
                Markdown.Style.NOTE_LINK -> "<b class=\"nl\">" to "</b>"
                Markdown.Style.LINK, Markdown.Style.URL -> "<a href=\"${escape(s.url.orEmpty())}\">" to "</a>"
            }
            opens.getOrPut(s.contentStart) { StringBuilder() }.append(open)
            // Inner spans close first: prepend.
            closes.getOrPut(s.contentEnd) { StringBuilder() }.insert(0, close)
        }
        return buildString {
            for (i in 0..text.length) {
                closes[i]?.let { append(it) }
                opens[i]?.let { append(it) }
                if (i < text.length && !hidden[i]) append(escape(text[i].toString()))
            }
        }
    }

    /** The body of a text note as HTML blocks. */
    fun body(markdown: String): String = buildString {
        val lines = Markdown.lines(markdown)
        val raw = lines.map { it.first }
        var list: String? = null
        fun closeList() { list?.let { append("</$it>\n") }; list = null }
        fun openList(tag: String) { if (list != tag) { closeList(); append("<$tag>\n"); list = tag } }
        var i = 0
        while (i < lines.size) {
            val (line, info) = lines[i]
            val content = line.substring(info.prefix.coerceAtMost(line.length))
            when (info.block) {
                Markdown.Block.FENCE -> {
                    closeList()
                    val code = StringBuilder()
                    var j = i + 1
                    while (j < lines.size && lines[j].second.block != Markdown.Block.FENCE) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[j].first)
                        j++
                    }
                    append("<pre>").append(escape(code.toString())).append("</pre>\n")
                    i = j + 1
                    continue
                }
                Markdown.Block.H1 -> { closeList(); append("<h2>").append(inline(content)).append("</h2>\n") }
                Markdown.Block.H2 -> { closeList(); append("<h3>").append(inline(content)).append("</h3>\n") }
                Markdown.Block.H3 -> { closeList(); append("<h4>").append(inline(content)).append("</h4>\n") }
                Markdown.Block.BULLET -> { openList("ul"); append("<li>").append(inline(content)).append("</li>\n") }
                Markdown.Block.NUMBERED -> { openList("ol"); append("<li>").append(inline(content)).append("</li>\n") }
                Markdown.Block.TASK -> {
                    openList("ul")
                    append(task(info.checked, inline(content)))
                }
                Markdown.Block.QUOTE -> { closeList(); append("<blockquote>").append(inline(content)).append("</blockquote>\n") }
                Markdown.Block.RULE -> { closeList(); append("<hr>\n") }
                Markdown.Block.CODE -> { closeList(); append("<pre>").append(escape(line)).append("</pre>\n") }
                Markdown.Block.PARAGRAPH -> {
                    closeList()
                    val t = Markdown.table(raw, i)
                    if (t != null) {
                        append(table(t))
                        i += t.lineCount
                        continue
                    }
                    if (line.isNotBlank()) append("<p>").append(inline(line)).append("</p>\n")
                }
            }
            i++
        }
        closeList()
    }

    fun table(t: Markdown.Table): String = buildString {
        fun cell(tag: String, k: Int, text: String) {
            val a = when (t.align[k]) { Markdown.Align.CENTER -> " style=\"text-align:center\""; Markdown.Align.END -> " style=\"text-align:right\""; else -> "" }
            append("<").append(tag).append(a).append(">").append(inline(text)).append("</").append(tag).append(">")
        }
        append("<table><thead><tr>")
        t.header.forEachIndexed { k, h -> cell("th", k, h) }
        append("</tr></thead><tbody>\n")
        for (r in t.rows) {
            append("<tr>")
            r.forEachIndexed { k, c -> cell("td", k, c) }
            append("</tr>\n")
        }
        append("</tbody></table>\n")
    }

    private fun task(checked: Boolean, html: String) =
        "<li class=\"task${if (checked) " done" else ""}\"><span class=\"box\">${if (checked) "☑" else "☐"}</span> $html</li>\n"

    fun checklist(items: List<CheckItem>): String = buildString {
        append("<ul>\n")
        for (it in items) if (it.text.isNotBlank()) append(task(it.checked, inline(it.text)))
        append("</ul>\n")
    }

    /** A whole printable document; each note starts on a new page. */
    fun document(title: String, pages: List<Page>): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>").append(escape(title)).append("</title><style>")
        append(CSS)
        append("</style></head><body>\n")
        pages.forEachIndexed { k, p ->
            append("<section class=\"note\"")
            if (k > 0) append(" style=\"page-break-before: always\"")
            append(">\n<div class=\"card\"")
            if (p.color != null) append(" style=\"background:").append(p.color).append("\"")
            append(">\n")
            if (p.title.isNotBlank()) append("<h1>").append(escape(p.title)).append("</h1>\n")
            for (src in p.pictures) append("<img src=\"").append(src).append("\">\n")
            append(if (p.items != null) checklist(p.items) else body(p.markdown))
            append("<div class=\"footer\">").append(escape(p.footer)).append("</div>\n</div>\n</section>\n")
        }
        append("</body></html>")
    }

    private const val CSS = """
        @page { margin: 16mm 14mm; }
        body { font-family: sans-serif; font-size: 12pt; line-height: 1.5; color: #1f1b16; margin: 0; }
        .card { border-radius: 10px; padding: 4mm 6mm; }
        h1 { font-size: 20pt; margin: 0 0 8pt; }
        h2 { font-size: 16pt; margin: 12pt 0 4pt; }
        h3 { font-size: 14pt; margin: 10pt 0 4pt; }
        h4 { font-size: 12.5pt; margin: 8pt 0 4pt; }
        p { margin: 0 0 6pt; }
        ul, ol { margin: 0 0 6pt; padding-left: 18pt; }
        li.task { list-style: none; margin-left: -18pt; }
        li.task .box { display: inline-block; width: 16pt; }
        li.done { color: #8a8580; text-decoration: line-through; }
        blockquote { margin: 0 0 6pt; padding-left: 10pt; border-left: 3px solid #d97706; font-style: italic; }
        pre { background: #f2efe9; padding: 8pt; border-radius: 6px; white-space: pre-wrap; font-size: 10.5pt; }
        code { background: #f2efe9; padding: 0 3px; border-radius: 3px; font-size: 10.5pt; }
        a { color: #b45309; }
        .nl { color: #b45309; }
        hr { border: 0; border-top: 1px solid #ccc; }
        table { border-collapse: collapse; margin: 0 0 8pt; width: 100%; page-break-inside: avoid; }
        th, td { border: 1px solid #d6d0c8; padding: 3pt 6pt; vertical-align: top; }
        th { background: #f2efe9; text-align: left; }
        img { display: block; max-width: 100%; max-height: 120mm; margin: 0 auto 8pt; border-radius: 6px; page-break-inside: avoid; }
        .footer { margin-top: 12pt; font-size: 9pt; color: #8a8580; }
    """
}

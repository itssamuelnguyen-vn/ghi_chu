package com.samuelcruz.ghichu.core

/**
 * Links between notes, written `[[Title of another note]]` (like Obsidian / a wiki). A link points to a note by its
 * title (accent and case are ignored), so it keeps working on every device without ids in the text.
 */
object Links {
    private val link = Regex("\\[\\[([^\\[\\]\\n]+)]]")
    /** An unfinished `[[que` right before the cursor. */
    private val open = Regex("\\[\\[([^\\[\\]\\n]*)$")

    /** Titles linked from [text], in order, without repeats. */
    fun targets(text: String): List<String> =
        link.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinctBy { key(it) }.toList()

    /** Comparison key: "Đi Chợ " and "di cho" are the same note. */
    fun key(title: String): String = title.trim().fold().replace(Regex("\\s+"), " ")

    /** Does [text] link to a note titled [title]? */
    fun linksTo(text: String, title: String): Boolean {
        if (title.isBlank() || !text.contains("[[")) return false
        val k = key(title)
        return link.findAll(text).any { key(it.groupValues[1]) == k }
    }

    /** The `[[` typed just before [cursor]: where it starts and what was typed after it; null when not typing a link. */
    fun queryAt(text: String, cursor: Int): Pair<Int, String>? {
        if (cursor < 2 || cursor > text.length) return null
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val m = open.find(text.substring(lineStart, cursor)) ?: return null
        return (lineStart + m.range.first) to m.groupValues[1]
    }

    /** Replaces the unfinished link starting at [start] (up to [cursor]) with `[[title]]`; returns text and cursor. */
    fun complete(text: String, start: Int, cursor: Int, title: String): Pair<String, Int> {
        // Already closed after the cursor ("[[ab|]]")? Then replace those brackets too.
        val end = if (text.startsWith("]]", cursor)) cursor + 2 else cursor
        val inserted = "[[" + title.trim() + "]]"
        return (text.substring(0, start) + inserted + text.substring(end)) to (start + inserted.length)
    }
}

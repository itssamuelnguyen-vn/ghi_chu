package com.samuelcruz.ghichu.core

import java.text.Normalizer

/**
 * Accent-free text for searching: "Cà phê Đen" → "ca phe den". Done one character at a time, so the result has
 * exactly the same length as the input and a match at index i is also at index i in the original (for highlighting).
 */
fun String.fold(): String {
    val out = CharArray(length)
    for (i in indices) out[i] = foldChar(this[i])
    return String(out)
}

private fun foldChar(c: Char): Char {
    if (c.code < 128) return c.lowercaseChar()
    if (c == 'đ' || c == 'Đ') return 'd'
    if (c.isSurrogate()) return c
    val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
    return base.firstOrNull()?.lowercaseChar() ?: c
}

/** Words of a search query, folded; empty words dropped. */
fun searchWords(query: String): List<String> = query.fold().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Every word must appear somewhere in [folded] (already [fold]ed). */
fun matchesAll(folded: String, words: List<String>): Boolean = words.all { folded.contains(it) }

/** Index ranges in [text] where any of the [words] occur (for highlighting), sorted and merged. */
fun matchRanges(text: String, words: List<String>): List<IntRange> {
    if (words.isEmpty() || text.isEmpty()) return emptyList()
    val folded = text.fold()
    val ranges = mutableListOf<IntRange>()
    for (w in words) {
        var from = 0
        while (true) {
            val i = folded.indexOf(w, from)
            if (i < 0) break
            ranges += i until i + w.length
            from = i + w.length
        }
    }
    ranges.sortBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (r in ranges) {
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last + 1) merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
        else merged += r
    }
    return merged
}

/** Number of words, counting Vietnamese syllables as words like a word processor does. */
fun wordCount(text: String): Int = text.split(Regex("[\\s\\p{Punct}&&[^'’]]+")).count { w -> w.any { it.isLetterOrDigit() } }

/** A safe file name from a note title: "Đi chợ / Tết" → "Đi chợ - Tết". */
fun fileNameOf(title: String, fallback: String): String {
    val clean = title.replace(Regex("\\s*[\\\\/:*?\"<>|\\n\\r\\t]+\\s*"), " - ").replace(Regex(" {2,}"), " ")
        .trim().trim('.').take(60).trim()
    return clean.ifEmpty { fallback }
}

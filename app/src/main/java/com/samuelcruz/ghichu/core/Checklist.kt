package com.samuelcruz.ghichu.core

/**
 * A checklist note keeps its items in the body as Markdown tasks, one per line ("- [ ] milk", "- [x] eggs"), so
 * the same text works in exports, backups and other apps. The order of lines is the user's order; checked items
 * are only shown at the bottom.
 */
data class CheckItem(val text: String, val checked: Boolean = false)

object Checklist {
    private val taskLine = Regex("^ *[-*+] \\[([ xX])] ?(.*)$")
    private val listMarker = Regex("^ *(?:[-*+•] |\\d{1,4}[.)] )")

    /** Items of a checklist body. Lines that are not tasks become unchecked items (pasted text). */
    fun parse(body: String): List<CheckItem> {
        if (body.isEmpty()) return emptyList()
        return body.split('\n').map { l ->
            val m = taskLine.find(l)
            if (m != null) CheckItem(m.groupValues[2], m.groupValues[1] != " ")
            else CheckItem(l.replace(listMarker, ""))
        }
    }

    fun format(items: List<CheckItem>): String =
        items.joinToString("\n") { (if (it.checked) "- [x] " else "- [ ] ") + it.text.replace('\n', ' ') }

    /** Text note → checklist: every non-empty line becomes an item (list markers removed, tasks keep their tick). */
    fun fromText(body: String): List<CheckItem> =
        body.split('\n').filter { it.isNotBlank() }.map { l ->
            val m = taskLine.find(l)
            if (m != null) CheckItem(m.groupValues[2].trim(), m.groupValues[1] != " ")
            else CheckItem(l.replace(listMarker, "").trim())
        }

    /** Checklist → text note: one task line per item, so nothing is lost and it can be turned back. */
    fun toText(items: List<CheckItem>): String = format(items.filter { it.text.isNotBlank() })

    /** Indices into [items] in display order: unchecked first, then checked (if [checkedLast]). */
    fun displayOrder(items: List<CheckItem>, checkedLast: Boolean): List<Int> =
        if (!checkedLast) items.indices.toList()
        else items.indices.filter { !items[it].checked } + items.indices.filter { items[it].checked }
}

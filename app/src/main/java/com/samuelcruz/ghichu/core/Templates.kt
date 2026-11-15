package com.samuelcruz.ghichu.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Note templates: a template is a note with `template = true`. When a note is made from it, these words in its
 * title and text are replaced (handy for a daily journal or meeting notes):
 * `{{date}}` → 14/11/2026, `{{time}}` → 08:30, `{{weekday}}` → Thứ Bảy / Saturday.
 */
object Templates {
    val placeholders = listOf("{{date}}", "{{time}}", "{{weekday}}")

    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun fill(text: String, millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (!text.contains("{{")) return text
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        return text
            .replace("{{date}}", dt.format(date), ignoreCase = true)
            .replace("{{time}}", dt.format(time), ignoreCase = true)
            .replace("{{weekday}}", S.weekday(dt.dayOfWeek.value), ignoreCase = true)
    }
}

/**
 * "Ghi chú hôm nay" (daily note): one note per day, found again by its title, so it also works across synced
 * phones. The title is the chosen template's title when it has {{date}} in it, otherwise just the date.
 */
object Daily {
    fun titlePattern(templateTitle: String?): String =
        if (templateTitle != null && templateTitle.contains("{{date}}", ignoreCase = true)) templateTitle else "{{date}}"

    fun title(templateTitle: String?, millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Templates.fill(titlePattern(templateTitle), millis, zone).trim()
}

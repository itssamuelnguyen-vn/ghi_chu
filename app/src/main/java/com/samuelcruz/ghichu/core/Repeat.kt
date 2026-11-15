package com.samuelcruz.ghichu.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * How a reminder repeats. A note stores the first time ([com.samuelcruz.ghichu.data.Note.reminderAt]) and the
 * rule; the n-th time is always counted from that first time (31 Jan monthly → 28 Feb → 31 Mar, no drift), so
 * two phones always compute the same next time and never fight over it while syncing.
 */
object Repeat {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKLY = 2
    const val MONTHLY = 3
    const val YEARLY = 4
    const val WEEKDAYS = 5

    val all = listOf(NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY)

    fun occurrence(first: LocalDateTime, rule: Int, n: Long): LocalDateTime = when (rule) {
        DAILY -> first.plusDays(n)
        WEEKLY -> first.plusWeeks(n)
        MONTHLY -> first.plusMonths(n)
        YEARLY -> first.plusYears(n)
        else -> first
    }

    /**
     * The first time at or after [now] (epoch millis), or null when a one-time reminder is already past
     * ([includePast] returns it anyway).
     */
    fun next(firstAt: Long, rule: Int, now: Long, zone: ZoneId = ZoneId.systemDefault(), includePast: Boolean = false): Long? {
        if (firstAt >= now) {
            if (rule == WEEKDAYS) return nextWeekday(firstAt, now, zone)
            return firstAt
        }
        if (rule == NONE) return if (includePast) firstAt else null
        if (rule == WEEKDAYS) return nextWeekday(firstAt, now, zone)
        val first = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstAt), zone)
        val nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        // Jump close to now first, then step: a daily reminder from years ago needs no thousands of steps.
        var n = when (rule) {
            DAILY -> java.time.Duration.between(first, nowDt).toDays() - 1
            WEEKLY -> java.time.Duration.between(first, nowDt).toDays() / 7 - 1
            MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(first, nowDt) - 1
            YEARLY -> java.time.temporal.ChronoUnit.YEARS.between(first, nowDt) - 1
            else -> 0
        }.coerceAtLeast(0)
        while (true) {
            val t = occurrence(first, rule, n).atZone(zone).toInstant().toEpochMilli()
            if (t >= now) return t
            n++
        }
    }

    /** Every time the reminder fires in [from, to) (the calendar). At most [limit] times. */
    fun between(firstAt: Long, rule: Int, from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault(), limit: Int = 400): List<Long> {
        if (rule == NONE) return if (firstAt in from until to) listOf(firstAt) else emptyList()
        val out = mutableListOf<Long>()
        var t = next(firstAt, rule, from, zone)
        while (t != null && t < to && out.size < limit) {
            out += t
            t = next(firstAt, rule, t + 1, zone)
        }
        return out
    }

    /** Monday to Friday at the first reminder's time of day. */
    private fun nextWeekday(firstAt: Long, now: Long, zone: ZoneId): Long {
        val first = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstAt), zone)
        var day = LocalDateTime.ofInstant(Instant.ofEpochMilli(maxOf(firstAt, now)), zone).toLocalDate()
        while (true) {
            val dt = day.atTime(first.toLocalTime())
            val t = dt.atZone(zone).toInstant().toEpochMilli()
            if (t >= now && t >= firstAt && day.dayOfWeek.value <= 5) return t
            day = day.plusDays(1)
        }
    }
}

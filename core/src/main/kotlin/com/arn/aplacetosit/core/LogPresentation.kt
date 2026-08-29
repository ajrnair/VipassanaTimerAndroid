package com.arn.aplacetosit.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure presentation math for the log, ported from the iOS core: records
 * grouped under month headings in arrival order with per-month sitting time,
 * and one month laid out for the seven-column calendar grid where a day
 * either holds practice or it doesn't — deliberately nothing else.
 */
data class MonthSection(
    /** Stable identity — "2026-08" — for scroll anchors and the collapsed set. */
    val id: String,
    val title: String,
    val records: List<MeditationRecord>,
    val totalMillis: Long,
    val yearMonth: YearMonth,
)

data class MonthGrid(val leadingBlanks: Int, val days: List<Day>) {
    data class Day(val date: LocalDate, val dayNumber: Int, val practiced: Boolean)
}

object LogPresentation {
    /** Sittings credited under a minute are noise, not practice. */
    const val MINIMUM_VISIBLE_MILLIS = 60_000L

    fun monthSections(records: List<MeditationRecord>, zone: ZoneId): List<MonthSection> {
        val sections = mutableListOf<MonthSection>()
        var currentKey: YearMonth? = null
        var bucket = mutableListOf<MeditationRecord>()
        var total = 0L

        fun flush() {
            val key = currentKey ?: return
            sections.add(
                MonthSection(
                    id = "%04d-%02d".format(key.year, key.monthValue),
                    title = "${key.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${key.year}",
                    records = bucket.toList(),
                    totalMillis = total,
                    yearMonth = key,
                )
            )
        }

        for (record in records) {
            if (record.creditedMillis < MINIMUM_VISIBLE_MILLIS) continue
            val key = YearMonth.from(record.endedAtInstant.atZone(zone))
            if (key != currentKey) {
                flush()
                currentKey = key
                bucket = mutableListOf()
                total = 0
            }
            bucket.add(record)
            total += record.creditedMillis
        }
        flush()
        return sections
    }

    fun monthGrid(
        yearMonth: YearMonth,
        records: List<MeditationRecord>,
        zone: ZoneId,
        firstDayOfWeek: java.time.DayOfWeek,
    ): MonthGrid {
        val practiced: Set<LocalDate> = records
            .filter { it.creditedMillis >= MINIMUM_VISIBLE_MILLIS }
            .map { it.endedAtInstant.atZone(zone).toLocalDate() }
            .toSet()

        val first = yearMonth.atDay(1)
        val leadingBlanks = ((first.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val days = (1..yearMonth.lengthOfMonth()).map { dayNumber ->
            val date = yearMonth.atDay(dayNumber)
            MonthGrid.Day(date, dayNumber, date in practiced)
        }
        return MonthGrid(leadingBlanks, days)
    }
}

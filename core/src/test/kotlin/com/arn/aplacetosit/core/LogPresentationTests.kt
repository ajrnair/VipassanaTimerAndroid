package com.arn.aplacetosit.core

import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ported from the iOS `LogPresentationTests`, same cases, same expectations. */
class LogPresentationTests {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun record(iso: String, minutes: Int = 45, note: String? = null) = MeditationRecord(
        id = java.util.UUID.randomUUID().toString(),
        plannedDurationSeconds = minutes * 60.0,
        creditedDurationSeconds = minutes * 60.0,
        meditationStartedAt = iso,
        endedAt = iso,
        completedAutomatically = true,
        note = note,
    )

    @Test
    fun `sections preserve arrival order and sum their months`() {
        val sections = LogPresentation.monthSections(
            listOf(
                record("2026-08-27T05:15:00Z", 45),
                record("2026-08-25T05:15:00Z", 60),
                record("2026-07-01T05:15:00Z", 30),
            ),
            berlin,
        )
        assertEquals(listOf("2026-08", "2026-07"), sections.map { it.id })
        assertEquals(105 * 60_000L, sections[0].totalMillis)
        assertEquals(30 * 60_000L, sections[1].totalMillis)
    }

    @Test
    fun `sub-minute sittings stay out`() {
        val sections = LogPresentation.monthSections(
            listOf(record("2026-08-27T05:15:00Z", 45), record("2026-08-26T05:15:00Z", 0)),
            berlin,
        )
        assertEquals(1, sections.single().records.size)
    }

    @Test
    fun `december and january land in distinct sections`() {
        val sections = LogPresentation.monthSections(
            listOf(record("2026-01-02T05:15:00Z"), record("2025-12-30T05:15:00Z")),
            berlin,
        )
        assertEquals(listOf("2026-01", "2025-12"), sections.map { it.id })
    }

    @Test
    fun `grid leading blanks honor the first weekday`() {
        // August 2026 begins on a Saturday: Monday-first 5 blanks, Sunday-first 6.
        val august = YearMonth.of(2026, 8)
        assertEquals(5, LogPresentation.monthGrid(august, emptyList(), berlin, DayOfWeek.MONDAY).leadingBlanks)
        assertEquals(6, LogPresentation.monthGrid(august, emptyList(), berlin, DayOfWeek.SUNDAY).leadingBlanks)
        assertEquals(31, LogPresentation.monthGrid(august, emptyList(), berlin, DayOfWeek.MONDAY).days.size)
    }

    @Test
    fun `practiced days line up across the DST change`() {
        // 29 March 2026 is the spring transition in Europe/Berlin.
        val records = listOf(record("2026-03-29T04:00:00Z"), record("2026-03-15T06:00:00Z"))
        val grid = LogPresentation.monthGrid(YearMonth.of(2026, 3), records, berlin, DayOfWeek.MONDAY)
        assertEquals(listOf(15, 29), grid.days.filter { it.practiced }.map { it.dayNumber })
    }
}

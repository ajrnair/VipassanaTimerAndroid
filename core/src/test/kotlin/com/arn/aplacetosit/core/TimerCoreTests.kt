package com.arn.aplacetosit.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ports of the iOS core tests that define the engine's behavior. Where the two
 * platforms must agree — timelines, the random-gap rule, the record schema —
 * the expected values here are copied from the Swift suite, not re-derived.
 */
class TimerCoreTests {
    private fun clock(wall: Long = 1_700_000_000_000, elapsed: Long = 100_000) =
        SessionClock(wall, elapsed)

    @Test
    fun `a silent sitting has no mid-sit event at any length`() {
        for (minutes in listOf(15, 30, 45, 60, 120, 240)) {
            val session = TimerEngine.startStandard(minutes, clock())
            val events = TimerEngine.timelineEvents(session).map { it.event }
            assertEquals(listOf(TimerEvent.MeditationStarted, TimerEvent.Completed), events, "$minutes minutes")
        }
    }

    @Test
    fun `awareness final boundary matches the iOS suite`() {
        // 8 h / 10 min: 47 intermediate gongs, completed exactly at the end.
        val session = TimerEngine.startAwarenessFixed(8, 10, clock())
        val events = TimerEngine.timelineEvents(session)
        assertEquals(47, events.count { it.event is TimerEvent.AwarenessInterval })
        assertEquals(TimerEvent.Completed, events.last().event)
        assertEquals(8 * 3_600_000L, events.last().timelineOffsetMillis)
    }

    @Test
    fun `random bounds follow the session length`() {
        assertEquals(5 * 60_000L to 10 * 60_000L, AwarenessScheduler.randomBounds(3_600_000))
        assertEquals(10 * 60_000L to 20 * 60_000L, AwarenessScheduler.randomBounds(4 * 3_600_000))
        assertEquals(20 * 60_000L to 40 * 60_000L, AwarenessScheduler.randomBounds(8 * 3_600_000))
        assertEquals(20 * 60_000L to 40 * 60_000L, AwarenessScheduler.randomBounds(24 * 3_600_000))
    }

    @Test
    fun `random offsets are increasing, inside bounds, and end before the session`() {
        val total = 8 * 3_600_000L
        val offsets = AwarenessScheduler.gongOffsets(total, Random(42))
        val (minimum, maximum) = AwarenessScheduler.randomBounds(total)
        assertTrue(offsets.isNotEmpty())
        assertEquals(offsets.sorted(), offsets)
        assertTrue(offsets.all { it < total })
        var previous = 0L
        for (offset in offsets) {
            val gap = offset - previous
            assertTrue(gap in minimum..maximum, "gap $gap outside [$minimum, $maximum]")
            previous = offset
        }
    }

    @Test
    fun `the same seed draws the same schedule`() {
        val a = AwarenessScheduler.gongOffsets(6 * 3_600_000, Random(7))
        val b = AwarenessScheduler.gongOffsets(6 * 3_600_000, Random(7))
        assertEquals(a, b)
    }

    @Test
    fun `a persisted random session replays its exact timeline`() {
        val session = TimerEngine.startAwarenessRandom(8, clock(), Random(11))
        val json = MeditationRecord.json.encodeToString(ActiveSession.serializer(), session)
        val decoded = MeditationRecord.json.decodeFromString(ActiveSession.serializer(), json)
        assertEquals(TimerEngine.timelineEvents(session), TimerEngine.timelineEvents(decoded))
    }

    @Test
    fun `elapsed prefers the monotonic clock and falls back across reboot`() {
        val session = TimerEngine.startStandard(60, clock(wall = 1_000_000, elapsed = 500_000))
        // Monotonic moved forward: monotonic wins even if the wall clock jumped.
        assertEquals(120_000, TimerEngine.elapsed(session, SessionClock(999_999_999, 620_000)))
        // Monotonic went backwards: a reboot happened; the wall clock is the truth.
        assertEquals(300_000, TimerEngine.elapsed(session, SessionClock(1_300_000, 10_000)))
    }

    @Test
    fun `credited duration is clamped and preparation earns nothing`() {
        val session = TimerEngine.startStandard(60, clock(wall = 0, elapsed = 0))
        assertEquals(0, TimerEngine.creditedDuration(session, SessionClock(4_000, 4_000)))
        assertEquals(60_000, TimerEngine.creditedDuration(session, SessionClock(68_000, 68_000)))
        assertEquals(3_600_000, TimerEngine.creditedDuration(session, SessionClock(99_999_999, 99_999_999)))
    }

    @Test
    fun `the record schema round-trips the iOS shape`() {
        val iosJson = """
            {"id":"11111111-2222-3333-4444-555555555555",
             "plannedDuration":2700.0,"creditedDuration":2700.0,
             "meditationStartedAt":"2026-08-27T01:15:00Z",
             "endedAt":"2026-08-27T02:00:00Z",
             "completedAutomatically":true,
             "note":"Strong sensations today."}
        """.trimIndent()
        val record = MeditationRecord.json.decodeFromString(MeditationRecord.serializer(), iosJson)
        assertEquals(2_700_000, record.creditedMillis)
        assertEquals("Strong sensations today.", record.note)
        val reencoded = MeditationRecord.json.encodeToString(MeditationRecord.serializer(), record)
        val back = MeditationRecord.json.decodeFromString(MeditationRecord.serializer(), reencoded)
        assertEquals(record, back)
    }

    @Test
    fun `formatter speaks the log's voice`() {
        assertEquals("45m", DurationFormatter.concise(45 * 60_000))
        assertEquals("1h", DurationFormatter.concise(60 * 60_000))
        assertEquals("1h 30m", DurationFormatter.concise(90 * 60_000))
        assertEquals("27:14", DurationFormatter.countdown((27 * 60 + 14) * 1000L))
        assertEquals("6:12:48", DurationFormatter.countdown(((6 * 3600) + (12 * 60) + 48) * 1000L))
    }
}

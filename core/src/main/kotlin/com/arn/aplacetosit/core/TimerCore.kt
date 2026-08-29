package com.arn.aplacetosit.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The timer core, ported from the iOS app's `VipassanaTimer/Core` and held to
 * the same tests. Everything here is pure: time comes in through
 * [SessionClock], randomness through a caller-supplied [Random], and the
 * schedule of a session is a function of its persisted state — the property
 * that makes recovery after process death reproduce the identical gongs.
 *
 * Times are in milliseconds throughout (the platform's native unit), where the
 * iOS core uses seconds.
 */

enum class SessionMode { STANDARD, AWARENESS }

enum class SessionPhase { PREPARING, MEDITATING, AWARENESS, COMPLETED }

/**
 * A reading of both clocks. `elapsedRealtimeMillis` is Android's monotonic
 * clock that keeps counting through deep sleep — the one luxury iOS never
 * offered — so elapsed time needs no boot-identity dance here: a session
 * anchored to it survives locking, Doze, and wall-clock changes alike. The
 * wall clock is the fallback for a session restored after a reboot, exactly
 * as on iOS.
 */
data class SessionClock(val wallMillis: Long, val elapsedRealtimeMillis: Long)

@Serializable
data class ActiveSession(
    val id: String,
    val mode: SessionMode,
    val createdAtWallMillis: Long,
    val anchorElapsedRealtimeMillis: Long,
    val plannedDurationMillis: Long,
    val preparationDurationMillis: Long,
    val intervalMillis: Long? = null,
    /** The materialized random gong schedule; present only for random Awareness. */
    val gongOffsetsMillis: List<Long>? = null,
    val guidedMinutes: Int? = null,
)

sealed class TimerEvent(val identifier: String) {
    data object MeditationStarted : TimerEvent("meditation-started")
    data class AwarenessInterval(val index: Int) : TimerEvent("awareness-interval-$index")
    data object Completed : TimerEvent("completed")
}

data class TimedEvent(val event: TimerEvent, val timelineOffsetMillis: Long)

data class TimerSnapshot(
    val phase: SessionPhase,
    val elapsedTimelineMillis: Long,
    val remainingMillis: Long,
    val progressRemaining: Double,
)

object TimerEngine {
    const val PREPARATION_MILLIS = 8_000L

    fun startStandard(minutes: Int, clock: SessionClock, guidedMinutes: Int? = null): ActiveSession =
        ActiveSession(
            id = randomId(),
            mode = SessionMode.STANDARD,
            createdAtWallMillis = clock.wallMillis,
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            plannedDurationMillis = minutes * 60_000L,
            preparationDurationMillis = PREPARATION_MILLIS,
            guidedMinutes = guidedMinutes,
        )

    fun startAwarenessFixed(hours: Int, intervalMinutes: Int, clock: SessionClock): ActiveSession =
        ActiveSession(
            id = randomId(),
            mode = SessionMode.AWARENESS,
            createdAtWallMillis = clock.wallMillis,
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            plannedDurationMillis = hours * 3_600_000L,
            preparationDurationMillis = 0,
            intervalMillis = intervalMinutes * 60_000L,
        )

    /**
     * Random Awareness: the whole schedule is drawn here, once, and persisted
     * with the session. `intervalMillis` carries the minimum gap so a reader
     * without the offsets degrades to a sane fixed schedule.
     */
    fun startAwarenessRandom(hours: Int, clock: SessionClock, random: Random): ActiveSession {
        val total = hours * 3_600_000L
        val bounds = AwarenessScheduler.randomBounds(total)
        return ActiveSession(
            id = randomId(),
            mode = SessionMode.AWARENESS,
            createdAtWallMillis = clock.wallMillis,
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            plannedDurationMillis = total,
            preparationDurationMillis = 0,
            intervalMillis = bounds.first,
            gongOffsetsMillis = AwarenessScheduler.gongOffsets(total, random),
        )
    }

    fun elapsed(session: ActiveSession, clock: SessionClock): Long {
        val monotonic = clock.elapsedRealtimeMillis - session.anchorElapsedRealtimeMillis
        // A negative monotonic difference means the device rebooted since the
        // session was anchored; the wall clock is the honest fallback.
        if (monotonic >= 0) return monotonic
        return maxOf(0, clock.wallMillis - session.createdAtWallMillis)
    }

    fun snapshot(session: ActiveSession, clock: SessionClock): TimerSnapshot {
        val elapsedTimeline = elapsed(session, clock)
        return when (session.mode) {
            SessionMode.STANDARD -> {
                if (elapsedTimeline < session.preparationDurationMillis) {
                    val remaining = session.preparationDurationMillis - elapsedTimeline
                    TimerSnapshot(
                        SessionPhase.PREPARING, elapsedTimeline, remaining,
                        (remaining.toDouble() / session.preparationDurationMillis).coerceIn(0.0, 1.0),
                    )
                } else {
                    val meditationElapsed = elapsedTimeline - session.preparationDurationMillis
                    val remaining = maxOf(0, session.plannedDurationMillis - meditationElapsed)
                    TimerSnapshot(
                        if (remaining > 0) SessionPhase.MEDITATING else SessionPhase.COMPLETED,
                        elapsedTimeline, remaining,
                        if (session.plannedDurationMillis > 0)
                            remaining.toDouble() / session.plannedDurationMillis else 0.0,
                    )
                }
            }
            SessionMode.AWARENESS -> {
                val remaining = maxOf(0, session.plannedDurationMillis - elapsedTimeline)
                TimerSnapshot(
                    if (remaining > 0) SessionPhase.AWARENESS else SessionPhase.COMPLETED,
                    elapsedTimeline, remaining,
                    if (session.plannedDurationMillis > 0)
                        remaining.toDouble() / session.plannedDurationMillis else 0.0,
                )
            }
        }
    }

    /**
     * The cue plan, a pure function of the session. A silent sitting is bare:
     * one start marker, one completion — nothing in between, exactly the
     * promise the copy makes.
     */
    fun timelineEvents(session: ActiveSession): List<TimedEvent> = when (session.mode) {
        SessionMode.STANDARD -> listOf(
            TimedEvent(TimerEvent.MeditationStarted, session.preparationDurationMillis),
            TimedEvent(
                TimerEvent.Completed,
                session.preparationDurationMillis + session.plannedDurationMillis,
            ),
        )
        SessionMode.AWARENESS -> buildList {
            val offsets = session.gongOffsetsMillis
            if (offsets != null) {
                offsets.forEachIndexed { index, offset ->
                    if (offset < session.plannedDurationMillis) {
                        add(TimedEvent(TimerEvent.AwarenessInterval(index + 1), offset))
                    }
                }
            } else {
                val interval = session.intervalMillis
                if (interval != null && interval > 0) {
                    var index = 1
                    var offset = interval
                    while (offset < session.plannedDurationMillis) {
                        add(TimedEvent(TimerEvent.AwarenessInterval(index), offset))
                        index += 1
                        offset = interval * index
                    }
                }
            }
            add(TimedEvent(TimerEvent.Completed, session.plannedDurationMillis))
        }
    }

    fun eventsCrossed(session: ActiveSession, fromMillis: Long, throughMillis: Long): List<TimerEvent> {
        if (throughMillis < fromMillis) return emptyList()
        return timelineEvents(session)
            .filter { it.timelineOffsetMillis > fromMillis && it.timelineOffsetMillis <= throughMillis }
            .map { it.event }
    }

    fun creditedDuration(session: ActiveSession, clock: SessionClock): Long {
        if (session.mode != SessionMode.STANDARD) return 0
        val meditationElapsed = elapsed(session, clock) - session.preparationDurationMillis
        return meditationElapsed.coerceIn(0, session.plannedDurationMillis)
    }

    private fun randomId(): String = java.util.UUID.randomUUID().toString().uppercase()
}

/** Same rule as iOS: gaps in [total/24, total/12], clamped so no gap falls under 5 minutes or past 40. */
object AwarenessScheduler {
    const val MINIMUM_GAP_MILLIS = 5 * 60_000L
    const val MAXIMUM_GAP_MILLIS = 40 * 60_000L

    fun randomBounds(totalMillis: Long): Pair<Long, Long> {
        val minimum = (totalMillis / 24).coerceIn(MINIMUM_GAP_MILLIS, MAXIMUM_GAP_MILLIS / 2)
        val maximum = (totalMillis / 12).coerceIn(MINIMUM_GAP_MILLIS * 2, MAXIMUM_GAP_MILLIS)
        return minimum to maxOf(minimum, maximum)
    }

    fun gongOffsets(totalMillis: Long, random: Random): List<Long> {
        if (totalMillis <= 0) return emptyList()
        val (minimum, maximum) = randomBounds(totalMillis)
        val offsets = mutableListOf<Long>()
        var offset = 0L
        while (true) {
            // Whole-second gaps, as on iOS, so schedules stay human-legible.
            val gapSeconds = random.nextLong(minimum / 1000, maximum / 1000 + 1)
            offset += gapSeconds * 1000
            if (offset >= totalMillis) break
            offsets.add(offset)
        }
        return offsets
    }
}

object AwarenessPolicy {
    const val DEFAULT_HOURS = 8
    const val DEFAULT_INTERVAL_MINUTES = 10
    const val MIN_HOURS = 1
    const val MAX_HOURS = 24
    const val MIN_INTERVAL_MINUTES = 1
    const val MAX_INTERVAL_MINUTES = 1_440
}

object DurationFormatter {
    /** "45m", "1h", "1h 30m" — the log's concise voice. */
    fun concise(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    /** "27:14" or "6:12:48" — the running countdown. */
    fun countdown(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}

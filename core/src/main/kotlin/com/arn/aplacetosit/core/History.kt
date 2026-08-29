package com.arn.aplacetosit.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * One record per sitting, one JSON file per record — the same schema, field
 * names, and ISO-8601 dates as the iOS app's `MeditationRecord`, so a record
 * written on either platform reads on the other. Durations serialize in
 * seconds (the iOS unit); this type holds milliseconds internally.
 */
@Serializable
data class MeditationRecord(
    val id: String,
    @SerialName("plannedDuration") val plannedDurationSeconds: Double,
    @SerialName("creditedDuration") val creditedDurationSeconds: Double,
    val meditationStartedAt: String,
    val endedAt: String,
    val completedAutomatically: Boolean,
    val note: String? = null,
    val modifiedAt: String? = null,
) {
    val creditedMillis: Long get() = (creditedDurationSeconds * 1000).toLong()
    val endedAtInstant: Instant get() = Instant.parse(endedAt)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }

        fun forSession(
            session: ActiveSession,
            creditedMillis: Long,
            endedAtWallMillis: Long,
            completedAutomatically: Boolean,
        ): MeditationRecord {
            val started = session.createdAtWallMillis + session.preparationDurationMillis
            return MeditationRecord(
                id = session.id,
                plannedDurationSeconds = session.plannedDurationMillis / 1000.0,
                creditedDurationSeconds = creditedMillis / 1000.0,
                meditationStartedAt = iso(started),
                endedAt = iso(endedAtWallMillis),
                completedAutomatically = completedAutomatically,
            )
        }

        fun iso(wallMillis: Long): String =
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(wallMillis).with(java.time.temporal.ChronoField.MILLI_OF_SECOND, 0))
    }
}

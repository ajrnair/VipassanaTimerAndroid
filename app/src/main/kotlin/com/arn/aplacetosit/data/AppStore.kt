package com.arn.aplacetosit.data

import android.content.Context
import com.arn.aplacetosit.core.ActiveSession
import com.arn.aplacetosit.core.MeditationRecord
import java.io.File

/**
 * One JSON file per sitting in `files/history/`, the iOS schema exactly, plus
 * one `session-state.json` for the active session so a killed process can
 * restore a running sitting and its identical gong schedule.
 */
class AppStore(context: Context) {
    private val historyDir = File(context.filesDir, "history").apply { mkdirs() }
    private val stateFile = File(context.filesDir, "session-state.json")
    private val json = MeditationRecord.json

    fun loadRecords(): List<MeditationRecord> =
        historyDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString(MeditationRecord.serializer(), file.readText())
                }.getOrNull()
            }
            ?.sortedByDescending { it.endedAt }
            ?: emptyList()

    fun save(record: MeditationRecord) {
        val tmp = File(historyDir, "${record.id}.json.tmp")
        tmp.writeText(json.encodeToString(MeditationRecord.serializer(), record))
        tmp.renameTo(File(historyDir, "${record.id}.json"))
    }

    fun delete(id: String) {
        File(historyDir, "$id.json").delete()
    }

    fun persistActive(session: ActiveSession?) {
        if (session == null) {
            stateFile.delete()
        } else {
            stateFile.writeText(json.encodeToString(ActiveSession.serializer(), session))
        }
    }

    fun restoreActive(): ActiveSession? =
        if (stateFile.exists()) {
            runCatching {
                json.decodeFromString(ActiveSession.serializer(), stateFile.readText())
            }.getOrNull()
        } else null
}

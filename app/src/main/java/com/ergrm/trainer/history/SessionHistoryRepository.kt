package com.ergrm.trainer.history

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

/**
 * Local history of completed workout sessions, persisted as a single JSON file in the app's
 * private storage (no server, no account needed — the same trade-off as the rest of the app's
 * settings/library persistence).
 */
class SessionHistoryRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "session_history.json")

    suspend fun listSessions(): List<WorkoutSession> = withContext(Dispatchers.IO) {
        readAll().sortedByDescending { it.startEpochMillis }
    }

    suspend fun saveSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        writeAll(readAll() + session)
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        writeAll(readAll().filterNot { it.id == id })
    }

    private fun readAll(): List<WorkoutSession> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<WorkoutSession>>(file.readText())
        } catch (t: Exception) {
            emptyList()
        }
    }

    private fun writeAll(sessions: List<WorkoutSession>) {
        file.writeText(json.encodeToString(sessions))
    }
}

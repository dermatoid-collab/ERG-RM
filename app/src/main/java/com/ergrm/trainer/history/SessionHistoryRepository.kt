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

    /** Exposed so the UI can share this exact file (via FileProvider) as an export — one file,
     *  always current, no separate export step needed to keep a copy in sync. */
    val exportFile: File get() = file

    suspend fun listSessions(): List<WorkoutSession> = withContext(Dispatchers.IO) {
        readAll().sortedByDescending { it.startEpochMillis }
    }

    suspend fun saveSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        writeAll(readAll() + session)
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        writeAll(readAll().filterNot { it.id == id })
    }

    /** Merges sessions from a previously exported file into the existing history, skipping any
     *  id already present — safe to import the same backup twice without duplicating rows.
     *  Returns the number of sessions actually added. */
    suspend fun importFromJson(text: String): Result<Int> = withContext(Dispatchers.IO) {
        val imported = try {
            json.decodeFromString<List<WorkoutSession>>(text)
        } catch (t: Exception) {
            return@withContext Result.failure(t)
        }
        val existingIds = readAll().map { it.id }.toSet()
        val newOnes = imported.filterNot { it.id in existingIds }
        if (newOnes.isNotEmpty()) {
            writeAll(readAll() + newOnes)
        }
        Result.success(newOnes.size)
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

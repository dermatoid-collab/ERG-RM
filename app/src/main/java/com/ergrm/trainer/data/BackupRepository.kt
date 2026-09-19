package com.ergrm.trainer.data

import android.content.Context
import com.ergrm.trainer.history.SessionHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

/** Combines [SettingsRepository] and [SessionHistoryRepository] into one backup file — the
 *  safety net for a device where reinstalling the app (e.g. to work around a sideload/update
 *  issue) wipes both settings and history at once. */
class BackupRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: SessionHistoryRepository,
) {
    private val file: File get() = File(context.filesDir, "erg_rm_backup.json")

    /** Writes the current settings + history to the backup file and returns it, ready to share
     *  via FileProvider. */
    suspend fun exportFile(): File = withContext(Dispatchers.IO) {
        val backup = AppBackup(
            settings = settingsRepository.settings.first(),
            history = historyRepository.listSessions(),
        )
        file.writeText(json.encodeToString(backup))
        file
    }

    /** [BackupImportResult.sessionsAdded] is how many history rows were actually new — the
     *  backup's own session count can include ones already present, deduped by id. */
    suspend fun importFromJson(text: String): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        val backup = try {
            json.decodeFromString<AppBackup>(text)
        } catch (t: Exception) {
            return@withContext Result.failure(t)
        }
        settingsRepository.applyBackup(backup.settings)
        val added = historyRepository.importSessions(backup.history)
        Result.success(BackupImportResult(backup, added))
    }
}

data class BackupImportResult(val backup: AppBackup, val sessionsAdded: Int)

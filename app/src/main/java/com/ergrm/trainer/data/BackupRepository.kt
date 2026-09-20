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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val json = Json { ignoreUnknownKeys = true }

// A gzip stream always starts with this two-byte magic number — cheap way to tell a compressed
// backup apart from a plain-JSON one exported by an older build, so importing either still works.
private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())

/** Combines [SettingsRepository] and [SessionHistoryRepository] into one backup file — the
 *  safety net for a device where reinstalling the app (e.g. to work around a sideload/update
 *  issue) wipes both settings and history at once. Gzipped: the full sample-by-sample history
 *  compresses roughly 9x with no data loss, which matters once a rider has months of rides saved. */
class BackupRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: SessionHistoryRepository,
) {
    /** Writes the current settings + history to a freshly timestamped backup file and returns
     *  it, ready to share via FileProvider — a new file per export (rather than one fixed name)
     *  so re-exporting to the same Drive folder doesn't collide with or silently replace the
     *  previous backup. */
    suspend fun exportFile(): File = withContext(Dispatchers.IO) {
        val backup = AppBackup(
            settings = settingsRepository.settings.first(),
            history = historyRepository.listSessions(),
        )
        val stamp = SimpleDateFormat("ddMMyy_HHmm", Locale.US).format(Date())
        val file = File(context.filesDir, "erg_rm_backup_$stamp.json.gz")
        GZIPOutputStream(file.outputStream()).use { it.write(json.encodeToString(backup).toByteArray()) }
        file
    }

    /** Same as [importFromJson], but for a file read as raw bytes — detects and transparently
     *  decompresses a gzipped backup, and falls back to treating the bytes as plain JSON text for
     *  a backup exported before compression was added. */
    suspend fun importFromBytes(bytes: ByteArray): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        val text = try {
            if (bytes.size >= 2 && bytes[0] == GZIP_MAGIC[0] && bytes[1] == GZIP_MAGIC[1]) {
                GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        } catch (t: Exception) {
            return@withContext Result.failure(t)
        }
        importFromJson(text)
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

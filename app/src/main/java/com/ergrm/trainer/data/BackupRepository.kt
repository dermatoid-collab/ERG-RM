package com.ergrm.trainer.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ergrm.trainer.history.SessionHistoryRepository
import com.ergrm.trainer.history.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
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

private const val SESSION_FILE_PREFIX = "erg_rm_session_"
private const val SESSION_FILE_SUFFIX = ".json.gz"

/** Two independent backups, deliberately not combined into one file:
 *  - Settings ([exportSettingsFile]/[importFromBytes]): small, manually triggered, one file
 *    replaced on every export — API key, Athlete ID, FTP/LTHR, remembered trainer/HR sensor,
 *    library folder.
 *  - Workout history ([writeSessionToFolder]/[syncSessionsFromFolder]): one file per session,
 *    written automatically after every save, named by that session's own id so it's never
 *    ambiguous which file is which and a write never has to find-and-overwrite an existing one. */
class BackupRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: SessionHistoryRepository,
) {
    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    private fun sessionFileName(session: WorkoutSession) = "$SESSION_FILE_PREFIX${session.id}$SESSION_FILE_SUFFIX"

    /** Writes the current settings to a freshly timestamped file and returns it, ready to share
     *  via FileProvider — a new file per export (rather than one fixed name) so re-exporting to
     *  the same Drive folder doesn't collide with or silently replace the previous one. */
    suspend fun exportSettingsFile(): File = withContext(Dispatchers.IO) {
        val backup = SettingsBackup(settings = settingsRepository.settings.first())
        val stamp = SimpleDateFormat("ddMMyy_HHmm", Locale.US).format(Date())
        val file = File(context.filesDir, "erg_rm_settings_backup_$stamp.json.gz")
        file.writeBytes(gzip(json.encodeToString(backup)))
        file
    }

    /** Writes [session] as its own small gzipped file straight into a SAF folder the user picked
     *  once (e.g. a Drive-synced folder) — the whole point being that it survives an uninstall,
     *  unlike the app's own internal storage. Called automatically after every saved workout.
     *  Each session's id makes its filename unique forever, so this is always a fresh create,
     *  never a find-and-overwrite — unlike a single shared backup file, there's no ambiguity to
     *  race against if the folder's own index hasn't caught up yet. Returns false (rather than
     *  throwing) if the folder is gone or permission was revoked, so the caller can warn the
     *  rider instead of crashing on a routine save. */
    suspend fun writeSessionToFolder(folderUri: Uri, session: WorkoutSession): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext false
            if (!folder.exists() || !folder.isDirectory) return@withContext false
            val target = folder.createFile("application/gzip", sessionFileName(session)) ?: return@withContext false
            val bytes = gzip(json.encodeToString(session))
            context.contentResolver.openOutputStream(target.uri)?.use { it.write(bytes) } ?: return@withContext false
            true
        } catch (t: Exception) {
            false
        }
    }

    /** Pulls in any session backed up to [folderUri] that isn't already in local history —
     *  e.g. after a reinstall, or one written from a different device sharing the same folder.
     *  Cheap in the common case: session ids are read straight out of each file's *name*, so
     *  only files not already recognized locally are actually opened, decompressed and parsed.
     *  Returns how many were newly added. */
    suspend fun syncSessionsFromFolder(folderUri: Uri): Int = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext 0
        if (!folder.exists() || !folder.isDirectory) return@withContext 0
        val existingIds = historyRepository.listSessions().map { it.id }.toSet()
        val newSessions = folder.listFiles().mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            if (!name.startsWith(SESSION_FILE_PREFIX) || !name.endsWith(SESSION_FILE_SUFFIX)) return@mapNotNull null
            val id = name.removePrefix(SESSION_FILE_PREFIX).removeSuffix(SESSION_FILE_SUFFIX)
            if (id in existingIds) return@mapNotNull null
            try {
                val bytes = context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() } ?: return@mapNotNull null
                val text = GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
                json.decodeFromString<WorkoutSession>(text)
            } catch (t: Exception) {
                null
            }
        }
        if (newSessions.isEmpty()) return@withContext 0
        historyRepository.importSessions(newSessions)
    }

    /** Same as [importSettingsFromJson], but for a file read as raw bytes — detects and
     *  transparently decompresses a gzipped backup, and falls back to treating the bytes as
     *  plain JSON text for a backup exported before compression was added. */
    suspend fun importFromBytes(bytes: ByteArray): Result<SettingsBackup> = withContext(Dispatchers.IO) {
        val text = try {
            if (bytes.size >= 2 && bytes[0] == GZIP_MAGIC[0] && bytes[1] == GZIP_MAGIC[1]) {
                GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        } catch (t: Exception) {
            return@withContext Result.failure(t)
        }
        importSettingsFromJson(text)
    }

    suspend fun importSettingsFromJson(text: String): Result<SettingsBackup> = withContext(Dispatchers.IO) {
        val backup = try {
            json.decodeFromString<SettingsBackup>(text)
        } catch (t: Exception) {
            return@withContext Result.failure(t)
        }
        settingsRepository.applyBackup(backup.settings)
        Result.success(backup)
    }
}

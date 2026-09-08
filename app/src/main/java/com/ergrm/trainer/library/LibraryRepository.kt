package com.ergrm.trainer.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ergrm.trainer.workout.ErgParser
import com.ergrm.trainer.workout.WorkoutStep
import com.ergrm.trainer.workout.ZwoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SUPPORTED_EXTENSIONS = listOf(".zwo", ".erg", ".mrc")

/** A single workout file (.zwo, .erg or .mrc) found in the user's chosen library folder (e.g. a synced Google Drive folder). */
data class LibraryWorkoutFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

sealed interface LibraryImportResult {
    data class Success(val steps: List<WorkoutStep>) : LibraryImportResult
    data class Error(val message: String) : LibraryImportResult
}

/**
 * Reads workout files from a folder the user picked via the Storage Access Framework
 * (Intent.ACTION_OPEN_DOCUMENT_TREE). This works transparently for a Google Drive folder
 * as long as the Google Drive app is installed — it exposes itself as a DocumentsProvider,
 * so no separate Drive API / OAuth setup is needed for on-demand import.
 */
class LibraryRepository(private val context: Context) {

    suspend fun listWorkouts(folderUri: Uri): List<LibraryWorkoutFile> = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        folder.listFiles()
            .filter { file -> file.isFile && SUPPORTED_EXTENSIONS.any { file.name?.endsWith(it, ignoreCase = true) == true } }
            .map { LibraryWorkoutFile(it.uri, it.name ?: "workout.zwo", it.length(), it.lastModified()) }
            .sortedByDescending { it.lastModifiedMillis }
    }

    suspend fun importWorkout(fileUri: Uri, fileName: String, ftpWatts: Int): LibraryImportResult = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(fileUri)?.use { it.reader().readText() }
                ?: return@withContext LibraryImportResult.Error("Impossibile leggere il file")
            val steps = if (fileName.endsWith(".erg", ignoreCase = true) || fileName.endsWith(".mrc", ignoreCase = true)) {
                ErgParser.parse(content, ftpWatts)
            } else {
                ZwoParser.parse(content, ftpWatts)
            }
            if (steps.isEmpty()) {
                LibraryImportResult.Error("Nessuno step trovato nel file")
            } else {
                LibraryImportResult.Success(steps)
            }
        } catch (t: Exception) {
            LibraryImportResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }
}

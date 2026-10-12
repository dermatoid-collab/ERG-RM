package com.ergrm.trainer.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ergrm.trainer.workout.GpxParser
import com.ergrm.trainer.workout.RouteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single .gpx file found in the user's chosen GPX routes folder. */
data class GpxRouteFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

sealed interface GpxImportResult {
    data class Success(val route: RouteProfile) : GpxImportResult
    data class Error(val message: String) : GpxImportResult
}

/** Same SAF-folder pattern as [LibraryRepository], for .gpx route files instead of workout
 *  plans — a separate folder since a rider's GPX tracks and ERG/ZWO workout plans are naturally
 *  kept apart (e.g. two different synced Drive folders). */
class GpxLibraryRepository(private val context: Context) {

    suspend fun listRoutes(folderUri: Uri): List<GpxRouteFile> = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        folder.listFiles()
            .filter { file -> file.isFile && file.name?.endsWith(".gpx", ignoreCase = true) == true }
            .map { GpxRouteFile(it.uri, it.name ?: "route.gpx", it.length(), it.lastModified()) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun importRoute(fileUri: Uri, fileName: String): GpxImportResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: return@withContext GpxImportResult.Error("Couldn't read the file")
            val name = fileName.removeSuffix(".gpx").removeSuffix(".GPX")
            val route = GpxParser.parse(bytes.decodeToString(), name)
                ?: return@withContext GpxImportResult.Error("That doesn't look like a valid GPX track")
            GpxImportResult.Success(route)
        } catch (t: Exception) {
            GpxImportResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }
}

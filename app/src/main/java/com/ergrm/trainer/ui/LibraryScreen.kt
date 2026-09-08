package com.ergrm.trainer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.library.LibraryWorkoutFile
import com.ergrm.trainer.ui.theme.ErgOnSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onImported: () -> Unit = {},
    onPickToday: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast(':') ?: "Cartella"
            viewModel.onLibraryFolderPicked(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Libreria workout", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPickToday) {
                    Text("Oggi (Intervals.icu)")
                }
                IconButton(onClick = { viewModel.refreshLibrary() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sincronizza")
                }
            }
        }

        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = ErgOnSurface)
                    Text(
                        text = settings.libraryFolderName ?: "Nessuna cartella selezionata",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    "Scegli una cartella (es. sincronizzata con Google Drive tramite l'app Drive) " +
                        "contenente file .zwo. L'import avviene su richiesta, non in background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (settings.libraryFolderUri == null) "Scegli cartella" else "Cambia cartella")
                }
            }
        }

        when (val state = libraryState) {
            LibraryUiState.NoFolder -> {
                // handled by the card above; nothing else to show
            }
            LibraryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
            is LibraryUiState.Error -> {
                Text(
                    "Errore: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is LibraryUiState.Loaded -> {
                if (state.files.isEmpty()) {
                    Text(
                        "Nessun file .zwo trovato in questa cartella.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErgOnSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.files, key = { it.uri.toString() }) { file ->
                            LibraryFileRow(file) {
                                viewModel.importLibraryWorkout(file)
                                onImported()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFileRow(file: LibraryWorkoutFile, onImport: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(file.name.removeSuffix(".zwo"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatMeta(file),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                )
            }
            Button(onClick = onImport) { Text("Importa") }
        }
    }
}

private fun formatMeta(file: LibraryWorkoutFile): String {
    val date = Instant.ofEpochMilli(file.lastModifiedMillis).atZone(ZoneId.systemDefault()).format(dateFormatter)
    val sizeKb = file.sizeBytes / 1024f
    return "%.1f KB · %s".format(sizeKb, date)
}

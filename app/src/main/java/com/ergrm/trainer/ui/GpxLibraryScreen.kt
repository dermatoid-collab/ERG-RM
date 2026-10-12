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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ergrm.trainer.library.GpxRouteFile
import com.ergrm.trainer.ui.theme.ErgOnSurface

/** Same persistent-SAF-folder pattern as [LibraryScreen], for .gpx route files — a separate
 *  folder since routes and workout plans are naturally organized apart. Reached from "Other
 *  sources" only once a GPX folder is set in Settings; otherwise that menu entry falls back to
 *  a one-off file picker instead of landing here. */
@Composable
fun GpxLibraryScreen(
    viewModel: MainViewModel,
    onPicked: () -> Unit = {},
    otherSources: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val settings by viewModel.settings.collectAsState()
    val gpxLibraryState by viewModel.gpxLibraryState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Folder"
            viewModel.onGpxFolderPicked(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        SearchableHeaderRow(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            active = searchActive,
            onActiveChange = { searchActive = it },
            onRefresh = { viewModel.refreshGpxLibrary() },
            otherSources = otherSources,
        )
        Text("GPX Routes", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Map, contentDescription = null, tint = ErgOnSurface)
                    Text(
                        text = settings.gpxFolderName ?: "No folder selected",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        AppNavigationEvents.pickerLaunchInFlight = true
                        folderPicker.launch(null)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Text(if (settings.gpxFolderUri == null) "Choose folder" else "Change folder")
                }
            }
        }

        when (val state = gpxLibraryState) {
            GpxLibraryUiState.NoFolder -> {
                // handled by the card above; nothing else to show
            }
            GpxLibraryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
            is GpxLibraryUiState.Error -> {
                Text(
                    "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is GpxLibraryUiState.Loaded -> {
                val visibleFiles = remember(state.files, searchQuery) {
                    if (searchQuery.isBlank()) {
                        state.files
                    } else {
                        state.files.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                }
                if (state.files.isEmpty()) {
                    Text(
                        "No .gpx file found in this folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErgOnSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else if (visibleFiles.isEmpty()) {
                    Text(
                        "No results for \"$searchQuery\".",
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
                        items(visibleFiles, key = { it.uri.toString() }) { file ->
                            GpxFileRow(
                                file = file,
                                onImport = {
                                    viewModel.importGpxFromLibrary(file)
                                    onPicked()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GpxFileRow(file: GpxRouteFile, onImport: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(1f) + maxLines/ellipsis: see LibraryFileRow's identical note — an unweighted
            // wrapping Text can squeeze the Button beside it down to nothing on a long filename.
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    file.name.substringBeforeLast(".", file.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatFileSizeKb(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Button(onClick = onImport) { Text("Import") }
        }
    }
}

private fun formatFileSizeKb(bytes: Long): String = "%.0f KB".format(bytes / 1024f)

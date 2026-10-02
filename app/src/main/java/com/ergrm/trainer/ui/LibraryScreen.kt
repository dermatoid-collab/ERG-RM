package com.ergrm.trainer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ergrm.trainer.library.LibraryWorkoutFile
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.workout.WorkoutStep

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onImported: () -> Unit = {},
    otherSources: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val settings by viewModel.settings.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // The tree URI's own last path segment is the provider's internal document ID (for
            // Google Drive, an opaque encoded token, not a human name) — DocumentFile queries the
            // provider for its real display name instead. Only the folder's own name, though: SAF
            // doesn't expose the chain of parent folder names, so this can't show a full path.
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Folder"
            viewModel.onLibraryFolderPicked(uri, name)
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
            onRefresh = { viewModel.refreshLibrary() },
            otherSources = otherSources,
        )
        Text("Workout Library", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = ErgOnSurface)
                    Text(
                        text = settings.libraryFolderName ?: "No folder selected",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Text(if (settings.libraryFolderUri == null) "Choose folder" else "Change folder")
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
                    "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is LibraryUiState.Loaded -> {
                val visibleFiles = remember(state.files, searchQuery) {
                    if (searchQuery.isBlank()) {
                        state.files
                    } else {
                        state.files.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                }
                if (state.files.isEmpty()) {
                    Text(
                        "No .zwo, .erg or .mrc file found in this folder.",
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
                            LibraryFileRow(
                                file = file,
                                ftpWatts = settings.ftpWatts,
                                fetchPreview = viewModel::previewLocalWorkout,
                                onImport = {
                                    viewModel.importLibraryWorkout(file)
                                    onImported()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Same dropdown, reused identically on every workout-source screen (Workout's own header,
 *  Library, Calendar, Intervals.icu Library) so you can jump straight to another source without
 *  backing out first — always the same 4 items in the same order, including the screen you're
 *  already on (tapping it is a harmless no-op), so the menu never looks different depending on
 *  where you opened it from. */
@Composable
internal fun WorkoutSourceMenu(items: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Other sources")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

/** Same search affordance reused on every workout-source list screen (Library, Calendar,
 *  Intervals.icu Library): a magnifying-glass icon between "Other sources" and Refresh that
 *  expands into a full-width text field in their place, with a close button to collapse it and
 *  clear the query. This only owns the icon/field chrome and the query text — filtering the list
 *  by that query is each screen's own job. */
@Composable
internal fun SearchableHeaderRow(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    otherSources: List<Pair<String, () -> Unit>>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = ErgOnSurface.copy(alpha = 0.3f),
                ),
            )
            IconButton(onClick = {
                onQueryChange("")
                onActiveChange(false)
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        } else {
            WorkoutSourceMenu(otherSources)
            IconButton(onClick = { onActiveChange(true) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun LibraryFileRow(
    file: LibraryWorkoutFile,
    ftpWatts: Int,
    fetchPreview: suspend (LibraryWorkoutFile) -> List<WorkoutStep>,
    onImport: () -> Unit,
) {
    var previewSteps by remember(file.uri) { mutableStateOf<List<WorkoutStep>?>(null) }
    LaunchedEffect(file.uri) { previewSteps = fetchPreview(file) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // weight(1f) + maxLines/ellipsis: an unweighted wrapping Text in a Row can report
                // its measured width as the full row width and squeeze the Button that follows it
                // down to nothing — confirmed on a real device for the Library's equivalent row
                // with a long title. Bound the text instead of letting a long filename risk it.
                Text(
                    file.name.substringBeforeLast(".", file.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Button(onClick = onImport) { Text("Import") }
            }
            val steps = previewSteps
            if (!steps.isNullOrEmpty()) {
                Text(
                    formatWorkoutDuration(steps.sumOf { it.durationSec }),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                WorkoutMiniChart(
                    steps = steps,
                    ftpWatts = ftpWatts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

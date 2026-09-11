package com.ergrm.trainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.intervals.LibraryFolderGroup
import com.ergrm.trainer.intervals.LibraryWorkout
import com.ergrm.trainer.ui.theme.ErgOnSurface

@Composable
fun IntervalsLibraryScreen(viewModel: MainViewModel, onPicked: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.intervalsLibraryState.collectAsState()
    val loadState by viewModel.workoutLoadState.collectAsState()
    // Tracks the workout we asked to load, so this screen can show its own progress/error
    // instead of closing immediately and hoping the result is visible elsewhere — closing
    // unconditionally on tap used to hide load failures (and made "nothing happened" reports
    // impossible to tell apart from a real failure without a second screenshot round-trip).
    var pendingWorkoutId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.fetchIntervalsLibrary() }

    LaunchedEffect(loadState) {
        if (pendingWorkoutId != null && loadState is WorkoutLoadState.Loaded) {
            pendingWorkoutId = null
            onPicked()
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
            Text("Intervals.icu Library", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.fetchIntervalsLibrary() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        if (!settings.intervalsConfigured) {
            Text(
                "Set your Intervals.icu API key and athlete ID in Settings first.",
                style = MaterialTheme.typography.bodyMedium,
                color = ErgOnSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        when (val s = state) {
            IntervalsLibraryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
            is IntervalsLibraryUiState.Error -> {
                Text(
                    "Error: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is IntervalsLibraryUiState.Loaded -> {
                if (s.folders.isEmpty()) {
                    Text(
                        "No saved workouts found in your Intervals.icu library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErgOnSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // One section per top-level folder, in Intervals.icu's own order —
                        // including a folder with no workouts right now, so the list here always
                        // matches the real folder structure instead of silently dropping one.
                        s.folders.forEach { folder ->
                            item(key = "folder-${folder.name}") { FolderHeader(folder.name) }
                            if (folder.workouts.isEmpty()) {
                                item(key = "folder-${folder.name}-empty") {
                                    Text(
                                        "No workouts in this folder.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErgOnSurface,
                                    )
                                }
                            } else {
                                items(folder.workouts, key = { it.workoutId }) { workout ->
                                    val isPending = pendingWorkoutId == workout.workoutId
                                    LibraryWorkoutRow(
                                        workout = workout,
                                        isLoading = isPending && loadState is WorkoutLoadState.Loading,
                                        errorMessage = (loadState as? WorkoutLoadState.Error)?.message.takeIf { isPending },
                                        onPick = {
                                            pendingWorkoutId = workout.workoutId
                                            viewModel.loadIntervalsLibraryWorkout(workout)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHeader(name: String) {
    Text(
        name.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = ErgOnSurface,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun LibraryWorkoutRow(
    workout: LibraryWorkout,
    isLoading: Boolean,
    errorMessage: String?,
    onPick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The real cause of the "invisible" Load button: a long title wraps to 2 lines,
                // and an un-weighted Text in a Row reports its measured width as the full
                // available width whenever it wraps — squeezing the Button that follows it down
                // to nothing. weight(1f) bounds Text to the space left after the Button's own
                // (fixed) size instead, and maxLines/overflow keeps very long titles in check.
                Text(
                    workout.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                // A real device showed this row's trailing slot completely blank (but still
                // clickable!) when it alternated between two different composables (Button vs.
                // CircularProgressIndicator) here — keeping one Button always composed and only
                // swapping its inner content avoids whatever that was.
                Button(onClick = onPick, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Load")
                    }
                }
            }
            if (errorMessage != null) {
                Text(
                    "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

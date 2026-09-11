package com.ergrm.trainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.intervals.LibraryWorkout
import com.ergrm.trainer.ui.theme.ErgOnSurface

@Composable
fun IntervalsLibraryScreen(viewModel: MainViewModel, onPicked: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.intervalsLibraryState.collectAsState()

    LaunchedEffect(Unit) { viewModel.fetchIntervalsLibrary() }

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
                if (s.workouts.isEmpty()) {
                    Text(
                        "No saved workouts found in your Intervals.icu library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErgOnSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    val byFolder = s.workouts.groupBy { it.folderPath }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        byFolder.forEach { (folder, workouts) ->
                            item(key = "folder-$folder") { FolderHeader(folder) }
                            items(workouts, key = { it.workoutId }) { workout ->
                                LibraryWorkoutRow(workout) {
                                    viewModel.loadIntervalsLibraryWorkout(workout)
                                    onPicked()
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
private fun LibraryWorkoutRow(workout: LibraryWorkout, onPick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(workout.name, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onPick) { Text("Load") }
        }
    }
}

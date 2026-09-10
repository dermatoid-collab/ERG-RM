package com.ergrm.trainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ergrm.trainer.ble.TrainerConnectionState
import com.ergrm.trainer.ui.theme.ErgRmTheme

private enum class Screen { CONNECT, WORKOUT }
private enum class Overlay { NONE, SETTINGS, LIBRARY, HISTORY }

@Composable
fun ErgRmApp(viewModel: MainViewModel = viewModel()) {
    ErgRmTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var overlay by remember { mutableStateOf(Overlay.NONE) }
            var screen by remember { mutableStateOf(Screen.CONNECT) }
            val connectionState by viewModel.connectionState.collectAsState()
            val settings by viewModel.settings.collectAsState()
            val isConnected = connectionState is TrainerConnectionState.Ready

            // Jump to the workout screen automatically once the trainer connects,
            // but the user can also navigate there manually beforehand.
            LaunchedEffect(isConnected) {
                if (isConnected) screen = Screen.WORKOUT
            }

            Scaffold(
                topBar = {
                    // Compact custom bar instead of Material3's TopAppBar (which reserves ~64dp
                    // regardless of content) — matches TrainerDay's tighter chrome and frees up
                    // vertical space for the chart below.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .statusBarsPadding()
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("ERG-RM", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                overlay = Overlay.NONE
                                screen = Screen.WORKOUT
                            }) {
                                Icon(Icons.Filled.FitnessCenter, contentDescription = "Workout")
                            }
                            IconButton(onClick = {
                                overlay = Overlay.NONE
                                screen = Screen.CONNECT
                            }) {
                                Icon(
                                    Icons.Filled.Bluetooth,
                                    contentDescription = "Devices",
                                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = {
                                overlay = if (overlay == Overlay.LIBRARY) Overlay.NONE else Overlay.LIBRARY
                            }) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = "Workout library")
                            }
                            IconButton(onClick = {
                                overlay = if (overlay == Overlay.HISTORY) Overlay.NONE else Overlay.HISTORY
                            }) {
                                Icon(Icons.Filled.History, contentDescription = "History")
                            }
                            IconButton(onClick = {
                                overlay = if (overlay == Overlay.SETTINGS) Overlay.NONE else Overlay.SETTINGS
                            }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        overlay == Overlay.SETTINGS -> SettingsScreen(
                            settings = settings,
                            onSave = viewModel::saveIntervalsSettings,
                            onClose = { overlay = Overlay.NONE },
                        )
                        overlay == Overlay.LIBRARY -> LibraryScreen(
                            viewModel,
                            onImported = { overlay = Overlay.NONE },
                            onPickToday = {
                                viewModel.fetchTodayWorkout()
                                overlay = Overlay.NONE
                            },
                        )
                        overlay == Overlay.HISTORY -> HistoryScreen(viewModel)
                        screen == Screen.WORKOUT -> WorkoutScreen(
                            viewModel,
                            isTrainerConnected = isConnected,
                            onOpenLibrary = { overlay = Overlay.LIBRARY },
                        )
                        else -> ConnectScreen(viewModel)
                    }
                }
            }
        }
    }
}

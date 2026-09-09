package com.ergrm.trainer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                    TopAppBar(
                        title = { Text("ERG-RM") },
                        actions = {
                            IconButton(onClick = { screen = Screen.WORKOUT }) {
                                Icon(Icons.Filled.FitnessCenter, contentDescription = "Workout")
                            }
                            IconButton(onClick = { screen = Screen.CONNECT }) {
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
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
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

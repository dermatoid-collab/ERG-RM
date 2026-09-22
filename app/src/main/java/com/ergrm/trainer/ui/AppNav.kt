package com.ergrm.trainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ergrm.trainer.ble.HrConnectionState
import com.ergrm.trainer.ble.TrainerConnectionState
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgRmTheme
import com.ergrm.trainer.ui.theme.ErgWarn

private enum class Screen { CONNECT, WORKOUT }
private enum class Overlay { NONE, SETTINGS, LIBRARY, HISTORY, CALENDAR, INTERVALS_LIBRARY }

@Composable
fun ErgRmApp(viewModel: MainViewModel = viewModel()) {
    ErgRmTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var overlay by remember { mutableStateOf(Overlay.NONE) }
            var screen by remember { mutableStateOf(Screen.WORKOUT) }
            val connectionState by viewModel.connectionState.collectAsState()
            val hrConnectionState by viewModel.hrConnectionState.collectAsState()
            val isScanning by viewModel.isScanning.collectAsState()
            val isHrScanning by viewModel.isHrScanning.collectAsState()
            val settings by viewModel.settings.collectAsState()
            val isConnected = connectionState is TrainerConnectionState.Ready
            val snackbarHostState = remember { SnackbarHostState() }

            // Green only once the trainer, and the HR sensor if one is remembered at all, are
            // both actually connected — not just the trainer alone. Amber while either is still
            // mid-attempt — scanning for a device, or already found one and working through the
            // GATT handshake (auto-reconnect on launch, or a manual pick) — so the icon reflects
            // "still working on it" rather than looking identical to "nothing happening".
            val hrConfigured = settings.lastHrDeviceAddress != null
            val hrReady = !hrConfigured || hrConnectionState is HrConnectionState.Ready
            val bluetoothTint = when {
                isConnected && hrReady -> ErgAccent
                isScanning || isHrScanning ||
                    connectionState is TrainerConnectionState.Connecting ||
                    connectionState is TrainerConnectionState.DiscoveringServices ||
                    connectionState is TrainerConnectionState.RequestingControl ||
                    (hrConfigured && (hrConnectionState is HrConnectionState.Connecting || hrConnectionState is HrConnectionState.DiscoveringServices)) ->
                    ErgWarn
                else -> MaterialTheme.colorScheme.onSurface
            }

            // Workout is already the default landing screen, but this also pulls the rider back
            // to it if they'd navigated to Connect (e.g. to pick a different trainer) and it
            // then connects — same as a fresh connection would.
            LaunchedEffect(isConnected) {
                if (isConnected) screen = Screen.WORKOUT
            }

            LaunchedEffect(Unit) {
                viewModel.snackbarMessages.collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            LaunchedEffect(Unit) {
                AppNavigationEvents.openSettingsForBackup.collect {
                    overlay = Overlay.SETTINGS
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            NavIcon(
                                icon = Icons.Filled.FitnessCenter,
                                contentDescription = "Workout",
                                active = overlay == Overlay.NONE && screen == Screen.WORKOUT,
                                onClick = {
                                    overlay = Overlay.NONE
                                    screen = Screen.WORKOUT
                                },
                                underlineOffsetX = 1.5.dp,
                            )
                            NavIcon(
                                icon = Icons.Filled.Bluetooth,
                                contentDescription = "Devices",
                                active = overlay == Overlay.NONE && screen == Screen.CONNECT,
                                tint = bluetoothTint,
                                onClick = {
                                    overlay = Overlay.NONE
                                    screen = Screen.CONNECT
                                },
                            )
                            NavIcon(
                                icon = Icons.Filled.FolderOpen,
                                contentDescription = "Workout library",
                                active = overlay == Overlay.LIBRARY,
                                onClick = { overlay = Overlay.LIBRARY },
                            )
                            NavIcon(
                                icon = Icons.Filled.History,
                                contentDescription = "History",
                                active = overlay == Overlay.HISTORY,
                                onClick = { overlay = Overlay.HISTORY },
                                underlineOffsetX = 1.5.dp,
                            )
                            NavIcon(
                                icon = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                active = overlay == Overlay.SETTINGS,
                                onClick = { overlay = Overlay.SETTINGS },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        overlay == Overlay.SETTINGS -> SettingsScreen(
                            settings = settings,
                            viewModel = viewModel,
                            onSave = viewModel::saveIntervalsSettings,
                            onClose = { overlay = Overlay.NONE },
                        )
                        overlay == Overlay.LIBRARY -> LibraryScreen(
                            viewModel,
                            onImported = { overlay = Overlay.NONE },
                            onOpenCalendar = { overlay = Overlay.CALENDAR },
                            onOpenIntervalsLibrary = { overlay = Overlay.INTERVALS_LIBRARY },
                            onLoadToday = {
                                viewModel.fetchTodayWorkout()
                                overlay = Overlay.NONE
                            },
                        )
                        overlay == Overlay.HISTORY -> HistoryScreen(viewModel)
                        overlay == Overlay.CALENDAR -> CalendarScreen(
                            viewModel,
                            onPicked = { overlay = Overlay.NONE },
                            onLoadToday = {
                                viewModel.fetchTodayWorkout()
                                overlay = Overlay.NONE
                            },
                            onOpenIntervalsLibrary = { overlay = Overlay.INTERVALS_LIBRARY },
                            onOpenLibrary = { overlay = Overlay.LIBRARY },
                        )
                        overlay == Overlay.INTERVALS_LIBRARY -> IntervalsLibraryScreen(
                            viewModel,
                            onPicked = { overlay = Overlay.NONE },
                            onLoadToday = {
                                viewModel.fetchTodayWorkout()
                                overlay = Overlay.NONE
                            },
                            onOpenCalendar = { overlay = Overlay.CALENDAR },
                            onOpenLibrary = { overlay = Overlay.LIBRARY },
                        )
                        screen == Screen.WORKOUT -> WorkoutScreen(
                            viewModel,
                            isTrainerConnected = isConnected,
                            onOpenLibrary = { overlay = Overlay.LIBRARY },
                            onOpenCalendar = { overlay = Overlay.CALENDAR },
                            onOpenIntervalsLibrary = { overlay = Overlay.INTERVALS_LIBRARY },
                        )
                        else -> ConnectScreen(viewModel)
                    }
                }
            }
        }
    }
}

/** A top-bar icon with a thin underline mark when [active] — independent of [tint], so
 *  Bluetooth's own green "connected" tint and the "you're on this page" indicator can both show
 *  at once without fighting over the same signal. The underline carries no color meaning of its
 *  own (just presence/absence) since color on these icons is already spoken for by Bluetooth's
 *  connection-state tint.
 *
 *  [underlineOffsetX] nudges the mark left/right of dead center — some Material glyphs (the
 *  FitnessCenter dumbbell, the History clock-arrow) aren't drawn symmetrically within their own
 *  24dp bounds, so a mark centered on the *layout* box reads as off-center under the *glyph*. A
 *  perfectly symmetric icon (Bluetooth, FolderOpen, Settings) needs no correction. */
@Composable
private fun NavIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    underlineOffsetX: Dp = 0.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .offset(x = underlineOffsetX)
                .size(width = 20.dp, height = 2.5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (active) Color.White.copy(alpha = 0.55f) else Color.Transparent),
        )
    }
}

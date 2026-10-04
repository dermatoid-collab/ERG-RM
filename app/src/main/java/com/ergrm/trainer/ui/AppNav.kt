package com.ergrm.trainer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ergrm.trainer.ble.HrConnectionState
import com.ergrm.trainer.ble.TrainerConnectionState
import com.ergrm.trainer.data.AppSettings
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgRmTheme
import com.ergrm.trainer.ui.theme.ErgWarn

private enum class Screen { CONNECT, WORKOUT }
private enum class Overlay { NONE, SETTINGS, LIBRARY, HISTORY, CALENDAR, INTERVALS_LIBRARY }

// See ActiveWorkoutPager's doc comment: large enough to feel infinite, small enough that
// HorizontalPager's internal fling/snap math over this range doesn't hit Infinity/NaN.
private const val VIRTUAL_PAGE_COUNT = 100_000

@Composable
fun ErgRmApp(viewModel: MainViewModel = viewModel()) {
    ErgRmTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var overlay by remember { mutableStateOf(Overlay.NONE) }
            var screen by remember { mutableStateOf(Screen.WORKOUT) }
            // Collapses the topBar + WorkoutHeader into a single minibar row during an active
            // workout, freeing that space for the chart (Task #68). Driven declaratively off
            // hasStarted's transitions below, not polled continuously, so a manual toggle (the
            // minibar's back arrow, or tapping the Workout nav icon again) sticks until the next
            // real start/stop instead of being fought back on every recomposition.
            var chromeCollapsed by remember { mutableStateOf(false) }
            val workoutState by viewModel.workoutState.collectAsState()
            val workoutLoadState by viewModel.workoutLoadState.collectAsState()
            val workoutTitle = when (val loaded = workoutLoadState) {
                is WorkoutLoadState.Loaded -> loaded.name
                else -> if (workoutState.steps.isNotEmpty()) "Workout" else "No workout loaded"
            }
            LaunchedEffect(workoutState.hasStarted) {
                chromeCollapsed = workoutState.hasStarted
            }
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

            // Reset to the Workout screen the next time the app comes back to the foreground
            // after the user deliberately left it (Home key, recent-apps switcher) — not after
            // merely returning from a folder picker or the TCX share sheet the app itself opened,
            // which also back-and-forth through the activity lifecycle but aren't "closing the
            // app". AppNavigationEvents.userLeftApp is set only for the former (see
            // MainActivity.onUserLeaveHint) and read here as a plain synchronous flag — not via a
            // Flow collector — so the backup-notification's explicit Settings destination (which
            // clears it in handleIntent, guaranteed to run before this ON_START) always wins with
            // no coroutine-dispatch race between the two.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        // Consumed every ON_START regardless of userLeftApp, so a picker launch
                        // flag never lingers to wrongly suppress a later, genuine reset.
                        val returningFromPicker = AppNavigationEvents.pickerLaunchInFlight
                        AppNavigationEvents.pickerLaunchInFlight = false
                        if (AppNavigationEvents.userLeftApp) {
                            AppNavigationEvents.userLeftApp = false
                            if (!returningFromPicker) {
                                overlay = Overlay.NONE
                                screen = Screen.WORKOUT
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // The same 4 items, same order, on every screen's "Other sources" menu (Workout's own
            // header included) — each item still works when tapped from the screen it points to,
            // it's just a no-op, so the menu never has to leave anything out to stay consistent.
            val otherSources: List<Pair<String, () -> Unit>> = listOf(
                "Intervals.icu WOD" to {
                    viewModel.fetchTodayWorkout()
                    overlay = Overlay.NONE
                    screen = Screen.WORKOUT
                },
                "Calendar (Intervals.icu)" to { overlay = Overlay.CALENDAR },
                "Library (Intervals.icu)" to { overlay = Overlay.INTERVALS_LIBRARY },
                "Workout Library" to { overlay = Overlay.LIBRARY },
            )

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (workoutState.hasStarted && chromeCollapsed && overlay == Overlay.NONE && screen == Screen.WORKOUT) {
                        Minibar(
                            workoutTitle = workoutTitle,
                            otherSources = otherSources,
                            bluetoothTint = bluetoothTint,
                            onBack = { chromeCollapsed = false },
                        )
                    } else {
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
                                    // Already here with the workout running: a second tap re-
                                    // collapses to the minibar instead of being a no-op (Task #68's
                                    // step 4) — only reachable when chromeCollapsed is false, since
                                    // that's the only way the full topBar (and this icon) is showing
                                    // at all during an active workout.
                                    if (workoutState.hasStarted && overlay == Overlay.NONE && screen == Screen.WORKOUT) {
                                        chromeCollapsed = true
                                    } else {
                                        overlay = Overlay.NONE
                                        screen = Screen.WORKOUT
                                    }
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
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        // Reached only from "Other sources", not one of the 5 topBar icons, so
                        // they sit outside the swipeable set below (Task #73).
                        overlay == Overlay.CALENDAR -> CalendarScreen(
                            viewModel,
                            onPicked = { overlay = Overlay.NONE },
                            otherSources = otherSources,
                        )
                        overlay == Overlay.INTERVALS_LIBRARY -> IntervalsLibraryScreen(
                            viewModel,
                            onPicked = { overlay = Overlay.NONE },
                            otherSources = otherSources,
                        )
                        screen == Screen.WORKOUT && workoutState.hasStarted && chromeCollapsed ->
                            // The active-workout fullscreen state only (Task #75): swipe between
                            // the dashboard (today's WorkoutScreen body) and the new Vitals &
                            // Analytics screen, both under the shared minibar above. Disabled
                            // outside this state — the idle Workout screen keeps its normal full
                            // topBar and single body, no pager involved.
                            ActiveWorkoutPager(
                                dashboard = {
                                    WorkoutScreen(
                                        viewModel,
                                        isTrainerConnected = isConnected,
                                        otherSources = otherSources,
                                        chromeCollapsed = true,
                                    )
                                },
                                vitals = { VitalsScreen(viewModel) },
                            )
                        else -> MainPager(
                            overlay = overlay,
                            screen = screen,
                            onOverlayChange = { overlay = it },
                            onScreenChange = { screen = it },
                            viewModel = viewModel,
                            isConnected = isConnected,
                            otherSources = otherSources,
                            settings = settings,
                        )
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

/** Two pages swipeable with a circular/infinite feel (Task #75) despite there being only 2 real
 *  ones: a huge virtual page count with the real page taken as `virtualPage % 2` means swiping
 *  left off the last real page keeps scrolling smoothly into the first again, and the same the
 *  other way, rather than dead-ending at an edge. Starts on an even virtual page (the dashboard)
 *  every time this enters composition — i.e. on every collapse, including a re-collapse via the
 *  Workout nav icon — matching "re-collapsing always lands back on the dashboard+big chart".
 *
 *  [VIRTUAL_PAGE_COUNT] is deliberately NOT Int.MAX_VALUE — that froze the whole screen the
 *  instant this Composable entered composition (reported as the UI hanging right when a workout
 *  is started): HorizontalPager's fling/snap physics do floating-point math over the scrollable
 *  range implied by the page count, and a range that large produces Infinity/NaN partway through
 *  that math, which stalls rather than throws. 100,000 pages (50,000 swipes to wrap around) is
 *  still effectively infinite for a single ride, without tripping that. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveWorkoutPager(dashboard: @Composable () -> Unit, vitals: @Composable () -> Unit) {
    val pageCount = VIRTUAL_PAGE_COUNT
    val startPage = remember { (pageCount / 2) - (pageCount / 2) % 2 }
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        if (page % 2 == 0) dashboard() else vitals()
    }
}

/** Maps the 5 topBar-icon destinations (Calendar/Intervals.icu Library are reached only from
 *  "Other sources" and aren't part of this swipeable set) to a page index, 0-4 in icon order. */
private fun pageFor(overlay: Overlay, screen: Screen): Int = when (overlay) {
    Overlay.NONE -> if (screen == Screen.WORKOUT) 0 else 1
    Overlay.LIBRARY -> 2
    Overlay.HISTORY -> 3
    Overlay.SETTINGS -> 4
    Overlay.CALENDAR, Overlay.INTERVALS_LIBRARY -> 0 // unreachable here — see the when{} in ErgRmApp
}

/** Swipe between the app's 5 main destinations (Task #73), bounded (no wrap at either end) —
 *  Workout, Connect, Library, History, Settings, in the same order as their topBar icons. Two
 *  effects keep this pager and the existing (overlay, screen) state in sync in both directions
 *  without fighting each other: [targetPage] (derived from that state) drives the pager whenever
 *  something OTHER than a swipe changes it — tapping a topBar icon, the auto-reconnect jump to
 *  Workout, the reset-to-Workout-on-reopen effect — via animateScrollToPage; and settledPage
 *  (deliberately not currentPage, which also reports in-between pages while that same animated
 *  scroll is still under way) drives (overlay, screen) once a user's own swipe comes to rest on a
 *  page, so the two never fight over an in-progress animated transition. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainPager(
    overlay: Overlay,
    screen: Screen,
    onOverlayChange: (Overlay) -> Unit,
    onScreenChange: (Screen) -> Unit,
    viewModel: MainViewModel,
    isConnected: Boolean,
    otherSources: List<Pair<String, () -> Unit>>,
    settings: AppSettings,
) {
    val targetPage = pageFor(overlay, screen)
    val pagerState = rememberPagerState(initialPage = targetPage) { 5 }

    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            when (page) {
                0 -> { onOverlayChange(Overlay.NONE); onScreenChange(Screen.WORKOUT) }
                1 -> { onOverlayChange(Overlay.NONE); onScreenChange(Screen.CONNECT) }
                2 -> onOverlayChange(Overlay.LIBRARY)
                3 -> onOverlayChange(Overlay.HISTORY)
                4 -> onOverlayChange(Overlay.SETTINGS)
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> WorkoutScreen(
                viewModel,
                isTrainerConnected = isConnected,
                otherSources = otherSources,
                chromeCollapsed = false,
            )
            1 -> ConnectScreen(viewModel)
            2 -> LibraryScreen(
                viewModel,
                onImported = { onOverlayChange(Overlay.NONE) },
                otherSources = otherSources,
            )
            3 -> HistoryScreen(viewModel)
            else -> SettingsScreen(
                settings = settings,
                viewModel = viewModel,
                onSave = viewModel::saveIntervalsSettings,
                onClose = { onOverlayChange(Overlay.NONE) },
            )
        }
    }
}

/** The collapsed topBar shown during an active workout (Task #68): the full topBar's brand +
 *  5 nav icons and WorkoutScreen's own separate title row merge into this one line — a back
 *  arrow to re-expand (the workout itself keeps running either way), the workout name with its
 *  own "choose source" dropdown (identical menu to [WorkoutHeader]'s), and the Bluetooth status
 *  icon with the exact same connection-state tint as the full topBar's own Bluetooth nav icon. */
@Composable
private fun Minibar(
    workoutTitle: String,
    otherSources: List<Pair<String, () -> Unit>>,
    bluetoothTint: Color,
    onBack: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Expand", tint = MaterialTheme.colorScheme.onSurface)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    workoutTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose workout")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                otherSources.forEach { (label, action) ->
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
        Icon(
            Icons.Filled.Bluetooth,
            contentDescription = "Devices",
            tint = bluetoothTint,
            modifier = Modifier.size(24.dp),
        )
    }
}

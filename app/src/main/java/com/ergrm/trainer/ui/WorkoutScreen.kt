package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ergrm.trainer.ble.CoreTempReading
import com.ergrm.trainer.ble.TrainerSample
import com.ergrm.trainer.data.AppSettings
import com.ergrm.trainer.ui.theme.ErgAboveTarget
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgAtTarget
import com.ergrm.trainer.ui.theme.ErgBelowTarget
import com.ergrm.trainer.ui.theme.ErgCadenceLine
import com.ergrm.trainer.ui.theme.ErgDivider
import com.ergrm.trainer.ui.theme.ErgHrLine
import com.ergrm.trainer.ui.theme.ErgHrPlus
import com.ergrm.trainer.ui.theme.ErgIntensityDownGlyph
import com.ergrm.trainer.ui.theme.ErgIntensityUpGlyph
import com.ergrm.trainer.ui.theme.ErgLiveAccent
import com.ergrm.trainer.ui.theme.ErgModeErg
import com.ergrm.trainer.ui.theme.ErgModeSlo
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.ui.theme.ErgProgressLine
import com.ergrm.trainer.ui.theme.ErgSkinTemp
import com.ergrm.trainer.ui.theme.ErgSurface
import com.ergrm.trainer.ui.theme.ErgSurface2
import com.ergrm.trainer.ui.theme.ErgWarn
import com.ergrm.trainer.workout.ControlMode
import com.ergrm.trainer.workout.RouteProfile
import com.ergrm.trainer.workout.SamplePoint
import com.ergrm.trainer.workout.WorkoutRunState
import com.ergrm.trainer.workout.WorkoutStep
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun WorkoutScreen(
    viewModel: MainViewModel,
    isTrainerConnected: Boolean = true,
    otherSources: List<Pair<String, () -> Unit>> = emptyList(),
    // True while AppNav's minibar is showing the workout title in its place (Task #68) — skips
    // this screen's own WorkoutHeader so the title isn't shown twice, and so the chart's
    // weight(1f) picks up the freed row's height instead of just the topBar's.
    chromeCollapsed: Boolean = false,
    // Lets the active-workout pager (AppNav) hoist this screen's chart zoom above itself, so it
    // survives swiping to Vitals and back instead of resetting every time this composable is
    // disposed and recreated by the pager. Left null (default internal state) for the idle,
    // not-yet-started Workout screen, which isn't inside that pager.
    chartZoom: ChartZoom? = null,
    onChartZoomChange: ((ChartZoom) -> Unit)? = null,
) {
    val live by viewModel.liveData.collectAsState()
    val coreReading by viewModel.coreTempReading.collectAsState()
    val workoutState by viewModel.workoutState.collectAsState()
    val loadState by viewModel.workoutLoadState.collectAsState()
    val samples by viewModel.sampleHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showStopConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var autoBackupDialogReason by remember { mutableStateOf<AutoBackupNeededReason?>(null) }
    var internalChartZoom by remember { mutableStateOf(ChartZoom.FULL) }
    val effectiveChartZoom = chartZoom ?: internalChartZoom
    val setChartZoom = onChartZoomChange ?: { z: ChartZoom -> internalChartZoom = z }

    LaunchedEffect(Unit) {
        viewModel.autoBackupNeeded.collect { reason -> autoBackupDialogReason = reason }
    }

    val workoutTitle = when (val loaded = loadState) {
        is WorkoutLoadState.Loaded -> loaded.name
        else -> if (workoutState.steps.isNotEmpty()) "Workout" else "No workout loaded"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Same breakpoint as Vitals/SessionDetailScreen: wider-than-tall means the phone is
        // rotated, not a configuration lookup, so this also behaves sensibly on a tablet.
        if (maxWidth > maxHeight) {
            WorkoutScreenLandscape(
                viewModel = viewModel,
                isTrainerConnected = isTrainerConnected,
                otherSources = otherSources,
                chromeCollapsed = chromeCollapsed,
                workoutTitle = workoutTitle,
                live = live,
                coreReading = coreReading,
                workoutState = workoutState,
                loadState = loadState,
                samples = samples,
                settings = settings,
                effectiveChartZoom = effectiveChartZoom,
                setChartZoom = setChartZoom,
                onShowStopConfirm = { showStopConfirm = true },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (!chromeCollapsed) {
                    WorkoutHeader(title = workoutTitle, otherSources = otherSources)
                }

                if (!isTrainerConnected) {
                    Text(
                        "Trainer not connected — power targets won't be sent",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErgWarn,
                    )
                }
                WorkoutStatusLine(loadState)

                StatTileGrid(live, workoutState, settings.ftpWatts, settings.lthrBpm)

                CoreTempTileRow(reading = coreReading)

                ControlsRow(
                    // A GPX-route ride has no steps of its own (see WorkoutExecutor.loadRoute),
                    // so steps.isNotEmpty() alone would leave Play permanently disabled — the
                    // same bug for every one of these 4 hasWorkout call sites across this file and
                    // VitalsScreen.kt.
                    hasWorkout = workoutState.steps.isNotEmpty() || workoutState.routeProfile != null,
                    isRunning = workoutState.isRunning,
                    hasStarted = workoutState.hasStarted,
                    onPlay = { viewModel.startWorkout() },
                    onPause = { viewModel.pauseWorkout() },
                    onExit = { showStopConfirm = true },
                    onExtend = { viewModel.extendCurrentInterval() },
                    onSkip = { viewModel.skipStep() },
                )

                IntervalDetailsSection(
                    current = workoutState.currentStep,
                    next = workoutState.nextStep,
                    ftpWatts = settings.ftpWatts,
                    lthrBpm = settings.lthrBpm,
                    intensityPercent = workoutState.intensityPercent,
                    controlMode = workoutState.controlMode,
                )

                ChartCard(
                    steps = workoutState.steps,
                    currentStepIndex = workoutState.currentStepIndex,
                    totalElapsedSec = workoutState.totalElapsedSec,
                    totalDurationSec = workoutState.totalDurationSec,
                    samples = samples,
                    ftpWatts = settings.ftpWatts,
                    lthrBpm = settings.lthrBpm,
                    intensityPercent = workoutState.intensityPercent,
                    controlMode = workoutState.controlMode,
                    maxGradePercent = settings.maxGradePercent,
                    routeProfile = workoutState.routeProfile,
                    routeDistanceM = workoutState.routeDistanceM,
                    zoom = effectiveChartZoom,
                    onZoomChange = setChartZoom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                IntensityRow(
                    intensityPercent = workoutState.intensityPercent,
                    gradePercent = workoutState.currentGradePercent,
                    controlMode = workoutState.controlMode,
                    enabled = workoutState.steps.isNotEmpty(),
                    onDecreaseIntensity = { viewModel.decreaseIntensity() },
                    onIncreaseIntensity = { viewModel.increaseIntensity() },
                    onDecreaseGrade = { viewModel.decreaseGrade() },
                    onIncreaseGrade = { viewModel.increaseGrade() },
                    onToggleMode = { viewModel.toggleControlMode() },
                )
            }
        }
    }

    if (showStopConfirm) {
            // A plain Dialog instead of AlertDialog: AlertDialog only has 2 button slots
            // (confirm/dismiss), and this one needs 3 — Discard, Cancel, Save.
            Dialog(onDismissRequest = { showStopConfirm = false }) {
                Surface(shape = RoundedCornerShape(20.dp), color = ErgSurface2) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Stop workout?", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "This will save the workout to history and end the session.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                        ) {
                            // Material3's default button contentPadding (24dp horizontal) is
                            // sized for a single full-width button, not a 3-way equal split —
                            // at this width it left "Discard"/"Cancel" too little room and
                            // wrapped mid-word. Shrinking the padding (not the font) reclaims
                            // that space instead.
                            val pillPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)
                            OutlinedButton(
                                onClick = {
                                    showStopConfirm = false
                                    showDiscardConfirm = true
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErgAboveTarget),
                                contentPadding = pillPadding,
                                modifier = Modifier.weight(1f),
                            ) { Text("Discard", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                            OutlinedButton(
                                onClick = { showStopConfirm = false },
                                shape = RoundedCornerShape(50),
                                contentPadding = pillPadding,
                                modifier = Modifier.weight(1f),
                            ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                            Button(
                                onClick = {
                                    showStopConfirm = false
                                    viewModel.exitWorkout()
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = ErgAccent, contentColor = Color.Black),
                                contentPadding = pillPadding,
                                modifier = Modifier.weight(1f),
                            ) { Text("Save", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                        }
                    }
                }
            }
        }

        if (showDiscardConfirm) {
            AlertDialog(
                // Backing out (tap outside, back button) returns to the Discard/Cancel/Save
                // dialog rather than closing everything, same as tapping "No" below.
                onDismissRequest = {
                    showDiscardConfirm = false
                    showStopConfirm = true
                },
                title = { Text("Are you sure you want to discard?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDiscardConfirm = false
                            viewModel.discardWorkout()
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = ErgAboveTarget, contentColor = Color.Black),
                    ) { Text("Yes", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showDiscardConfirm = false
                            showStopConfirm = true
                        },
                        shape = RoundedCornerShape(50),
                    ) { Text("No", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                },
            )
        }

        autoBackupDialogReason?.let { reason ->
            AlertDialog(
                onDismissRequest = { autoBackupDialogReason = null },
                title = {
                    Text(if (reason == AutoBackupNeededReason.NOT_CONFIGURED) "Auto-backup not set up" else "Auto-backup failed")
                },
                text = {
                    Text(
                        if (reason == AutoBackupNeededReason.NOT_CONFIGURED) {
                            "This ride wasn't backed up — no auto-backup folder is set. Choose one in Settings?"
                        } else {
                            "This ride wasn't backed up — writing to the auto-backup folder failed. Check it in Settings?"
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        autoBackupDialogReason = null
                        AppNavigationEvents.openSettingsForBackup.tryEmit(Unit)
                    }) { Text("Go to Settings") }
                },
                dismissButton = {
                    TextButton(onClick = { autoBackupDialogReason = null }) { Text("Not now") }
                },
            )
        }
}

/** Landscape Workout: data (title, tiles, controls, interval details) on the left, the power
 *  chart at full height on the right — the chart is the one element that actually benefits from
 *  the extra width, so it gets most of it; the data column scrolls since landscape leaves far
 *  less height than portrait for the same stack of tiles. */
@Composable
private fun WorkoutScreenLandscape(
    viewModel: MainViewModel,
    isTrainerConnected: Boolean,
    otherSources: List<Pair<String, () -> Unit>>,
    chromeCollapsed: Boolean,
    workoutTitle: String,
    live: TrainerSample,
    coreReading: CoreTempReading?,
    workoutState: WorkoutRunState,
    loadState: WorkoutLoadState,
    samples: List<SamplePoint>,
    settings: AppSettings,
    effectiveChartZoom: ChartZoom,
    setChartZoom: (ChartZoom) -> Unit,
    onShowStopConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                // Now/Next moved onto the chart itself (see ChartCard's overlayCurrentStep), so
                // this column no longer needs to fit it — narrowed accordingly, the chart takes
                // the width it frees.
                .weight(0.22f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (!chromeCollapsed) {
                WorkoutHeader(title = workoutTitle, otherSources = otherSources)
            }

            if (!isTrainerConnected) {
                Text(
                    "Trainer not connected — power targets won't be sent",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgWarn,
                )
            }
            WorkoutStatusLine(loadState)

            WorkoutStatLines(live, workoutState, settings.ftpWatts, settings.lthrBpm, coreReading)

            ControlsRow(
                hasWorkout = workoutState.steps.isNotEmpty() || workoutState.routeProfile != null,
                isRunning = workoutState.isRunning,
                hasStarted = workoutState.hasStarted,
                onPlay = { viewModel.startWorkout() },
                onPause = { viewModel.pauseWorkout() },
                onExit = onShowStopConfirm,
                onExtend = { viewModel.extendCurrentInterval() },
                onSkip = { viewModel.skipStep() },
                compact = true,
            )

            IntensityRow(
                intensityPercent = workoutState.intensityPercent,
                gradePercent = workoutState.currentGradePercent,
                controlMode = workoutState.controlMode,
                enabled = workoutState.steps.isNotEmpty(),
                onDecreaseIntensity = { viewModel.decreaseIntensity() },
                onIncreaseIntensity = { viewModel.increaseIntensity() },
                onDecreaseGrade = { viewModel.decreaseGrade() },
                onIncreaseGrade = { viewModel.increaseGrade() },
                onToggleMode = { viewModel.toggleControlMode() },
            )
        }

        ChartCard(
            steps = workoutState.steps,
            currentStepIndex = workoutState.currentStepIndex,
            totalElapsedSec = workoutState.totalElapsedSec,
            totalDurationSec = workoutState.totalDurationSec,
            samples = samples,
            ftpWatts = settings.ftpWatts,
            lthrBpm = settings.lthrBpm,
            intensityPercent = workoutState.intensityPercent,
            controlMode = workoutState.controlMode,
            maxGradePercent = settings.maxGradePercent,
            routeProfile = workoutState.routeProfile,
            routeDistanceM = workoutState.routeDistanceM,
            zoom = effectiveChartZoom,
            onZoomChange = setChartZoom,
            overlayCurrentStep = workoutState.currentStep,
            overlayNextStep = workoutState.nextStep,
            modifier = Modifier
                .weight(0.78f)
                .fillMaxHeight(),
        )
    }
}

/** Plain "label: value" line — the landscape data column's compact stand-in for a boxed
 *  StatTile/DetailTile, so the stack of live readings takes a fraction of the vertical space a
 *  grid of tiles would, leaving more height (and, via the column's own weight, more width) for
 *  the chart beside it. */
@Composable
private fun StatLine(label: String, value: String, valueColor: Color = ErgOnSurface, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = ErgOnSurface.copy(alpha = 0.65f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

/** Landscape stand-in for [StatTileGrid] + [CoreTempTileRow]: same live values, as stacked text
 *  lines instead of a grid of tiles. Drops the tap-to-toggle elapsed/remaining and %FTP views —
 *  one fixed reading per line keeps this genuinely compact; the full interactive tiles are still
 *  what portrait shows. */
@Composable
private fun WorkoutStatLines(
    live: TrainerSample,
    workoutState: WorkoutRunState,
    ftpWatts: Int,
    lthrBpm: Int,
    coreReading: CoreTempReading?,
    modifier: Modifier = Modifier,
) {
    val actual = live.powerWatts ?: 0
    val target = workoutState.currentTargetWatts
    val powerColor = when {
        target <= 0 -> ErgOnSurface
        actual < target - 15 -> ErgWarn
        actual > target + 15 -> ErgAboveTarget
        else -> ErgAtTarget
    }
    val zone = zoneFor(target, ftpWatts, ErgOnSurface)
    val mode = workoutState.controlMode
    val wattsColor = when (mode) {
        ControlMode.HR_PLUS -> zone.color
        ControlMode.SLO -> ErgOnSurface
        ControlMode.ERG -> powerColor
    }
    val hrTargetColor = if (mode == ControlMode.HR_PLUS) {
        workoutState.currentTargetBpm?.let { hrTarget ->
            live.heartRateBpm?.let { hrActual ->
                when {
                    hrActual < hrTarget - 5 -> ErgBelowTarget
                    hrActual > hrTarget + 5 -> ErgAboveTarget
                    else -> ErgAtTarget
                }
            }
        }
    } else {
        null
    }
    val hrZoneColor = live.heartRateBpm?.let { zoneForHr(it, lthrBpm, ErgOnSurface).color } ?: ErgOnSurface
    val hrColor = hrTargetColor ?: hrZoneColor
    val grade = workoutState.currentGradePercent
    val gradeColor = when {
        grade > 0.05f -> ErgAboveTarget
        grade < -0.05f -> ErgBelowTarget
        else -> ErgAtTarget
    }
    val flooredCore = coreReading?.coreTempC?.let { floorToOneDecimal(it) }
    val flooredHsi = coreReading?.heatStrainIndex?.let { floorToOneDecimal(it) }

    val route = workoutState.routeProfile
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (route != null) {
            StatLine("Distance", "%.1f km".format(workoutState.routeDistanceM / 1000f))
            StatLine("Remaining", "%.1f km".format(((route.totalDistanceM - workoutState.routeDistanceM) / 1000f).coerceAtLeast(0f)))
        } else {
            StatLine("Interval", formatTime(workoutState.remainingInStepSec))
            StatLine("Total", formatTime(workoutState.totalRemainingSec))
        }
        StatLine("Cadence", live.cadenceRpm?.let { "${it.toInt()} rpm" } ?: "--")
        when (mode) {
            ControlMode.HR_PLUS -> {
                StatLine("Watts", "$actual W", valueColor = wattsColor)
                StatLine("Target HR", (workoutState.currentTargetBpm?.toString() ?: "--") + " bpm")
                StatLine("HR", live.heartRateBpm?.let { "$it bpm" } ?: "--", valueColor = hrColor)
            }
            ControlMode.SLO -> {
                StatLine("Watts", "$actual W", valueColor = wattsColor)
                StatLine("Grade", (if (grade == 0f) "0.0" else "%+.1f".format(grade)) + " %", valueColor = gradeColor)
                StatLine("HR", live.heartRateBpm?.let { "$it bpm" } ?: "--", valueColor = hrColor)
            }
            ControlMode.ERG -> {
                StatLine("HR", live.heartRateBpm?.let { "$it bpm" } ?: "--", valueColor = hrColor)
                StatLine("Target W", "$target W", valueColor = zone.color)
                StatLine("Watts", "$actual W", valueColor = wattsColor)
            }
        }
        StatLine("Skin", coreReading?.skinTempC?.let { "%.1f°".format(it) } ?: "--", valueColor = ErgSkinTemp)
        StatLine(
            "Core", flooredCore?.let { "%.1f°".format(it) } ?: "--",
            valueColor = flooredCore?.let { coreColor(it) } ?: ErgOnSurface,
        )
        StatLine(
            "HSI", flooredHsi?.let { "%.1f".format(it) } ?: "--",
            valueColor = flooredHsi?.let { hsiColor(it) } ?: ErgOnSurface,
        )
    }
}

@Composable
private fun WorkoutStatusLine(loadState: WorkoutLoadState) {
    val text = when (loadState) {
        WorkoutLoadState.Loading -> "Loading workout…"
        WorkoutLoadState.Empty -> "No workout planned for today on Intervals.icu"
        is WorkoutLoadState.Error -> "Error: ${loadState.message}"
        else -> null
    }
    if (text != null) {
        val color = if (loadState is WorkoutLoadState.Error) ErgAboveTarget else ErgOnSurface
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Matches TrainerDay: tapping a tile cycles its display mode instead of adding a separate
 *  control. Interval/Total each toggle elapsed vs. remaining independently; tapping either of
 *  Target watts/Watts flips both together between absolute watts and %FTP, since they show the
 *  same underlying pair of numbers in two units. */
@Composable
private fun StatTileGrid(
    live: TrainerSample,
    workoutState: WorkoutRunState,
    ftpWatts: Int,
    lthrBpm: Int,
) {
    val actual = live.powerWatts ?: 0
    val target = workoutState.currentTargetWatts
    val powerColor = when {
        target <= 0 -> ErgOnSurface
        // Z4's amber (ErgWarn shares its hex) instead of ErgBelowTarget's blue — that blue stays
        // Z2's own zone color and HR's below-target color, both untouched by this Watts-only ask.
        actual < target - 15 -> ErgWarn
        actual > target + 15 -> ErgAboveTarget
        else -> ErgAtTarget
    }
    val zone = zoneFor(target, ftpWatts, ErgOnSurface)
    val mode = workoutState.controlMode
    val isHrPlus = mode == ControlMode.HR_PLUS
    val isSlo = mode == ControlMode.SLO
    // The row a metric lands in decides its color style, not the metric itself: whichever stat
    // sits next to Cadence (row 2) is colored by zone, and whichever sits next to Target (row 3)
    // is colored by deviation from that target — Watts is in row 2 in HR+, row 3 in ERG, so it
    // flips between the two the same way HR already does below. SLO has no target at all (power
    // emerges from effort against the simulated grade), so its Watts just stays plain.
    val wattsColor = when (mode) {
        ControlMode.HR_PLUS -> zone.color
        ControlMode.SLO -> ErgOnSurface
        ControlMode.ERG -> powerColor
    }
    // Mirrors powerColor's ±15W-vs-target logic, at a ±5bpm deadband — but only HR+ has a bpm
    // target to compare against, so this only applies there (ERG has none, and SLO's is the
    // stale value from whichever mode ran before it). Both other modes fall back to the plain
    // HR-zone color instead.
    val hrTargetColor = if (isHrPlus) {
        workoutState.currentTargetBpm?.let { hrTarget ->
            live.heartRateBpm?.let { hrActual ->
                when {
                    hrActual < hrTarget - 5 -> ErgBelowTarget
                    hrActual > hrTarget + 5 -> ErgAboveTarget
                    else -> ErgAtTarget
                }
            }
        }
    } else {
        null
    }
    val hrZoneColor = live.heartRateBpm?.let { zoneForHr(it, lthrBpm, ErgOnSurface).color } ?: ErgOnSurface
    val hrColor = hrTargetColor ?: hrZoneColor
    val grade = workoutState.currentGradePercent
    val gradeColor = when {
        grade > 0.05f -> ErgAboveTarget
        grade < -0.05f -> ErgBelowTarget
        else -> ErgAtTarget
    }

    var intervalShowElapsed by remember { mutableStateOf(false) }
    var totalShowElapsed by remember { mutableStateOf(false) }
    var showPercentFtp by remember { mutableStateOf(false) }

    // Follows each tile's own elapsed/remaining toggle: showing elapsed fills the bar with that
    // fraction, blue growing from the left (progressAnchorEnd = false); showing remaining fills
    // it with the remaining fraction instead, anchored to the right so it's the blue portion
    // itself — "what's left" — that visibly shrinks away over time, not just some arbitrary
    // left-anchored sliver (progressAnchorEnd = true, see StatTile).
    val stepDurationSec = workoutState.currentStep?.durationSec ?: 0
    val intervalElapsedFraction = if (stepDurationSec > 0) workoutState.elapsedInStepSec / stepDurationSec.toFloat() else 0f
    val intervalProgress = if (intervalShowElapsed) intervalElapsedFraction else 1f - intervalElapsedFraction
    val totalElapsedFraction = if (workoutState.totalDurationSec > 0) workoutState.totalElapsedSec / workoutState.totalDurationSec.toFloat() else 0f
    val totalProgress = if (totalShowElapsed) totalElapsedFraction else 1f - totalElapsedFraction

    val route = workoutState.routeProfile
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            if (route != null) {
                // A GPX route has no time-based "interval"/"total" to show — distance along the
                // route is the equivalent progress measure here.
                val routeProgress = (workoutState.routeDistanceM / route.totalDistanceM).coerceIn(0f, 1f)
                StatTile(
                    label = "Distance",
                    value = "%.1f".format(workoutState.routeDistanceM / 1000f),
                    unit = "km",
                    modifier = Modifier.weight(1f),
                    progress = routeProgress,
                )
                StatTile(
                    label = "Remaining",
                    value = "%.1f".format(((route.totalDistanceM - workoutState.routeDistanceM) / 1000f).coerceAtLeast(0f)),
                    unit = "km",
                    modifier = Modifier.weight(1f),
                    progress = 1f - routeProgress,
                    progressAnchorEnd = true,
                )
            } else {
                StatTile(
                    label = "Interval",
                    value = formatTime(if (intervalShowElapsed) workoutState.elapsedInStepSec else workoutState.remainingInStepSec),
                    modifier = Modifier.weight(1f),
                    progress = intervalProgress,
                    progressAnchorEnd = !intervalShowElapsed,
                    onClick = { intervalShowElapsed = !intervalShowElapsed },
                )
                StatTile(
                    label = "Total",
                    value = formatTime(if (totalShowElapsed) workoutState.totalElapsedSec else workoutState.totalRemainingSec),
                    modifier = Modifier.weight(1f),
                    progress = totalProgress,
                    progressAnchorEnd = !totalShowElapsed,
                    onClick = { totalShowElapsed = !totalShowElapsed },
                )
            }
        }
        // The Watts/%FTP tile and the plain HR tile trade positions between modes instead of one
        // relabeling into a second HR tile — in HR+ the trainer is still holding a real power
        // (corrected toward the HR target), so Watts stays worth seeing, and duplicating HR
        // instead of showing it would leave nothing telling the rider what power they're on.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                label = "Cadence",
                value = live.cadenceRpm?.let { "${it.toInt()}" } ?: "--",
                modifier = Modifier.weight(1f),
                unit = "rpm",
            )
            if (isSlo) {
                StatTile(
                    label = "Watts",
                    value = "$actual",
                    modifier = Modifier.weight(1f),
                    valueColor = wattsColor,
                    unit = "W",
                )
            } else if (isHrPlus) {
                StatTile(
                    label = if (showPercentFtp) "% FTP" else "Watts",
                    value = if (showPercentFtp) percentOfFtp(actual, ftpWatts) else "$actual",
                    modifier = Modifier.weight(1f),
                    valueColor = wattsColor,
                    unit = if (showPercentFtp) null else "W",
                    onClick = { showPercentFtp = !showPercentFtp },
                )
            } else {
                StatTile(
                    label = "HR",
                    value = live.heartRateBpm?.let { "$it" } ?: "--",
                    modifier = Modifier.weight(1f),
                    valueColor = hrColor,
                    unit = "bpm",
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            if (isSlo) {
                StatTile(
                    label = "Grade",
                    value = if (grade == 0f) "0.0" else "%+.1f".format(grade),
                    modifier = Modifier.weight(1f),
                    valueColor = gradeColor,
                    unit = "%",
                )
            } else {
                StatTile(
                    label = when { isHrPlus -> "Target HR"; showPercentFtp -> "Target % FTP"; else -> "Target watts" },
                    value = when {
                        isHrPlus -> workoutState.currentTargetBpm?.toString() ?: "--"
                        showPercentFtp -> percentOfFtp(target, ftpWatts)
                        else -> "$target"
                    },
                    modifier = Modifier.weight(1f),
                    unit = when { isHrPlus -> "bpm"; showPercentFtp -> null; else -> "W" },
                    zoneLabel = zone.label,
                    zoneColor = zone.color,
                    onClick = { showPercentFtp = !showPercentFtp },
                )
            }
            if (isHrPlus || isSlo) {
                StatTile(
                    label = "HR",
                    value = live.heartRateBpm?.let { "$it" } ?: "--",
                    modifier = Modifier.weight(1f),
                    valueColor = hrColor,
                    unit = "bpm",
                )
            } else {
                StatTile(
                    label = if (showPercentFtp) "% FTP" else "Watts",
                    value = if (showPercentFtp) percentOfFtp(actual, ftpWatts) else "$actual",
                    modifier = Modifier.weight(1f),
                    valueColor = wattsColor,
                    unit = if (showPercentFtp) null else "W",
                    onClick = { showPercentFtp = !showPercentFtp },
                )
            }
        }
    }
}

private fun percentOfFtp(watts: Int, ftpWatts: Int): String =
    if (ftpWatts > 0) "${(watts * 100f / ftpWatts).roundToInt()}%" else "--"

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ErgOnSurface,
    unit: String? = null,
    zoneLabel: String? = null,
    zoneColor: Color = ErgOnSurface,
    progress: Float? = null,
    // false (default): the fill represents elapsed time and grows from the left, anchored start.
    // true: the fill represents remaining time and should instead be anchored to the right —
    // shrinking away from its own left edge as time passes, not from the right — so what's
    // actually running out visually recedes from the correct side.
    progressAnchorEnd: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .background(ErgSurface, RoundedCornerShape(13.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = ErgOnSurface.copy(alpha = 0.6f),
            )
            if (zoneLabel != null) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    zoneLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    modifier = Modifier
                        .background(zoneColor, RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Row(modifier = Modifier.padding(top = 1.dp)) {
            Text(
                value,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    unit,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = valueColor.copy(alpha = 0.6f),
                    modifier = Modifier.alignByBaseline().padding(start = 3.dp),
                )
            }
        }
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ErgOnSurface.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .align(if (progressAnchorEnd) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(ErgLiveAccent),
                )
            }
        }
    }
}

/** Floors to the same 1-decimal precision the CORE/HSI tiles display ("%.1f"), so the displayed
 *  number and the color threshold it's checked against always agree — checking the sensor's full
 *  raw precision against these thresholds let e.g. a raw 38.24 (displayed as "38.2°") land in the
 *  38.3-38.5 band, showing the same on-screen number in two different colors depending on the
 *  hidden decimals. */
internal fun floorToOneDecimal(value: Float): Float = floor(value * 10) / 10f

/** Green/yellow/red/purple, matching the same escalating-severity palette used for HR and power
 *  zones elsewhere on this screen (at-target green, warning amber, above-target/thermal red, and
 *  the max-zone purple) rather than inventing new colors for a fourth kind of zone. */
internal fun hsiColor(hsi: Float): Color = when {
    hsi <= 0.9f -> ErgAccent
    hsi <= 2.9f -> ErgWarn
    hsi <= 6.9f -> ErgHrLine
    else -> ZONE_MAX.color
}

/** Core temperature thresholds, same escalating-severity palette as [hsiColor] but with a blue
 *  "below normal" band instead of green, since core temp has no healthy-at-target reading the
 *  way HSI's 0 does. */
internal fun coreColor(coreTempC: Float): Color = when {
    coreTempC <= 38.2f -> ErgBelowTarget
    coreTempC <= 38.5f -> ErgWarn
    coreTempC <= 38.9f -> ErgHrLine
    else -> ZONE_MAX.color
}

/** Core/skin temperature and Heat Strain Index from an optional CORE sensor, as a row of 3 tiles
 *  between the 3rd StatTileGrid row and the controls — out of the chart entirely. Label+value
 *  stay on a single line each (unlike the other StatTiles' label-above-value layout) to keep
 *  these tiles short. Always shown, "--" when nothing is connected yet, same as every other live
 *  reading on this screen. */
@Composable
private fun CoreTempTileRow(reading: CoreTempReading?, modifier: Modifier = Modifier) {
    val flooredCore = reading?.coreTempC?.let { floorToOneDecimal(it) }
    val flooredHsi = reading?.heatStrainIndex?.let { floorToOneDecimal(it) }
    // SKIN, then CORE (the center tile), then HSI.
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CoreTempTile(
            label = "SKIN",
            value = reading?.skinTempC?.let { "%.1f°".format(it) } ?: "--",
            valueColor = ErgSkinTemp,
            modifier = Modifier.weight(1f),
        )
        CoreTempTile(
            label = "CORE",
            value = flooredCore?.let { "%.1f°".format(it) } ?: "--",
            valueColor = flooredCore?.let { coreColor(it) } ?: ErgOnSurface,
            modifier = Modifier.weight(1f),
        )
        CoreTempTile(
            label = "HSI",
            value = flooredHsi?.let { "%.1f".format(it) } ?: "--",
            valueColor = flooredHsi?.let { hsiColor(it) } ?: ErgOnSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CoreTempTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ErgOnSurface,
) {
    Row(
        modifier = modifier
            .background(ErgSurface, RoundedCornerShape(13.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label.uppercase(),
            // Same label style as every other StatTile ("Target watts" included), just x1.1 —
            // this tile just keeps label+value on one line instead of two.
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.1.sp),
            color = ErgOnSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            value,
            fontSize = 17.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * Chart zoom window, cycled by tapping the chart's empty area above the bars: full workout ->
 * 20 min -> 5 min -> full. [scrollThresholdSec] is how far into a zoomed window the progress
 * line travels (pinned at the left edge) before the window starts scrolling to keep it in place.
 */
enum class ChartZoom(val windowSec: Int?, val scrollThresholdSec: Int) {
    FULL(null, 0),
    TWENTY_MIN(20 * 60, 5 * 60),
    FIVE_MIN(5 * 60, 60);

    fun next(): ChartZoom = when (this) {
        FULL -> TWENTY_MIN
        TWENTY_MIN -> FIVE_MIN
        FIVE_MIN -> FULL
    }
}

/** Spacing between minute labels on the axis below the chart. Fixed for the two zoomed-in
 *  levels; at FULL it scales with the workout's own total length so a 3-hour plan doesn't end
 *  up with 18 crowded labels the way a fixed 10-minute step would. */
/** "Nice" km spacing for [ChartTimeAxis]'s route-mode ticks — a handful of labels across
 *  whatever the route's total distance turns out to be, same spirit as [axisLabelIntervalMin]. */
internal fun distanceLabelIntervalKm(totalKm: Float): Float = when {
    totalKm <= 5f -> 1f
    totalKm <= 15f -> 2f
    totalKm <= 40f -> 5f
    totalKm <= 80f -> 10f
    totalKm <= 150f -> 20f
    else -> 50f
}

/** Whole km when the step is whole (10, 20…), one decimal when it isn't (1.0, 2.0) — matches how
 *  [distanceLabelIntervalKm]'s steps are either whole already or sub-integer. */
internal fun formatAxisKm(km: Float): String =
    if (km == km.toInt().toFloat()) "${km.toInt()}" else "%.1f".format(km)

internal fun axisLabelIntervalMin(zoom: ChartZoom, totalDurationSec: Int): Int = when (zoom) {
    ChartZoom.FIVE_MIN -> 1
    ChartZoom.TWENTY_MIN -> 5
    ChartZoom.FULL -> {
        val totalMin = totalDurationSec / 60
        when {
            totalMin <= 60 -> 10
            totalMin <= 120 -> 15
            totalMin <= 180 -> 20
            else -> 30
        }
    }
}

/** The window [start, end) the chart currently shows, in elapsed seconds. In a zoomed level the
 *  progress line stays pinned [ChartZoom.scrollThresholdSec] from the window's left edge (or at
 *  elapsed time if less has passed) — i.e. it sits at the left edge until that much time has
 *  passed, then the window scrolls to keep it fixed there. */
internal fun computeChartWindow(zoom: ChartZoom, totalElapsedSec: Int, totalDurationSec: Int): Pair<Int, Int> {
    val windowSec = zoom.windowSec
    if (windowSec == null || windowSec >= totalDurationSec) {
        return 0 to totalDurationSec
    }
    val start = (totalElapsedSec - zoom.scrollThresholdSec).coerceAtLeast(0)
    return start to (start + windowSec)
}

/** Which step (if any) covers elapsed time [t]. */
private fun stepIndexAt(t: Int, steps: List<WorkoutStep>): Int? {
    var acc = 0
    steps.forEachIndexed { index, step ->
        val stepStart = acc
        val stepEnd = acc + step.durationSec
        acc = stepEnd
        if (t in stepStart until stepEnd) return index
        if (index == steps.lastIndex && t >= stepStart) return index
    }
    return null
}

/** [start, end) elapsed-seconds range of the step at [index]. */
private fun stepTimeRange(index: Int, steps: List<WorkoutStep>): Pair<Int, Int> {
    var acc = 0
    steps.forEachIndexed { i, step ->
        val start = acc
        acc += step.durationSec
        if (i == index) return start to acc
    }
    return 0 to 0
}

/** Draws a rounded pill with centered text, returning its width so the caller can chain pills. */
private fun DrawScope.drawPill(text: String, x: Float, y: Float, bg: Color, textColor: Color, textMeasurer: TextMeasurer): Float {
    val measured = textMeasurer.measure(text, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor))
    val paddingH = 10.dp.toPx()
    val paddingV = 6.dp.toPx()
    val pillWidth = measured.size.width + paddingH * 2
    val pillHeight = measured.size.height + paddingV * 2
    drawRoundRect(
        color = bg,
        topLeft = Offset(x, y),
        size = Size(pillWidth, pillHeight),
        cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f),
    )
    drawText(measured, topLeft = Offset(x + paddingH, y + paddingV))
    return pillWidth
}

private data class ChartInputs(
    val steps: List<WorkoutStep>,
    val currentStepIndex: Int,
    val totalElapsedSec: Int,
    val totalDurationSec: Int,
    val intensityPercent: Int,
    val ftpWatts: Int,
    val controlMode: ControlMode,
)

@Composable
private fun ChartCard(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    controlMode: ControlMode,
    maxGradePercent: Float,
    routeProfile: RouteProfile?,
    routeDistanceM: Float,
    zoom: ChartZoom,
    onZoomChange: (ChartZoom) -> Unit,
    // Landscape only: when set, draws the Now/Next detail as a compact pill centered over the
    // top of the chart instead of its own row elsewhere — see WorkoutScreenLandscape, the only
    // caller that passes these. Portrait keeps IntervalDetailsSection in its usual spot, so
    // leaves both null.
    overlayCurrentStep: WorkoutStep? = null,
    overlayNextStep: WorkoutStep? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ErgSurface, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            WorkoutProfileChart(
                steps = steps,
                currentStepIndex = currentStepIndex,
                totalElapsedSec = totalElapsedSec,
                totalDurationSec = totalDurationSec,
                samples = samples,
                ftpWatts = ftpWatts,
                lthrBpm = lthrBpm,
                intensityPercent = intensityPercent,
                controlMode = controlMode,
                maxGradePercent = maxGradePercent,
                routeProfile = routeProfile,
                routeDistanceM = routeDistanceM,
                zoom = zoom,
                onZoomChange = onZoomChange,
                modifier = Modifier.fillMaxSize(),
            )
            if (overlayCurrentStep != null) {
                NowNextOverlayPill(
                    current = overlayCurrentStep,
                    next = overlayNextStep,
                    ftpWatts = ftpWatts,
                    lthrBpm = lthrBpm,
                    intensityPercent = intensityPercent,
                    controlMode = controlMode,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

/** Landscape's compact stand-in for [IntervalDetailsSection]: same Now/Next data, laid out as one
 *  free-floating pill over the chart instead of a fixed-width row in the data column, where it
 *  was clipping the power value and losing the Next zone chip entirely. No weight()/marquee here
 *  (unlike [IntervalDetailBlock]) — the pill just sizes to its own content since nothing here
 *  constrains its width. */
@Composable
private fun NowNextOverlayPill(
    current: WorkoutStep?,
    next: WorkoutStep?,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    controlMode: ControlMode,
    modifier: Modifier = Modifier,
) {
    if (current == null) return
    Row(
        modifier = modifier
            .background(ErgSurface.copy(alpha = 0.92f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OverlayPillEntry(
            label = "Now",
            step = current,
            ftpWatts = ftpWatts,
            lthrBpm = lthrBpm,
            intensityPercent = intensityPercent,
            controlMode = controlMode,
            accentColor = ErgLiveAccent,
        )
        if (next != null) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(ErgOnSurface.copy(alpha = 0.2f)),
            )
            OverlayPillEntry(
                label = "Next",
                step = next,
                ftpWatts = ftpWatts,
                lthrBpm = lthrBpm,
                intensityPercent = intensityPercent,
                controlMode = controlMode,
                accentColor = null,
            )
        }
    }
}

@Composable
private fun OverlayPillEntry(
    label: String,
    step: WorkoutStep,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    controlMode: ControlMode,
    accentColor: Color?,
) {
    val scaledStart = (step.startWatts * intensityPercent / 100f).roundToInt()
    val scaledEnd = (step.endWatts * intensityPercent / 100f).roundToInt()
    val zone = zoneFor(scaledEnd, ftpWatts, ErgOnSurface)
    val valueLabel = if (controlMode == ControlMode.HR_PLUS && ftpWatts > 0) {
        bpmLabel(scaledStart, scaledEnd, ftpWatts, lthrBpm)
    } else {
        wattsLabel(scaledStart, scaledEnd)
    }
    // bodyMedium (14sp) to match the data column's own WorkoutStatLines text — was labelSmall
    // (11sp), noticeably smaller than everything to its left.
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = accentColor ?: ErgOnSurface, maxLines = 1)
        Text(formatMinSec(step.durationSec), style = MaterialTheme.typography.bodyMedium, color = accentColor ?: Color.Unspecified, maxLines = 1)
        Text(valueLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(
            zone.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            maxLines = 1,
            modifier = Modifier
                .background(zone.color, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** Headroom left empty above the chart's max watts, as a fraction of the chart height — just
 *  enough for the top axis labels, so the 550W/210bpm labels sit near the very top edge instead
 *  of wasting vertical space above them. */
internal const val CHART_TOP_HEADROOM = 0.08f

/** Fraction of the chart's height, from the top, that's the tap-to-zoom target — deliberately
 *  independent of and much larger than [CHART_TOP_HEADROOM] (which only sizes the axis-label
 *  margin). Most real workouts spend most of their profile well under the 550W ceiling, so a
 *  generous fixed band is a far more reliable zoom target than trying to track each bar's own
 *  height; the bottom quarter selects whatever interval is at that x for its tooltip instead. */
private const val CHART_ZOOM_TAP_FRACTION = 0.75f

/** How long a chart's tap-to-zoom gesture waits for the finger to lift before giving up on it —
 *  see the comment at its pointerInput's awaitEachGesture for why this exists. Generous relative
 *  to any real tap or short press, short enough to recover quickly from one stuck gesture. */
private const val GESTURE_UP_TIMEOUT_MS = 1500L

/** How often [IntervalDetailBlock]'s Now/Next value Text is force-recreated as a last-resort
 *  recovery from a wedged basicMarquee animation — see the comment at its own `key()` call.
 *  Long enough that a healthy marquee's remount is rarely noticed, short enough that a stuck one
 *  never stays stuck for more than one of these. */
private const val MARQUEE_SELF_HEAL_INTERVAL_MS = 12_000L

/**
 * Watts ceiling for the chart's Y axis, fixed relative to FTP (not auto-fit to the data) so that
 * raising or lowering the live %FTP intensity actually changes bar heights against a stable
 * reference instead of the axis rescaling to compensate and hiding the change.
 */
internal fun chartMaxWatts(ftpWatts: Int): Float = if (ftpWatts > 0) 1.8f * ftpWatts else 550f

/** HR ceiling, fixed relative to LTHR for the same reason [chartMaxWatts] is fixed to FTP. */
internal fun chartMaxBpm(lthrBpm: Int): Float = if (lthrBpm > 0) 1.1f * lthrBpm else 210f

internal const val CHART_BPM_MIN = 50f
internal const val CHART_MAX_CADENCE = 140f

/** Blends a zone's bright accent color toward near-black (dark themes) or a light neutral (light
 *  themes) so bar fills read as muted background, never as bright as the power/HR/cadence trace
 *  lines drawn on top of them. Blending toward near-black on a light theme produced muddy,
 *  low-contrast olive/navy bars — the same math reads as "muted background" only when the
 *  surface itself is dark. */
internal fun mutedZoneColor(zoneColor: Color, active: Boolean, isLightTheme: Boolean = false): Color {
    val base = if (isLightTheme) Color(0xFFDAD4C0) else Color(0xFF14171D)
    val t = if (active) 0.62f else 0.35f
    return lerp(base, zoneColor, t)
}

/** The power trace line (and anything meant to match it — axis labels, legends, tooltips): white
 *  reads fine on every dark theme's near-black background but disappears on a light one, so light
 *  themes get a distinct, saturated purple instead. */
internal fun powerTraceColor(isLightTheme: Boolean): Color = if (isLightTheme) Color(0xFFA000FF) else Color.White

/** Steps at or after [currentStepIndex] reflect a live intensity change; earlier ones are
 *  history and stay as originally ridden. */
internal fun displayWatts(rawWatts: Int, stepIndex: Int, currentStepIndex: Int, intensityPercent: Int): Int =
    if (stepIndex >= currentStepIndex) (rawWatts * intensityPercent / 100f).roundToInt() else rawWatts

@Composable
private fun WorkoutProfileChart(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    controlMode: ControlMode,
    maxGradePercent: Float,
    routeProfile: RouteProfile?,
    routeDistanceM: Float,
    zoom: ChartZoom,
    onZoomChange: (ChartZoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Divide by (1 - headroom) so the ceiling reaches only that fraction of the height, leaving
    // CHART_TOP_HEADROOM free at the top. A bar or trace above the ceiling (e.g. a high intensity
    // multiplier pushed it past 1.8x FTP) is simply clipped rather than rescaling the whole axis —
    // that's the point: the axis stays put so intensity changes are visible.
    val wattsScale = chartMaxWatts(ftpWatts) / (1f - CHART_TOP_HEADROOM)
    // Right (HR) axis lines up with the left (watts) axis's 4 gridlines, both topping out with
    // the same headroom fraction free above them.
    val bpmMin = CHART_BPM_MIN
    val bpmRange = chartMaxBpm(lthrBpm) - bpmMin
    val cadScale = CHART_MAX_CADENCE / (1f - CHART_TOP_HEADROOM)

    // zoom is hoisted to WorkoutScreen's own caller (see its chartZoom param) rather than kept
    // locally here, so it survives the active-workout pager disposing/recomposing this chart on
    // every swipe to Vitals and back instead of resetting to FULL each time.
    // Not keyed on `steps`: a stale index left over from a since-replaced workout plan simply
    // fails the `selIndex in steps.indices` guard below and stops rendering — no need to key a
    // remember() to reset it, which was instead causing the state to reset on every recomposition.
    var selectedStepIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val latestInputs = rememberUpdatedState(
        ChartInputs(steps, currentStepIndex, totalElapsedSec, totalDurationSec, intensityPercent, ftpWatts, controlMode),
    )
    // zoom/onZoomChange are read inside the pointerInput(Unit) gesture coroutine below, which
    // launches once and never restarts — without rememberUpdatedState it would keep seeing the
    // zoom value (and callback) from whenever that coroutine first launched, so every tap after
    // the first recomputed .next() from the same stale zoom and the UI appeared to "freeze" after
    // one change.
    val latestZoom = rememberUpdatedState(zoom)
    val latestOnZoomChange = rememberUpdatedState(onZoomChange)
    // Canvas{} below is a plain DrawScope lambda, not @Composable, so it can't read these
    // theme-reactive tokens itself — resolved here in the composable body and captured instead.
    val onSurfaceColor = ErgOnSurface
    val surface2Color = ErgSurface2
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val powerColor = powerTraceColor(isLightTheme)

    Column(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .pointerInput(Unit) {
            // detectTapGestures cancels the whole gesture if the finger drifts past touch slop
            // before lifting — fine for a big, imprecise target like the zoom band, but a tap
            // aimed at one specific interval column is exactly the kind of "precise" press that
            // drifts a pixel or two and silently gets cancelled, producing no callback at all.
            // Track down-then-up directly instead, which only cares where the finger lifted.
            awaitEachGesture {
                awaitFirstDown()
                // A swipe that starts on this chart (e.g. between Workout and Vitals, or between
                // the 5 main screens) can get claimed by the enclosing HorizontalPager partway
                // through, without ever delivering a clean up/cancel back to this gesture — which
                // otherwise leaves awaitEachGesture parked here forever, silently swallowing every
                // later tap. The timeout bounds how long one stuck gesture can block the next.
                val up = withTimeoutOrNull(GESTURE_UP_TIMEOUT_MS) { waitForUpOrCancellation() } ?: return@awaitEachGesture
                val offset = up.position
                // Top 75% of the chart cycles zoom; the bottom quarter selects whatever interval
                // sits at that x for its tooltip, even where that particular bar falls short of
                // the tap — matching TrainerDay, where you don't have to land precisely on a
                // short bar's tip. The zoom cycle itself doesn't need any step/duration data, so
                // it's checked BEFORE the no-plan-loaded guard below — zoom must still work on an
                // empty/no-plan chart (e.g. testing with no workout loaded), only the interval
                // tooltip genuinely needs real steps.
                if (offset.y < size.height * CHART_ZOOM_TAP_FRACTION) {
                    selectedStepIndex = null
                    latestOnZoomChange.value(latestZoom.value.next())
                    return@awaitEachGesture
                }
                val inputs = latestInputs.value
                // SLO has no workout bars to tap — the loaded plan's steps are irrelevant to
                // what's actually being sent to the trainer, so a tooltip built from them would
                // just show stale/misleading power numbers.
                if (inputs.controlMode == ControlMode.SLO) return@awaitEachGesture
                if (inputs.steps.isEmpty() || inputs.totalDurationSec <= 0) return@awaitEachGesture
                val (windowStart, windowEnd) = computeChartWindow(latestZoom.value, inputs.totalElapsedSec, inputs.totalDurationSec)
                val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
                val tSec = windowStart + ((offset.x / size.width) * windowLen).roundToInt()
                val tappedIndex = stepIndexAt(tSec, inputs.steps)
                selectedStepIndex = if (tappedIndex != null && selectedStepIndex != tappedIndex) tappedIndex else null
            }
        },
    ) {
        // SLO (manual or GPX-route-driven) has no fixed plan, so steps can legitimately be
        // empty there — every other mode still needs a real plan loaded to draw anything. A GPX
        // route's own total distance is known immediately on load, unlike totalDurationSec (which
        // only starts growing once the ride actually ticks) — so a route bypasses the
        // totalDurationSec gate too, to show its full profile before the rider presses Play.
        if ((totalDurationSec <= 0 && routeProfile == null) || (steps.isEmpty() && controlMode != ControlMode.SLO)) return@Canvas
        val w = size.width
        val h = size.height

        val (windowStart, windowEnd) = computeChartWindow(zoom, totalElapsedSec, totalDurationSec)
        val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
        fun xAt(t: Int): Float = w * (t - windowStart) / windowLen.toFloat()
        fun yWatts(watts: Int): Float = h - h * (watts.toFloat() / wattsScale).coerceIn(0f, 1f)
        fun yBpm(bpm: Int): Float = h - h * (((bpm - bpmMin) / bpmRange) * (1f - CHART_TOP_HEADROOM)).coerceIn(0f, 1f)
        fun yCad(rpm: Int): Float = h - h * (rpm.toFloat() / cadScale).coerceIn(0f, 1f)

        // A GPX route's whole profile is known the moment it's loaded — unlike a time-based plan,
        // whose total duration depends on how fast the rider goes — so it's drawn in full right
        // away, on a distance x axis, instead of growing in from recorded history like everything
        // else on this chart. Manual SLO (no route) has no such total, so it keeps the original
        // "recorded history only" behavior.
        val routeActive = controlMode == ControlMode.SLO && routeProfile != null && routeProfile.totalDistanceM > 0f
        fun xAtDist(d: Float): Float = w * (d / routeProfile!!.totalDistanceM).coerceIn(0f, 1f)

        if (controlMode == ControlMode.SLO) {
            // SLO has no fixed power plan to draw as bars — the loaded workout's steps are
            // ignored by the trainer in this mode (see WorkoutExecutor.pushTargetForCurrentStep).
            if (routeActive) {
                // GPX route: draw the route's real elevation profile — the instantaneous grade is
                // already shown numerically in the Grade tile, so the chart itself shows the
                // climb/descent shape instead, scaled to the route's own min/max elevation.
                val route = routeProfile!!
                val elevations = route.elevationsM
                if (elevations.size >= 2) {
                    val minEle = elevations.min()
                    val maxEle = elevations.max()
                    val eleRange = (maxEle - minEle).coerceAtLeast(1f)
                    fun yEle(ele: Float): Float = h - h * ((ele - minEle) / eleRange) * (1f - CHART_TOP_HEADROOM)
                    val segLen = route.segmentLengthM
                    val points = elevations.mapIndexed { i, ele -> Offset(xAtDist(i * segLen), yEle(ele)) }
                    val areaPath = Path().apply {
                        moveTo(points.first().x, h)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, h)
                        close()
                    }
                    drawPath(path = areaPath, color = ErgModeSlo.copy(alpha = 0.35f))
                    drawPoints(points = points, pointMode = PointMode.Polygon, color = ErgModeSlo, strokeWidth = 3f, cap = StrokeCap.Round)
                }
                // "You are here" — distance-based, since the x axis is distance in this mode (the
                // usual elapsed-time progress line below is skipped for the same reason).
                val hereX = xAtDist(routeDistanceM)
                drawLine(color = ErgProgressLine, start = Offset(hereX, 0f), end = Offset(hereX, h), strokeWidth = 2.5f)
            } else {
                // Manual SLO (no route, no elevation data) — draw the recorded grade history
                // instead, as a filled area around a 0% zero line.
                val zeroY = h / 2f
                drawLine(color = onSurfaceColor.copy(alpha = 0.18f), start = Offset(0f, zeroY), end = Offset(w, zeroY), strokeWidth = 1.5f)
                val maxGrade = maxGradePercent.coerceAtLeast(0.1f)
                fun yGrade(grade: Float): Float =
                    zeroY - (grade / maxGrade).coerceIn(-1f, 1f) * zeroY * (1f - CHART_TOP_HEADROOM)
                val gradeSamples = samples.filter { it.tSec in windowStart..windowEnd && it.gradePercent != null }
                if (gradeSamples.size >= 2) {
                    val areaPath = Path().apply {
                        moveTo(xAt(gradeSamples.first().tSec), zeroY)
                        gradeSamples.forEach { s -> lineTo(xAt(s.tSec), yGrade(s.gradePercent!!)) }
                        lineTo(xAt(gradeSamples.last().tSec), zeroY)
                        close()
                    }
                    drawPath(path = areaPath, color = ErgModeSlo.copy(alpha = 0.35f))
                    drawPoints(
                        points = gradeSamples.map { Offset(xAt(it.tSec), yGrade(it.gradePercent!!)) },
                        pointMode = PointMode.Polygon,
                        color = ErgModeSlo,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        } else {
            var acc = 0
            var previousEndBarHeight = 0f
            steps.forEachIndexed { index, step ->
                val stepStart = acc
                val stepEnd = acc + step.durationSec
                acc = stepEnd
                if (stepEnd < windowStart || stepStart > windowEnd) return@forEachIndexed

                val dispStart = displayWatts(step.startWatts, index, currentStepIndex, intensityPercent)
                val dispEnd = displayWatts(step.endWatts, index, currentStepIndex, intensityPercent)
                // A ramp's start/end differ, so the bar is a sloped trapezoid instead of a flat
                // rectangle — a steady step just has startBarHeight == endBarHeight, which draws the
                // same flat shape as before. These are the TRUE heights at the step's real
                // boundaries, used below only for the inter-step divider bookkeeping.
                val startBarHeight = h * (dispStart.toFloat() / wattsScale).coerceIn(0.05f, 1f)
                val endBarHeight = h * (dispEnd.toFloat() / wattsScale).coerceIn(0.05f, 1f)

                // When a zoomed-in window truncates this step (a ramp scrolled partway off-screen),
                // the trapezoid's visible edge needs the ramp's actual interpolated value at that
                // truncation point, not the value at the step's real start/end — otherwise the edge
                // doesn't line up with the power line it's meant to sit under (only noticeable at
                // 20min/5min zoom, not at "fit" where every step boundary is already on-screen).
                val durationSec = step.durationSec.coerceAtLeast(1)
                fun wattsAt(tSec: Int): Float {
                    val frac = (tSec - stepStart).toFloat() / durationSec
                    return dispStart + (dispEnd - dispStart) * frac
                }
                val visibleStart = stepStart.coerceAtLeast(windowStart)
                val visibleEnd = stepEnd.coerceAtMost(windowEnd)
                val x0 = xAt(visibleStart).coerceIn(0f, w)
                val x1 = xAt(visibleEnd).coerceIn(0f, w)
                val drawStartHeight = if (visibleStart == stepStart) {
                    startBarHeight
                } else {
                    h * (wattsAt(visibleStart) / wattsScale).coerceIn(0.05f, 1f)
                }
                val drawEndHeight = if (visibleEnd == stepEnd) {
                    endBarHeight
                } else {
                    h * (wattsAt(visibleEnd) / wattsScale).coerceIn(0.05f, 1f)
                }

                val zone = zoneFor(max(dispStart, dispEnd), ftpWatts, onSurfaceColor)
                val color = mutedZoneColor(zone.color, active = index == currentStepIndex, isLightTheme = isLightTheme)
                val barPath = Path().apply {
                    moveTo(x0, h - drawStartHeight)
                    lineTo(x1, h - drawEndHeight)
                    lineTo(x1, h)
                    lineTo(x0, h)
                    close()
                }
                drawPath(path = barPath, color = color)

                // Thin light-blue divider between consecutive intervals — stops at the top of the
                // taller of the two bar edges meeting at this boundary instead of running into the
                // empty area above them.
                if (stepStart in windowStart..windowEnd && index > 0) {
                    val dividerHeight = max(previousEndBarHeight, startBarHeight)
                    drawLine(color = ErgDivider, start = Offset(x0, h - dividerHeight), end = Offset(x0, h), strokeWidth = 0.75f)
                }
                previousEndBarHeight = endBarHeight
            }
        }

        // Live traces recorded during the workout: cadence under HR under power. Plotted by
        // distance (against the full route) on a GPX route, by elapsed time otherwise — xOf picks
        // the mapping that matches whichever x axis this chart is currently drawing.
        val visibleSamples = if (routeActive) samples.filter { it.distanceM != null } else samples.filter { it.tSec in windowStart..windowEnd }
        fun xOf(s: SamplePoint): Float = if (routeActive) xAtDist(s.distanceM!!) else xAt(s.tSec)
        if (visibleSamples.size >= 2) {
            val cadPoints = visibleSamples.mapNotNull { s -> s.cadenceRpm?.let { Offset(xOf(s), yCad(it)) } }
            val hrPoints = visibleSamples.mapNotNull { s -> s.hrBpm?.let { Offset(xOf(s), yBpm(it)) } }
            val powerPoints = visibleSamples.map { Offset(xOf(it), yWatts(it.watts)) }

            if (cadPoints.size >= 2) {
                drawPoints(
                    points = cadPoints,
                    pointMode = PointMode.Polygon,
                    color = ErgCadenceLine,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            if (hrPoints.size >= 2) {
                drawPoints(
                    points = hrPoints,
                    pointMode = PointMode.Polygon,
                    color = ErgHrLine,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
            if (powerPoints.size >= 2) {
                drawPoints(
                    points = powerPoints,
                    pointMode = PointMode.Polygon,
                    color = powerColor,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Progress line: a thin light-blue line at full chart height marking elapsed time —
        // pinned at the window's left edge until computeChartWindow starts scrolling to keep it
        // at its scroll threshold position (see ChartZoom/computeChartWindow). Skipped for a GPX
        // route, which already drew its own distance-based "you are here" line above.
        if (!routeActive && totalElapsedSec in windowStart..windowEnd) {
            val progressX = xAt(totalElapsedSec).coerceIn(0f, w)
            drawLine(color = ErgProgressLine, start = Offset(progressX, 0f), end = Offset(progressX, h), strokeWidth = 2.5f)
        }

        // Axis gridlines + labels: watts on the left, heart rate on the right, sharing the same
        // 4 height positions — both derived from the current FTP/LTHR ceilings instead of fixed
        // numbers, so the printed tick values always match what the axes actually scale to.
        val maxWatts = chartMaxWatts(ftpWatts)
        val wattsTicks = listOf(0.25f, 0.5f, 0.75f, 1f).map { (maxWatts * it).roundToInt() }
        val bpmTicks = listOf(0.25f, 0.5f, 0.75f, 1f).map { (bpmMin + bpmRange * it).roundToInt() }
        wattsTicks.forEachIndexed { i, watts ->
            val y = yWatts(watts)
            drawLine(color = onSurfaceColor.copy(alpha = 0.12f), start = Offset(0f, y), end = Offset(w, y), strokeWidth = 2.25f)
            val wattsLabelResult = textMeasurer.measure("$watts", TextStyle(fontSize = 10.sp, color = onSurfaceColor.copy(alpha = 0.85f)))
            drawText(wattsLabelResult, topLeft = Offset(4.dp.toPx(), y - wattsLabelResult.size.height - 2f))
            val bpmLabelResult = textMeasurer.measure("${bpmTicks[i]}", TextStyle(fontSize = 10.sp, color = ErgHrLine))
            drawText(bpmLabelResult, topLeft = Offset(w - bpmLabelResult.size.width - 4.dp.toPx(), y - bpmLabelResult.size.height - 2f))
        }

        // Selected interval (tapped on its bar): full-height highlight + a duration/watts/zone
        // tooltip, drawn last so it sits on top of everything else.
        val selIndex = selectedStepIndex
        if (selIndex != null && selIndex in steps.indices) {
            val (selStart, selEnd) = stepTimeRange(selIndex, steps)
            if (selEnd >= windowStart && selStart <= windowEnd) {
                val sx0 = xAt(selStart).coerceIn(0f, w)
                val sx1 = xAt(selEnd).coerceIn(0f, w)
                drawRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(sx0, 0f),
                    size = Size((sx1 - sx0).coerceAtLeast(1f), h),
                )

                val selStep = steps[selIndex]
                val selDispStart = displayWatts(selStep.startWatts, selIndex, currentStepIndex, intensityPercent)
                val selDispEnd = displayWatts(selStep.endWatts, selIndex, currentStepIndex, intensityPercent)
                val selZone = zoneFor(max(selDispStart, selDispEnd), ftpWatts, onSurfaceColor)
                val durationText = formatTime(selStep.durationSec)
                val wattsText = "${wattsLabel(selDispStart, selDispEnd)} (${selZone.label})"

                val paddingH = 10.dp.toPx()
                val gap = 6.dp.toPx()
                val durationWidth = textMeasurer.measure(durationText, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)).size.width + paddingH * 2
                val wattsWidth = textMeasurer.measure(wattsText, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)).size.width + paddingH * 2
                val totalWidth = durationWidth + gap + wattsWidth
                val margin = 6.dp.toPx()
                var pillX = (sx0 + margin).coerceIn(margin, (w - totalWidth - margin).coerceAtLeast(margin))
                val pillY = 8.dp.toPx()

                pillX += drawPill(durationText, pillX, pillY, surface2Color, onSurfaceColor, textMeasurer) + gap
                drawPill(wattsText, pillX, pillY, selZone.color, Color.Black, textMeasurer)
            }
        }
    }
    ChartTimeAxis(
        zoom = zoom,
        totalElapsedSec = totalElapsedSec,
        totalDurationSec = totalDurationSec,
        // routeProfile is non-null only in SLO (see WorkoutExecutor.loadRoute), so no extra
        // controlMode check is needed here.
        routeProfile = routeProfile,
    )
    }
}

/** Minute labels reclaimed from the chart's own weight(1f) allocation below it — everything else
 *  about the chart (scale, colors, zoom-tap area, traces, tooltip) is untouched; this only adds
 *  the axis strip and, by taking a fixed height for it, shortens the Canvas above by that much. */
@Composable
internal fun ChartTimeAxis(
    zoom: ChartZoom,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    // Non-null only for a GPX route in SLO — draws km ticks across the whole known route
    // distance instead of minute ticks across the recorded-so-far time window (see
    // WorkoutProfileChart's own identical fork for why).
    routeProfile: RouteProfile? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = ErgOnSurface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        val w = size.width
        val route = routeProfile
        if (route != null && route.totalDistanceM > 0f) {
            val totalKm = route.totalDistanceM / 1000f
            val intervalKm = distanceLabelIntervalKm(totalKm)
            val tickHeight = 4.dp.toPx()
            val tickToLabelGap = 2.dp.toPx()
            var km = intervalKm
            while (km < totalKm) {
                val x = w * (km / totalKm)
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, tickHeight),
                    strokeWidth = 1.5.dp.toPx(),
                )
                val label = textMeasurer.measure(formatAxisKm(km), TextStyle(fontSize = 10.sp, color = onSurfaceColor.copy(alpha = 0.6f)))
                drawText(
                    label,
                    topLeft = Offset((x - label.size.width / 2f).coerceIn(0f, w - label.size.width), tickHeight + tickToLabelGap),
                )
                km += intervalKm
            }
            return@Canvas
        }
        if (totalDurationSec <= 0) return@Canvas
        val (windowStart, windowEnd) = computeChartWindow(zoom, totalElapsedSec, totalDurationSec)
        val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
        fun xAt(t: Int): Float = w * (t - windowStart) / windowLen.toFloat()

        val tickHeight = 4.dp.toPx()
        val tickToLabelGap = 2.dp.toPx()
        val intervalSec = axisLabelIntervalMin(zoom, totalDurationSec) * 60
        var tSec = ((windowStart + intervalSec - 1) / intervalSec) * intervalSec
        if (tSec <= windowStart) tSec += intervalSec
        while (tSec < windowEnd) {
            val x = xAt(tSec)
            // Matches TrainerDay: a short tick marks the exact instant each label refers to,
            // rather than leaving the number to imply its own position. strokeWidth is in dp
            // (not a raw 1f px, which renders sub-dp and nearly invisible on a 3x-density screen).
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.6f),
                start = Offset(x, 0f),
                end = Offset(x, tickHeight),
                strokeWidth = 1.5.dp.toPx(),
            )
            val label = textMeasurer.measure("${tSec / 60}", TextStyle(fontSize = 10.sp, color = onSurfaceColor.copy(alpha = 0.6f)))
            drawText(
                label,
                topLeft = Offset((x - label.size.width / 2f).coerceIn(0f, w - label.size.width), tickHeight + tickToLabelGap),
            )
            tSec += intervalSec
        }
    }
}

internal data class PowerZone(val label: String, val color: Color)

/**
 * Andrew Coggan's 7-level power training zones, as %FTP: Active Recovery, Endurance, Tempo,
 * Lactate Threshold, VO2max, Anaerobic Capacity, Neuromuscular Power.
 */
internal val POWER_ZONES = listOf(
    0.55f to PowerZone("Z1", Color(0xFFA3AEC0)),
    0.75f to PowerZone("Z2", ErgBelowTarget),
    0.90f to PowerZone("Z3", ErgAccent),
    1.05f to PowerZone("Z4", Color(0xFFE6C15A)),
    1.20f to PowerZone("Z5", Color(0xFFE08A3E)),
    1.50f to PowerZone("Z6", Color(0xFFB23A5A)),
)
internal val ZONE_MAX = PowerZone("Z7", Color(0xFFA855F7))

internal fun zoneFor(watts: Int, ftpWatts: Int, noDataColor: Color): PowerZone {
    if (ftpWatts <= 0) return PowerZone("--", noDataColor)
    val pct = watts.toFloat() / ftpWatts
    return POWER_ZONES.firstOrNull { pct <= it.first }?.second ?: ZONE_MAX
}

/**
 * HR zones, as %LTHR — deliberately NOT the same breakpoints as [POWER_ZONES]: heart rate has a
 * physiological ceiling (it can't spike to 150%+ threshold the way a sprint's power can), so
 * reusing the power breakpoints here would mean the top zones could never actually light up.
 * These match the rider's own Intervals.icu HR zone thresholds instead. Reuses [POWER_ZONES]'
 * colors by zone number, so a "Z4" reads the same warmth in both places even though the two
 * zone systems measure different things at different percentages.
 */
internal val HR_ZONES = listOf(
    0.80f to POWER_ZONES[0].second,
    0.89f to POWER_ZONES[1].second,
    0.93f to POWER_ZONES[2].second,
    0.99f to POWER_ZONES[3].second,
    1.02f to POWER_ZONES[4].second,
    1.05f to POWER_ZONES[5].second,
)

internal fun zoneForHr(bpm: Int, lthrBpm: Int, noDataColor: Color): PowerZone {
    if (lthrBpm <= 0) return PowerZone("--", noDataColor)
    val pct = bpm.toFloat() / lthrBpm
    return HR_ZONES.firstOrNull { pct <= it.first }?.second ?: ZONE_MAX
}

private fun wattsLabel(startWatts: Int, endWatts: Int): String =
    if (startWatts == endWatts) "$endWatts W" else "$startWatts–$endWatts W"

/** Same %-of-interval as [wattsLabel], reinterpreted against LTHR instead of FTP — used only in
 *  HR+, so the zone tag next to it still always reflects the power zone (see [zoneFor] call in
 *  [IntervalDetailBlock]), not a heart-rate one. */
private fun bpmLabel(startWatts: Int, endWatts: Int, ftpWatts: Int, lthrBpm: Int): String {
    val startBpm = (lthrBpm * startWatts / ftpWatts.toFloat()).roundToInt()
    val endBpm = (lthrBpm * endWatts / ftpWatts.toFloat()).roundToInt()
    return if (startBpm == endBpm) "$endBpm bpm" else "$startBpm–$endBpm bpm"
}

@Composable
private fun IntervalDetailsSection(
    current: WorkoutStep?,
    next: WorkoutStep?,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    controlMode: ControlMode,
) {
    if (current == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErgSurface, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(50))
                .background(ErgLiveAccent),
        )
        IntervalDetailBlock(
            label = "Now",
            step = current,
            ftpWatts = ftpWatts,
            lthrBpm = lthrBpm,
            intensityPercent = intensityPercent,
            controlMode = controlMode,
            accentColor = ErgLiveAccent,
            modifier = Modifier.weight(1f),
        )
        if (next != null) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(ErgOnSurface.copy(alpha = 0.15f)),
            )
            IntervalDetailBlock(
                label = "Next",
                step = next,
                ftpWatts = ftpWatts,
                lthrBpm = lthrBpm,
                intensityPercent = intensityPercent,
                controlMode = controlMode,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Layout and zone tag are identical in ERG and HR+ — see [zoneFor], always power-based — only
 *  the numeric value's unit switches from watts to the LTHR-derived bpm in HR+. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntervalDetailBlock(
    label: String,
    step: WorkoutStep,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    controlMode: ControlMode,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val scaledStart = (step.startWatts * intensityPercent / 100f).roundToInt()
    val scaledEnd = (step.endWatts * intensityPercent / 100f).roundToInt()
    val zone = zoneFor(scaledEnd, ftpWatts, ErgOnSurface)
    val valueLabel = if (controlMode == ControlMode.HR_PLUS && ftpWatts > 0) {
        bpmLabel(scaledStart, scaledEnd, ftpWatts, lthrBpm)
    } else {
        wattsLabel(scaledStart, scaledEnd)
    }
    Row(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor ?: ErgOnSurface, maxLines = 1)
        // Fixed interval duration, not a live countdown — "Now" used to show its step's
        // remaining time ticking down every second, the only number on this row that changed
        // while everything else (label, zone chip, "Next") stayed put.
        Text(
            formatMinSec(step.durationSec),
            style = MaterialTheme.typography.bodyMedium,
            color = accentColor ?: Color.Unspecified,
            maxLines = 1,
        )
        // The label/time/zone chip are always short and fixed-width; the value is the one piece
        // that can genuinely run long (a three-digit bpm range like "150–220 bpm" is wider than
        // any watt range ever was). weight(1f) with the default fill=true claims ALL remaining
        // space after the fixed pieces are measured — not just what the text needs — so the zone
        // chip that follows always lands at the same fixed spot (the block's own right edge) no
        // matter how short or long the value text is, instead of trailing right behind it.
        // basicMarquee scrolls it in a continuous loop (iterations default to Int.MAX_VALUE)
        // instead of ellipsizing, so the full range stays readable without shrinking the font.
        // velocity halved from the 30.dp/s default; delayMillis=5_000 is the pause before each
        // pass (also before the very first one).
        //
        // Reported (repeatedly) to get visually stuck/garbled after a chart zoom tap elsewhere on
        // this screen: basicMarquee's own animation coroutine is a Modifier.Node internal to
        // Compose Foundation, with no public handle this app can reach in to detect or recover a
        // wedged instance directly. Instead of chasing that internal state, marqueeResetKey below
        // forces this Text (and the marquee node attached to it) to be fully disposed and
        // recreated — on every "Now"/"Next" step change (a fresh node per interval, instead of one
        // that persists stale scroll state across them) and, as a bound on any other way it could
        // still wedge, on a periodic timer — so a freeze from any cause self-heals within one
        // period instead of lasting the rest of the ride.
        var marqueeResetKey by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(MARQUEE_SELF_HEAL_INTERVAL_MS)
                marqueeResetKey++
            }
        }
        key(label, step.durationSec, step.startWatts, step.endWatts, marqueeResetKey) {
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(delayMillis = 5_000, velocity = 15.dp),
            )
        }
        Text(
            zone.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            maxLines = 1,
            modifier = Modifier
                .background(zone.color, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/**
 * Main action button cycles Start -> Pause -> Stop. Once paused, this button's only action is
 * Stop (behind a confirm dialog, via [onExit]) — the button itself never offers a Resume, but
 * pedaling again resumes the ride on its own (see WorkoutExecutor's pedaling collector), so
 * Stop here really does mean "end the session", not just "step off the bike for a moment".
 */
@Composable
private fun ControlsRow(
    hasWorkout: Boolean,
    isRunning: Boolean,
    hasStarted: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    // Landscape's data column has far less height to spend on controls than portrait does, so it
    // asks for the mini scale here instead — same pills, just smaller (close to IntensityRow's
    // own 38.4dp/28.8dp scale, a notch down).
    compact: Boolean = false,
) {
    val isPaused = hasStarted && !isRunning
    val mainIcon = when { isRunning -> Icons.Filled.Pause; isPaused -> Icons.Filled.Stop; else -> Icons.Filled.PlayArrow }
    val mainAction = when { isRunning -> onPause; isPaused -> onExit; else -> onPlay }
    val mainDescription = when { isRunning -> "Pause"; isPaused -> "Stop"; else -> "Start" }
    val mainContainerColor = if (isPaused) ErgAboveTarget else ErgSurface2
    val pillHeight = if (compact) 34.dp else 48.dp
    val pillIconSize = if (compact) 18.dp else 28.dp
    val addSkipWidth = if (compact) 44.dp else 60.dp

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        PillIconButton(
            icon = mainIcon,
            contentDescription = mainDescription,
            onClick = mainAction,
            enabled = hasWorkout,
            containerColor = mainContainerColor,
            iconSize = pillIconSize,
            height = pillHeight,
            modifier = Modifier.weight(1f),
            // Plain tap in all 3 states (Start/Pause/Stop) — only +5min and Skip require the 2s
            // hold now.
        )
        PillIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add 5 minutes to the interval",
            onClick = onExtend,
            enabled = hasWorkout,
            iconSize = pillIconSize,
            height = pillHeight,
            modifier = Modifier.width(addSkipWidth),
            requireLongPressMillis = 1000L,
        )
        PillIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Skip step",
            onClick = onSkip,
            enabled = hasWorkout,
            iconSize = pillIconSize,
            height = pillHeight,
            modifier = Modifier.weight(1f),
            requireLongPressMillis = 1000L,
        )
    }
}

@Composable
private fun PillIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = ErgSurface2,
    iconColor: Color = ErgOnSurface,
    // The ERG% pill's own up/down arrows stay at the larger default; only the Start/+5/Skip
    // controls below pass a smaller size.
    iconSize: Dp = 36.dp,
    height: Dp = 48.dp,
    // Start/Pause/Stop, +5min and Skip pass a duration here so a brush of the screen mid-ride
    // can't trigger them — only a deliberate, held-down press does. The intensity row's up/down
    // arrows leave this null (immediate tap), since nudging those a few times is routine.
    requireLongPressMillis: Long? = null,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .then(
                if (requireLongPressMillis != null) {
                    Modifier.requireLongPress(enabled, requireLongPressMillis, onClick)
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) iconColor else iconColor.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Fires [onActivate] only once the pointer has been held down for [durationMillis] without
 *  lifting — a normal tap, or a press released early, does nothing. [enabled] mirrors the
 *  button's own enabled state so a disabled control stays fully inert rather than still counting
 *  down a press that can never do anything. */
internal fun Modifier.requireLongPress(
    enabled: Boolean,
    durationMillis: Long,
    onActivate: () -> Unit,
): Modifier = pointerInput(enabled, durationMillis) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        awaitFirstDown()
        val releasedEarly = withTimeoutOrNull(durationMillis) { waitForUpOrCancellation() }
        if (releasedEarly == null) {
            onActivate()
            waitForUpOrCancellation()
        }
    }
}

/** The ERG/HR+/SLO switch lives inside this same pill instead of a row of its own, so the toggle
 *  costs no extra vertical space: the ↓/↑ arrows and the pill's overall height are unchanged,
 *  only the pill's content grows a tappable mode tag to the left of the percentage. In SLO, the
 *  same ↓/↑ arrows and percentage box instead show/adjust the simulated grade. */
@Composable
private fun IntensityRow(
    intensityPercent: Int,
    gradePercent: Float,
    controlMode: ControlMode,
    enabled: Boolean,
    onDecreaseIntensity: () -> Unit,
    onIncreaseIntensity: () -> Unit,
    onDecreaseGrade: () -> Unit,
    onIncreaseGrade: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val isSlo = controlMode == ControlMode.SLO
    val isHrPlus = controlMode == ControlMode.HR_PLUS
    val modeColor = when {
        isSlo -> ErgModeSlo
        isHrPlus -> ErgHrPlus
        else -> ErgModeErg
    }
    val modeLabel = when {
        isSlo -> "SLO"
        isHrPlus -> "HR+"
        else -> "ERG"
    }
    val onDecrease = if (isSlo) onDecreaseGrade else onDecreaseIntensity
    val onIncrease = if (isSlo) onIncreaseGrade else onIncreaseIntensity
    val displayText = if (isSlo) {
        (if (gradePercent == 0f) "0.0" else "%+.1f".format(gradePercent)) + "%"
    } else {
        "$intensityPercent%"
    }
    // The pill's background is now fixed — direction lives entirely in a small triangle next to
    // the percentage instead of recoloring the whole pill: ▼ blue below target, ▲ amber above,
    // no glyph exactly at 100% (or, in SLO, exactly flat).
    val glyphUp = if (isSlo) gradePercent > 0.05f else intensityPercent > 100
    val glyphDown = if (isSlo) gradePercent < -0.05f else intensityPercent < 100
    val intensityGlyph = when {
        glyphUp -> "▲"
        glyphDown -> "▼"
        else -> null
    }
    val intensityGlyphColor = if (glyphUp) ErgIntensityUpGlyph else ErgIntensityDownGlyph
    // The percentage/grade box's own background (ErgSurface2) is theme-reactive — white text on
    // it reads fine on every dark theme but disappears on a light one, so light themes get dark
    // text instead. Only this value needs it: the mode tag's text sits on a fixed saturated
    // color (ErgModeErg/HrPlus/Slo), never affected by light/dark theme.
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val intensityValueColor = if (isLightTheme) Color.Black else Color.White
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (isSlo) "Decrease grade" else "Decrease intensity",
            onClick = onDecrease,
            enabled = enabled,
            iconSize = 28.8.dp,
            height = 38.4.dp,
            modifier = Modifier.width(40.dp),
        )
        // Mode tag is a fixed-size circle and the triangle a fixed-size box at the opposite end —
        // same approach as Vitals' own CompactControlRow — instead of a text-width-measured
        // capsule mirrored by a computed triangle offset: that math fell apart once this pill got
        // narrow (landscape's tighter data column), landing the triangle on top of the tag instead
        // of past the far edge.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.4.dp)
                .clip(RoundedCornerShape(50))
                .background(ErgSurface2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(modeColor)
                    .requireLongPress(enabled, 1000L, onToggleMode),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Same font size as the percentage next to it.
                    modeLabel,
                    fontSize = 14.1.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    displayText,
                    fontSize = 14.1.sp,
                    fontWeight = FontWeight.Bold,
                    color = intensityValueColor,
                )
            }
            // Same size as the mode circle (balances it so the % box above stays centered in the
            // whole pill) — also where the ▲/▼ direction triangle shows, when intensity/grade
            // isn't flat.
            Box(modifier = Modifier.size(38.4.dp), contentAlignment = Alignment.Center) {
                if (intensityGlyph != null) {
                    // Drawn directly rather than as a "▲"/"▼" glyph: a Text's own vertical
                    // centering centers its font-metrics box (ascent to descent), not the
                    // triangle's actual ink, which sits noticeably higher within that box.
                    IntensityTriangle(
                        pointingUp = glyphUp,
                        color = intensityGlyphColor,
                        sizeDp = 14.4.dp,
                    )
                }
            }
        }
        PillIconButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = if (isSlo) "Increase grade" else "Increase intensity",
            onClick = onIncrease,
            enabled = enabled,
            iconSize = 28.8.dp,
            height = 38.4.dp,
            modifier = Modifier.width(40.dp),
        )
    }
}

/** A solid triangle drawn from scratch instead of a "▲"/"▼" glyph, so its own visual center is
 *  exactly the center of this composable's box — unlike a Text, whose vertical centering is
 *  based on font ascent/descent rather than the triangle's actual ink. */
@Composable
internal fun IntensityTriangle(pointingUp: Boolean, color: Color, sizeDp: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (pointingUp) {
                moveTo(w / 2f, 0f)
                lineTo(w, h)
                lineTo(0f, h)
            } else {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w / 2f, h)
            }
            close()
        }
        drawPath(path, color = color)
    }
}

/** Workout title, tap to choose where to load a plan from — the same 4 sources and labels as
 *  every other screen's "Other sources" menu, just triggered from the title itself instead of a
 *  labeled button, since this screen has no separate space to spare for one. */
@Composable
private fun WorkoutHeader(title: String, otherSources: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(1f, fill = false): bounds the Text to whatever's left once the dropdown icon
            // takes its own space, so maxLines=1/overflow=Ellipsis actually has a width to
            // ellipsize against — unweighted, Text measures at its full intrinsic width and a long
            // title just runs past the screen edge uncut instead of truncating.
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose workout")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            otherSources.forEach { (label, action) ->
                // "Free ride" is a starting point to shape with the existing controls, not a
                // source to pick a workout FROM like every item above it — set off with its own
                // divider instead of blending into the rest of the list.
                if (label == "Free ride") HorizontalDivider()
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

/** m:ss under an hour, h:mm:ss at or past it — switches back to m:ss the moment the value drops
 *  under 3600s again (e.g. TOTAL's remaining-time countdown crossing under an hour left). */
internal fun formatTime(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Always m:ss, even past an hour — used only for the Now/Next row's remaining-time text, which
 *  stays compact since it's tracking a single interval, not the whole workout. */
private fun formatMinSec(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ergrm.trainer.history.SessionSample
import com.ergrm.trainer.history.SessionStats
import com.ergrm.trainer.history.computeSessionStats
import com.ergrm.trainer.ui.theme.ErgAboveTarget
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgBackground
import com.ergrm.trainer.ui.theme.ErgBelowTarget
import com.ergrm.trainer.ui.theme.ErgCadenceLine
import com.ergrm.trainer.ui.theme.ErgDivider
import com.ergrm.trainer.ui.theme.ErgHrLine
import com.ergrm.trainer.ui.theme.ErgHrPlus
import com.ergrm.trainer.ui.theme.ErgModeErg
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.ui.theme.ErgSkinTemp
import com.ergrm.trainer.ui.theme.ErgSurface
import com.ergrm.trainer.ui.theme.ErgSurface2
import com.ergrm.trainer.workout.ControlMode
import com.ergrm.trainer.workout.SamplePoint
import com.ergrm.trainer.workout.WorkoutStep
import kotlin.math.max
import kotlin.math.roundToInt

/** Screen 2 of the active-workout pager (Task #75): a "Vitals & Analytics" view sharing only the
 *  minibar with the dashboard (Screen 1, today's WorkoutScreen) — session summary tiles and two
 *  stacked charts (CORE/SKIN/HSI over time, and the familiar power/HR/cadence chart with a new
 *  cadence axis) instead of the live tiles, controls and single chart Screen 1 already has.
 *
 *  Deliberately simpler than Screen 1's chart in one respect: no tap-to-zoom — this is a glance-
 *  at-the-whole-ride summary view, always showing the full elapsed window. */
@Composable
fun VitalsScreen(viewModel: MainViewModel) {
    val workoutState by viewModel.workoutState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val samples by viewModel.sampleHistory.collectAsState()
    val coreSamples by viewModel.coreTempHistory.collectAsState()
    var showStopConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val stats = remember(samples) {
        computeSessionStats(samples.map { SessionSample(it.tSec, it.watts, it.hrBpm, it.cadenceRpm, it.speedKmh) })
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        SummaryTilesGrid(stats = stats, durationSec = workoutState.totalElapsedSec)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CoreSkinHsiChart(
                samples = coreSamples,
                totalDurationSec = workoutState.totalDurationSec,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            VitalsPowerChart(
                steps = workoutState.steps,
                currentStepIndex = workoutState.currentStepIndex,
                totalDurationSec = workoutState.totalDurationSec,
                samples = samples,
                ftpWatts = settings.ftpWatts,
                lthrBpm = settings.lthrBpm,
                intensityPercent = workoutState.intensityPercent,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        CompactControlRow(
            hasWorkout = workoutState.steps.isNotEmpty(),
            isRunning = workoutState.isRunning,
            hasStarted = workoutState.hasStarted,
            intensityPercent = workoutState.intensityPercent,
            controlMode = workoutState.controlMode,
            onPlay = { viewModel.startWorkout() },
            onPause = { viewModel.pauseWorkout() },
            onStopRequested = { showStopConfirm = true },
            onExtend = { viewModel.extendCurrentInterval() },
            onSkip = { viewModel.skipStep() },
            onDecrease = { viewModel.decreaseIntensity() },
            onIncrease = { viewModel.increaseIntensity() },
            onToggleMode = { viewModel.toggleControlMode() },
        )
    }

    VitalsStopDialogs(
        viewModel = viewModel,
        showStopConfirm = showStopConfirm,
        onStopConfirmChange = { showStopConfirm = it },
        showDiscardConfirm = showDiscardConfirm,
        onDiscardConfirmChange = { showDiscardConfirm = it },
    )
}

/** 9 session-summary stats, as compact as the real tiles allow — no per-tile toggle like
 *  Screen 1's StatTiles, since there's nothing to toggle (each is a single fixed total). */
@Composable
private fun SummaryTilesGrid(stats: SessionStats, durationSec: Int, modifier: Modifier = Modifier) {
    val tiles = listOf(
        "Duration" to formatTime(durationSec),
        "Avg Watts" to "${stats.avgWatts} W",
        "NP" to (stats.normalizedWatts?.let { "$it W" } ?: "--"),
        "Work" to "${stats.totalKj} kJ",
        "Avg HR" to (stats.avgHrBpm?.toString() ?: "--"),
        "Max HR" to (stats.maxHrBpm?.toString() ?: "--"),
        "Avg Cad" to (stats.avgCadenceRpm?.toString() ?: "--"),
        "Avg Speed" to (stats.avgSpeedKmh?.let { "%.1f".format(it) } ?: "--"),
        "Distance" to (stats.distanceKm?.let { "%.1f km".format(it) } ?: "--"),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(ErgSurface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label.uppercase(),
                            fontSize = 7.5.sp,
                            color = ErgOnSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ErgOnSurface, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** CORE/SKIN/HSI over the whole ride. CORE and HSI are drawn one short segment at a time so each
 *  can take the exact same color its tile would show for that instant (same thresholds as
 *  [coreColor]/[hsiColor]) — SKIN stays [ErgSkinTemp] throughout, since its tile has no
 *  thresholds either. CORE and SKIN get their own side-by-side axis columns (same 30–40°C range)
 *  so their live-value pills never collide even when the two readings are close. */
@Composable
private fun CoreSkinHsiChart(samples: List<CoreSamplePoint>, totalDurationSec: Int, modifier: Modifier = Modifier) {
    val last = samples.lastOrNull()
    val coreMin = 30f
    val coreMax = 40f
    val hsiMin = 0f
    val hsiMax = 10f
    fun tempFrac(v: Float) = (1f - (v - coreMin) / (coreMax - coreMin)).coerceIn(0f, 1f)
    fun hsiFrac(v: Float) = (1f - (v - hsiMin) / (hsiMax - hsiMin)).coerceIn(0f, 1f)
    val tempTicks = listOf("40°", "37.5°", "35°", "32.5°")

    Row(modifier = modifier) {
        AxisColumn(
            name = "CORE",
            lineColor = ErgBelowTarget,
            ticks = tempTicks,
            liveValueText = last?.coreTempC?.let { "%.1f°".format(floorToOneDecimal(it)) } ?: "--",
            liveFraction = last?.coreTempC?.let { tempFrac(it) } ?: 1f,
            pillColor = last?.coreTempC?.let { coreColor(floorToOneDecimal(it)) } ?: ErgOnSurface,
        )
        AxisColumn(
            name = "SKIN",
            lineColor = ErgSkinTemp,
            ticks = tempTicks,
            liveValueText = last?.skinTempC?.let { "%.1f°".format(it) } ?: "--",
            liveFraction = last?.skinTempC?.let { tempFrac(it) } ?: 1f,
            pillColor = ErgSkinTemp,
        )
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (samples.size < 2 || totalDurationSec <= 0) return@Canvas
            val w = size.width
            val h = size.height
            fun xAt(t: Int) = w * (t.toFloat() / totalDurationSec)
            val strokeW = 2.dp.toPx()
            for (i in 0 until samples.size - 1) {
                val a = samples[i]
                val b = samples[i + 1]
                val x0 = xAt(a.tSec)
                val x1 = xAt(b.tSec)
                val ac = a.coreTempC
                val bc = b.coreTempC
                if (ac != null && bc != null) {
                    drawLine(coreColor(floorToOneDecimal(bc)), Offset(x0, h * tempFrac(ac)), Offset(x1, h * tempFrac(bc)), strokeWidth = strokeW)
                }
                val askin = a.skinTempC
                val bskin = b.skinTempC
                if (askin != null && bskin != null) {
                    drawLine(ErgSkinTemp, Offset(x0, h * tempFrac(askin)), Offset(x1, h * tempFrac(bskin)), strokeWidth = strokeW)
                }
                val ah = a.heatStrainIndex
                val bh = b.heatStrainIndex
                if (ah != null && bh != null) {
                    drawLine(hsiColor(floorToOneDecimal(bh)), Offset(x0, h * hsiFrac(ah)), Offset(x1, h * hsiFrac(bh)), strokeWidth = strokeW)
                }
            }
        }
        AxisColumn(
            name = "HSI",
            lineColor = ErgAccent,
            ticks = listOf("10", "7.5", "5", "2.5"),
            liveValueText = last?.heatStrainIndex?.let { "%.1f".format(floorToOneDecimal(it)) } ?: "--",
            liveFraction = last?.heatStrainIndex?.let { hsiFrac(it) } ?: 1f,
            pillColor = last?.heatStrainIndex?.let { hsiColor(floorToOneDecimal(it)) } ?: ErgOnSurface,
        )
    }
}

/** The familiar power/HR/cadence chart, always showing the full ride (no zoom — see
 *  [VitalsScreen]'s doc comment) with a new CADENCE axis column added alongside WATTS: cadence
 *  is already plotted today (just like [com.ergrm.trainer.ui.WorkoutProfileChart]'s own
 *  `ErgCadenceLine` trace) but has never had a visible axis of its own until now. */
@Composable
private fun VitalsPowerChart(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    modifier: Modifier = Modifier,
) {
    val wattsScale = chartMaxWatts(ftpWatts) / (1f - CHART_TOP_HEADROOM)
    val maxWatts = chartMaxWatts(ftpWatts)
    val bpmMin = CHART_BPM_MIN
    val bpmRange = chartMaxBpm(lthrBpm) - bpmMin
    val cadScale = CHART_MAX_CADENCE / (1f - CHART_TOP_HEADROOM)
    val last = samples.lastOrNull()

    Row(modifier = modifier) {
        AxisColumn(
            name = "CAD",
            lineColor = ErgCadenceLine,
            ticks = listOf("140", "105", "70", "35"),
            liveValueText = last?.cadenceRpm?.toString() ?: "--",
            liveFraction = last?.cadenceRpm?.let { (1f - it / cadScale).coerceIn(0f, 1f) } ?: 1f,
            pillColor = ErgCadenceLine,
            pillTextColor = Color.White,
        )
        AxisColumn(
            name = "W",
            lineColor = ErgOnSurface,
            ticks = listOf(
                maxWatts.roundToInt().toString(),
                (maxWatts * 0.75f).roundToInt().toString(),
                (maxWatts * 0.5f).roundToInt().toString(),
                (maxWatts * 0.25f).roundToInt().toString(),
            ),
            liveValueText = last?.watts?.toString() ?: "--",
            liveFraction = last?.watts?.let { (1f - it / wattsScale).coerceIn(0f, 1f) } ?: 1f,
            pillColor = ErgAboveTarget,
        )
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (steps.isEmpty() || totalDurationSec <= 0) return@Canvas
            val w = size.width
            val h = size.height
            fun xAt(t: Int) = w * (t.toFloat() / totalDurationSec)
            fun yWatts(watts: Int) = h - h * (watts.toFloat() / wattsScale).coerceIn(0f, 1f)
            fun yBpm(bpm: Int) = h - h * (((bpm - bpmMin) / bpmRange) * (1f - CHART_TOP_HEADROOM)).coerceIn(0f, 1f)
            fun yCad(rpm: Int) = h - h * (rpm.toFloat() / cadScale).coerceIn(0f, 1f)

            var acc = 0
            steps.forEachIndexed { index, step ->
                val stepStart = acc
                val stepEnd = acc + step.durationSec
                acc = stepEnd
                val dispStart = displayWatts(step.startWatts, index, currentStepIndex, intensityPercent)
                val dispEnd = displayWatts(step.endWatts, index, currentStepIndex, intensityPercent)
                val zone = zoneFor(max(dispStart, dispEnd), ftpWatts)
                val x0 = xAt(stepStart)
                val x1 = xAt(stepEnd)
                val path = Path().apply {
                    moveTo(x0, h)
                    lineTo(x0, yWatts(dispStart))
                    lineTo(x1, yWatts(dispEnd))
                    lineTo(x1, h)
                    close()
                }
                drawPath(path, color = zone.color)
                if (index > 0) {
                    drawLine(ErgDivider.copy(alpha = 0.5f), Offset(x0, h * 0.75f), Offset(x0, h), strokeWidth = 1.dp.toPx())
                }
            }
            val hrPoints = samples.mapNotNull { s -> s.hrBpm?.let { Offset(xAt(s.tSec), yBpm(it)) } }
            for (i in 0 until hrPoints.size - 1) {
                drawLine(ErgHrLine, hrPoints[i], hrPoints[i + 1], strokeWidth = 2.dp.toPx())
            }
            val cadPoints = samples.mapNotNull { s -> s.cadenceRpm?.let { Offset(xAt(s.tSec), yCad(it)) } }
            for (i in 0 until cadPoints.size - 1) {
                drawLine(
                    ErgCadenceLine,
                    cadPoints[i],
                    cadPoints[i + 1],
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }
        }
        AxisColumn(
            name = "HR",
            lineColor = ErgHrLine,
            ticks = listOf(
                (bpmMin + bpmRange).roundToInt().toString(),
                (bpmMin + bpmRange * 0.75f).roundToInt().toString(),
                (bpmMin + bpmRange * 0.5f).roundToInt().toString(),
                (bpmMin + bpmRange * 0.25f).roundToInt().toString(),
            ),
            liveValueText = last?.hrBpm?.toString() ?: "--",
            liveFraction = last?.hrBpm?.let { (1f - ((it - bpmMin) / bpmRange) * (1f - CHART_TOP_HEADROOM)).coerceIn(0f, 1f) } ?: 1f,
            pillColor = ErgHrLine,
            pillTextColor = Color.White,
        )
    }
}

/** One axis of the two charts above: a thin vertical line the full height of the chart, 4 tick
 *  values at 25/50/75/100% of the axis's range (same convention Screen 1's real chart already
 *  uses for Watts/HR), the axis's name written vertically at the very bottom, and a small pill
 *  carrying the live value — centered on the line, free to cover a tick when they coincide. */
@Composable
private fun AxisColumn(
    name: String,
    lineColor: Color,
    ticks: List<String>,
    liveValueText: String,
    liveFraction: Float,
    pillColor: Color,
    pillTextColor: Color = Color.Black,
) {
    BoxWithConstraints(modifier = Modifier.width(24.dp).fillMaxHeight()) {
        val h = maxHeight
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(2.dp)
                .height(h)
                .background(lineColor.copy(alpha = 0.45f)),
        )
        val fracs = listOf(0f, 0.25f, 0.5f, 0.75f)
        ticks.forEachIndexed { i, t ->
            Text(
                t,
                fontSize = 7.sp,
                color = ErgOnSurface.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (h * fracs[i] - 4.dp))
                    .background(ErgBackground)
                    .padding(horizontal = 1.dp),
            )
        }
        Text(
            name,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            color = lineColor,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .rotate(-90f)
                .background(ErgBackground)
                .padding(horizontal = 1.dp),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (h * liveFraction - 8.dp).coerceAtLeast(0.dp))
                .clip(RoundedCornerShape(50))
                .background(pillColor)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(liveValueText, fontSize = 8.sp, fontWeight = FontWeight.Black, color = pillTextColor, maxLines = 1)
        }
    }
}

/** Combines what are today ControlsRow + IntensityRow (two separate rows) into one, at
 *  IntensityRow's own height (38.4dp, already 0.8x-scaled per Task #65) — left half Start/+5min/
 *  Skip (Start and Skip equal width), right half the ERG/HR+ pill, same order the user specified. */
@Composable
private fun CompactControlRow(
    hasWorkout: Boolean,
    isRunning: Boolean,
    hasStarted: Boolean,
    intensityPercent: Int,
    controlMode: ControlMode,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopRequested: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val isPaused = hasStarted && !isRunning
    val mainIcon = when { isRunning -> Icons.Filled.Pause; isPaused -> Icons.Filled.Stop; else -> Icons.Filled.PlayArrow }
    val mainAction = when { isRunning -> onPause; isPaused -> onStopRequested; else -> onPlay }
    val mainColor = if (isPaused) ErgAboveTarget else ErgSurface2
    val isHrPlus = controlMode == ControlMode.HR_PLUS
    val modeColor = if (isHrPlus) ErgHrPlus else ErgModeErg

    Row(
        modifier = Modifier.fillMaxWidth().height(38.4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactButton(
                icon = mainIcon,
                description = "Start/pause/stop",
                onClick = mainAction,
                enabled = hasWorkout,
                containerColor = mainColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            CompactButton(
                icon = Icons.Filled.Add,
                description = "Add 5 minutes",
                onClick = onExtend,
                enabled = hasWorkout,
                modifier = Modifier.fillMaxHeight().width(38.4.dp),
            )
            CompactButton(
                icon = Icons.Filled.SkipNext,
                description = "Skip step",
                onClick = onSkip,
                enabled = hasWorkout,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(ErgSurface2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(modeColor)
                    .clickable(enabled = hasWorkout, onClick = onToggleMode)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isHrPlus) "HR+" else "ERG", fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
            IconButton(onClick = onIncrease, enabled = hasWorkout, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Increase intensity", tint = ErgOnSurface)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("$intensityPercent%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            IconButton(onClick = onDecrease, enabled = hasWorkout, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Decrease intensity", tint = ErgOnSurface)
            }
        }
    }
}

@Composable
private fun CompactButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = ErgSurface2,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) ErgOnSurface else ErgOnSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Same Stop/Discard confirmation as Screen 1's WorkoutScreen (duplicated rather than shared —
 *  the two screens' surrounding layout differs enough that hoisting it wasn't worth the extra
 *  indirection), so stopping from either screen behaves identically. */
@Composable
private fun VitalsStopDialogs(
    viewModel: MainViewModel,
    showStopConfirm: Boolean,
    onStopConfirmChange: (Boolean) -> Unit,
    showDiscardConfirm: Boolean,
    onDiscardConfirmChange: (Boolean) -> Unit,
) {
    if (showStopConfirm) {
        Dialog(onDismissRequest = { onStopConfirmChange(false) }) {
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
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    ) {
                        val pillPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)
                        OutlinedButton(
                            onClick = { onStopConfirmChange(false); onDiscardConfirmChange(true) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErgAboveTarget),
                            contentPadding = pillPadding,
                            modifier = Modifier.weight(1f),
                        ) { Text("Discard", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                        OutlinedButton(
                            onClick = { onStopConfirmChange(false) },
                            shape = RoundedCornerShape(50),
                            contentPadding = pillPadding,
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                        Button(
                            onClick = { onStopConfirmChange(false); viewModel.exitWorkout() },
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
            onDismissRequest = { onDiscardConfirmChange(false); onStopConfirmChange(true) },
            title = { Text("Are you sure you want to discard?") },
            confirmButton = {
                Button(
                    onClick = { onDiscardConfirmChange(false); viewModel.discardWorkout() },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = ErgAboveTarget, contentColor = Color.Black),
                ) { Text("Yes", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onDiscardConfirmChange(false); onStopConfirmChange(true) },
                    shape = RoundedCornerShape(50),
                ) { Text("No", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            },
        )
    }
}

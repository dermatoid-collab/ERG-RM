package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ergrm.trainer.ble.TrainerSample
import com.ergrm.trainer.ui.theme.ErgAboveTarget
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgAtTarget
import com.ergrm.trainer.ui.theme.ErgBelowTarget
import com.ergrm.trainer.ui.theme.ErgDivider
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.workout.WorkoutRunState
import com.ergrm.trainer.workout.WorkoutStep
import kotlin.math.max

@Composable
fun WorkoutScreen(viewModel: MainViewModel, onOpenLibrary: () -> Unit = {}) {
    val live by viewModel.liveData.collectAsState()
    val workoutState by viewModel.workoutState.collectAsState()
    val loadState by viewModel.workoutLoadState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        WorkoutHeader(
            title = when (loadState) {
                is WorkoutLoadState.Loaded -> loadState.name
                else -> if (workoutState.steps.isNotEmpty()) "Allenamento" else "Nessun allenamento"
            },
            onPickFromToday = { viewModel.fetchTodayWorkout() },
            onPickFromLibrary = onOpenLibrary,
        )

        Spacer(modifier = Modifier.height(12.dp))
        PowerReadout(live, workoutState.currentTargetWatts)

        Spacer(modifier = Modifier.height(8.dp))
        StatsRow(live, workoutState)

        Spacer(modifier = Modifier.height(16.dp))
        WorkoutProfileChart(
            steps = workoutState.steps,
            currentStepIndex = workoutState.currentStepIndex,
            totalElapsedSec = workoutState.totalElapsedSec,
            totalDurationSec = workoutState.totalDurationSec,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp), // +33% vs the original 120.dp
        )

        Spacer(modifier = Modifier.height(8.dp))
        WorkoutStatusLine(loadState, workoutState)

        Spacer(modifier = Modifier.height(8.dp))
        IntervalDetailsSection(
            current = workoutState.currentStep,
            currentRemainingSec = workoutState.remainingInStepSec,
            next = workoutState.nextStep,
            ftpWatts = settings.ftpWatts,
        )

        Spacer(modifier = Modifier.weight(1f))

        ControlsRow(
            hasWorkout = workoutState.steps.isNotEmpty(),
            isRunning = workoutState.isRunning,
            hasStarted = workoutState.hasStarted,
            onPlay = { viewModel.startWorkout() },
            onPause = { viewModel.pauseWorkout() },
            onExit = { viewModel.exitWorkout() },
            onExtend = { viewModel.extendCurrentInterval() },
            onSkip = { viewModel.skipStep() },
            onDisconnect = { viewModel.disconnect() },
        )
    }
}

@Composable
private fun PowerReadout(live: TrainerSample, targetWatts: Int) {
    val actual = live.powerWatts ?: 0
    val color = when {
        targetWatts <= 0 -> ErgOnSurface
        actual < targetWatts - 15 -> ErgBelowTarget
        actual > targetWatts + 15 -> ErgAboveTarget
        else -> ErgAtTarget
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$actual",
            fontSize = 96.sp,
            color = color,
        )
        Text(text = "watt", style = MaterialTheme.typography.bodyMedium, color = ErgOnSurface)
        if (targetWatts > 0) {
            Text(
                text = "target $targetWatts W",
                style = MaterialTheme.typography.titleMedium,
                color = ErgOnSurface,
            )
        }
    }
}

@Composable
private fun StatsRow(live: TrainerSample, workoutState: WorkoutRunState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatTile("Cadenza", live.cadenceRpm?.let { "${it.toInt()} rpm" } ?: "--")
        StatTile("FC", live.heartRateBpm?.let { "$it bpm" } ?: "--")
        StatTile("Trascorso", formatTime(workoutState.totalElapsedSec))
        StatTile("Rimanente", formatTime(workoutState.totalRemainingSec))
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ErgOnSurface)
    }
}

/** Chart zoom window, cycled by tapping the chart: full workout -> 5 min -> 1 min -> full. */
private enum class ChartZoom(val windowSec: Int?) {
    FULL(null),
    FIVE_MIN(5 * 60),
    ONE_MIN(60);

    fun next(): ChartZoom = when (this) {
        FULL -> FIVE_MIN
        FIVE_MIN -> ONE_MIN
        ONE_MIN -> FULL
    }
}

@Composable
private fun WorkoutProfileChart(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    modifier: Modifier = Modifier,
) {
    val maxWatts = remember(steps) {
        steps.maxOfOrNull { max(it.startWatts, it.endWatts) }?.coerceAtLeast(1) ?: 1
    }
    val doneColor = Color(0xFF3A4048)
    val pendingColor = Color(0xFF232833)
    var zoom by remember { mutableStateOf(ChartZoom.FULL) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { zoom = zoom.next() })
        },
    ) {
        if (steps.isEmpty() || totalDurationSec <= 0) return@Canvas
        val w = size.width
        val h = size.height

        val windowSec = zoom.windowSec
        val windowStart: Int
        val windowEnd: Int
        if (windowSec == null || windowSec >= totalDurationSec) {
            windowStart = 0
            windowEnd = totalDurationSec
        } else {
            val half = windowSec / 2
            var start = totalElapsedSec - half
            var end = totalElapsedSec + half
            if (start < 0) {
                end -= start
                start = 0
            }
            if (end > totalDurationSec) {
                start -= (end - totalDurationSec)
                end = totalDurationSec
            }
            windowStart = start.coerceAtLeast(0)
            windowEnd = end
        }
        val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
        fun xAt(t: Int): Float = w * (t - windowStart) / windowLen.toFloat()

        var acc = 0
        steps.forEachIndexed { index, step ->
            val stepStart = acc
            val stepEnd = acc + step.durationSec
            acc = stepEnd
            if (stepEnd < windowStart || stepStart > windowEnd) return@forEachIndexed

            val x0 = xAt(stepStart).coerceIn(0f, w)
            val x1 = xAt(stepEnd).coerceIn(0f, w)
            val barHeight = h * (max(step.startWatts, step.endWatts).toFloat() / maxWatts).coerceIn(0.05f, 1f)
            val color = when {
                index < currentStepIndex -> doneColor
                index == currentStepIndex -> ErgAccent
                else -> pendingColor
            }
            drawRect(color = color, topLeft = Offset(x0, h - barHeight), size = Size((x1 - x0).coerceAtLeast(1f), barHeight))

            // Thin light-blue divider between consecutive intervals.
            if (stepStart in windowStart..windowEnd && index > 0) {
                drawLine(color = ErgDivider, start = Offset(x0, 0f), end = Offset(x0, h), strokeWidth = 1.5f)
            }
        }

        if (totalElapsedSec in windowStart..windowEnd) {
            val progressX = xAt(totalElapsedSec).coerceIn(0f, w)
            drawLine(color = Color.White, start = Offset(progressX, 0f), end = Offset(progressX, h), strokeWidth = 3f)
        }
    }
}

private data class PowerZone(val label: String, val color: Color)

/**
 * Andrew Coggan's 7-level power training zones, as %FTP: Active Recovery, Endurance, Tempo,
 * Lactate Threshold, VO2max, Anaerobic Capacity, Neuromuscular Power.
 */
private val POWER_ZONES = listOf(
    0.55f to PowerZone("Z1", Color(0xFF5B6472)),
    0.75f to PowerZone("Z2", ErgBelowTarget),
    0.90f to PowerZone("Z3", ErgAccent),
    1.05f to PowerZone("Z4", Color(0xFFE6C15A)),
    1.20f to PowerZone("Z5", Color(0xFFE08A3E)),
    1.50f to PowerZone("Z6", ErgAboveTarget),
)
private val ZONE_MAX = PowerZone("Z7", Color(0xFFB23A5A))

private fun zoneFor(watts: Int, ftpWatts: Int): PowerZone {
    if (ftpWatts <= 0) return PowerZone("--", ErgOnSurface)
    val pct = watts.toFloat() / ftpWatts
    return POWER_ZONES.firstOrNull { pct <= it.first }?.second ?: ZONE_MAX
}

private fun wattsLabel(step: WorkoutStep): String =
    if (step.startWatts == step.endWatts) "${step.endWatts} W" else "${step.startWatts}–${step.endWatts} W"

@Composable
private fun IntervalDetailsSection(
    current: WorkoutStep?,
    currentRemainingSec: Int,
    next: WorkoutStep?,
    ftpWatts: Int,
) {
    if (current == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntervalDetailBlock(
            label = "Ora",
            step = current,
            remainingSec = currentRemainingSec,
            ftpWatts = ftpWatts,
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
                label = "Poi",
                step = next,
                remainingSec = next.durationSec,
                ftpWatts = ftpWatts,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun IntervalDetailBlock(
    label: String,
    step: WorkoutStep,
    remainingSec: Int,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
) {
    val zone = zoneFor(step.endWatts, ftpWatts)
    Row(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ErgOnSurface)
        Text(formatTime(remainingSec), style = MaterialTheme.typography.bodyMedium)
        Text(wattsLabel(step), style = MaterialTheme.typography.bodyMedium)
        Text(
            zone.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            modifier = Modifier
                .background(zone.color, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun WorkoutStatusLine(loadState: WorkoutLoadState, workoutState: WorkoutRunState) {
    val text = when (loadState) {
        WorkoutLoadState.Idle -> if (workoutState.steps.isEmpty()) "Nessun allenamento caricato" else workoutState.currentStep?.label.orEmpty()
        WorkoutLoadState.Loading -> "Caricamento allenamento…"
        is WorkoutLoadState.Loaded -> workoutState.currentStep?.label ?: loadState.name
        WorkoutLoadState.Empty -> "Nessun allenamento pianificato per oggi su Intervals.icu"
        is WorkoutLoadState.Error -> "Errore: ${loadState.message}"
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ErgOnSurface)
}

/**
 * Main action button cycles Avvia -> Pausa -> Stop: pausing a started workout doesn't offer a
 * resume, only a Stop that exits it (via [onExit]) — matches how the rider actually uses it.
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
    onDisconnect: () -> Unit,
) {
    val isPaused = hasStarted && !isRunning
    val mainLabel = when { isRunning -> "Pausa"; isPaused -> "Stop"; else -> "Avvia" }
    val mainIcon = when { isRunning -> Icons.Filled.Pause; isPaused -> Icons.Filled.Stop; else -> Icons.Filled.PlayArrow }
    val mainAction = when { isRunning -> onPause; isPaused -> onExit; else -> onPlay }
    val mainColor = if (isPaused) ErgAboveTarget else ErgAccent

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = mainAction,
                enabled = hasWorkout,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor),
            ) {
                Icon(mainIcon, contentDescription = null)
                Text(mainLabel)
            }
            OutlinedButton(onClick = onExtend, enabled = hasWorkout, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("5 min")
            }
            OutlinedButton(onClick = onSkip, enabled = hasWorkout, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.SkipNext, contentDescription = null)
                Text("Salta")
            }
        }
        TextButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnetti trainer")
        }
    }
}

/** Workout title, tap to choose whether to load today's plan from Intervals.icu or the local library. */
@Composable
private fun WorkoutHeader(
    title: String,
    onPickFromToday: () -> Unit,
    onPickFromLibrary: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Scegli allenamento")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Oggi (Intervals.icu)") },
                onClick = {
                    expanded = false
                    onPickFromToday()
                },
            )
            DropdownMenuItem(
                text = { Text("Dalla libreria") },
                onClick = {
                    expanded = false
                    onPickFromLibrary()
                },
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

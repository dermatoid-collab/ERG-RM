package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ergrm.trainer.ble.TrainerSample
import com.ergrm.trainer.ui.theme.ErgAboveTarget
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgAtTarget
import com.ergrm.trainer.ui.theme.ErgBelowTarget
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.workout.WorkoutRunState
import com.ergrm.trainer.workout.WorkoutStep
import kotlin.math.max

@Composable
fun WorkoutScreen(viewModel: MainViewModel, onOpenLibrary: () -> Unit = {}) {
    val live by viewModel.liveData.collectAsState()
    val workoutState by viewModel.workoutState.collectAsState()
    val loadState by viewModel.workoutLoadState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
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
                .height(120.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))
        WorkoutStatusLine(loadState, workoutState)

        Spacer(modifier = Modifier.weight(1f))

        ControlsRow(
            hasWorkout = workoutState.steps.isNotEmpty(),
            isRunning = workoutState.isRunning,
            onPickFromToday = { viewModel.fetchTodayWorkout() },
            onPickFromLibrary = onOpenLibrary,
            onPlayPause = {
                if (workoutState.isRunning) viewModel.pauseWorkout() else viewModel.startWorkout()
            },
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

    Canvas(modifier = modifier) {
        if (steps.isEmpty() || totalDurationSec <= 0) return@Canvas
        val w = size.width
        val h = size.height
        var x = 0f
        steps.forEachIndexed { index, step ->
            val stepWidth = (w * step.durationSec / totalDurationSec.toFloat()).coerceAtLeast(1f)
            val barHeight = h * (max(step.startWatts, step.endWatts).toFloat() / maxWatts).coerceIn(0.05f, 1f)
            val color = when {
                index < currentStepIndex -> doneColor
                index == currentStepIndex -> ErgAccent
                else -> pendingColor
            }
            drawRect(color = color, topLeft = Offset(x, h - barHeight), size = Size(stepWidth, barHeight))
            x += stepWidth
        }
        val progressX = (w * totalElapsedSec / totalDurationSec.toFloat()).coerceIn(0f, w)
        drawLine(color = Color.White, start = Offset(progressX, 0f), end = Offset(progressX, h), strokeWidth = 3f)
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

@Composable
private fun ControlsRow(
    hasWorkout: Boolean,
    isRunning: Boolean,
    onPickFromToday: () -> Unit,
    onPickFromLibrary: () -> Unit,
    onPlayPause: () -> Unit,
    onSkip: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkoutSourceButton(
                onPickFromToday = onPickFromToday,
                onPickFromLibrary = onPickFromLibrary,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onPlayPause,
                enabled = hasWorkout,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ErgAccent),
            ) {
                Icon(if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                Text(if (isRunning) "Pausa" else "Avvia")
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

/** Lets the rider choose whether to load today's plan from Intervals.icu or a workout from the local library. */
@Composable
private fun WorkoutSourceButton(
    onPickFromToday: () -> Unit,
    onPickFromLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Carica allenamento")
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

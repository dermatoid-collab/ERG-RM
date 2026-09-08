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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
import com.ergrm.trainer.ui.theme.ErgSurface
import com.ergrm.trainer.ui.theme.ErgSurface2
import com.ergrm.trainer.ui.theme.ErgWarn
import com.ergrm.trainer.workout.SamplePoint
import com.ergrm.trainer.workout.WorkoutRunState
import com.ergrm.trainer.workout.WorkoutStep
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(viewModel: MainViewModel, isTrainerConnected: Boolean = true, onOpenLibrary: () -> Unit = {}) {
    val live by viewModel.liveData.collectAsState()
    val workoutState by viewModel.workoutState.collectAsState()
    val loadState by viewModel.workoutLoadState.collectAsState()
    val samples by viewModel.sampleHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val loaded = loadState
        WorkoutHeader(
            title = when (loaded) {
                is WorkoutLoadState.Loaded -> loaded.name
                else -> if (workoutState.steps.isNotEmpty()) "Workout" else "No workout loaded"
            },
            onPickFromToday = { viewModel.fetchTodayWorkout() },
            onPickFromLibrary = onOpenLibrary,
        )

        if (!isTrainerConnected) {
            Text(
                "Trainer not connected — power targets won't be sent",
                style = MaterialTheme.typography.bodySmall,
                color = ErgWarn,
            )
        }
        WorkoutStatusLine(loadState)

        StatTileGrid(live, workoutState, settings.ftpWatts)

        ControlsRow(
            hasWorkout = workoutState.steps.isNotEmpty(),
            isRunning = workoutState.isRunning,
            hasStarted = workoutState.hasStarted,
            onPlay = { viewModel.startWorkout() },
            onPause = { viewModel.pauseWorkout() },
            onExit = { viewModel.exitWorkout() },
            onExtend = { viewModel.extendCurrentInterval() },
            onSkip = { viewModel.skipStep() },
        )

        IntervalDetailsSection(
            current = workoutState.currentStep,
            currentRemainingSec = workoutState.remainingInStepSec,
            next = workoutState.nextStep,
            ftpWatts = settings.ftpWatts,
            intensityPercent = workoutState.intensityPercent,
        )

        ChartCard(
            steps = workoutState.steps,
            currentStepIndex = workoutState.currentStepIndex,
            totalElapsedSec = workoutState.totalElapsedSec,
            totalDurationSec = workoutState.totalDurationSec,
            samples = samples,
            ftpWatts = settings.ftpWatts,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        IntensityRow(
            intensityPercent = workoutState.intensityPercent,
            enabled = workoutState.steps.isNotEmpty(),
            onDecrease = { viewModel.decreaseIntensity() },
            onIncrease = { viewModel.increaseIntensity() },
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

@Composable
private fun StatTileGrid(live: TrainerSample, workoutState: WorkoutRunState, ftpWatts: Int) {
    val actual = live.powerWatts ?: 0
    val target = workoutState.currentTargetWatts
    val powerColor = when {
        target <= 0 -> ErgOnSurface
        actual < target - 15 -> ErgBelowTarget
        actual > target + 15 -> ErgAboveTarget
        else -> ErgAtTarget
    }
    val zone = zoneFor(target, ftpWatts)

    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Interval", formatTime(workoutState.remainingInStepSec), Modifier.weight(1f))
            StatTile("Total", formatTime(workoutState.totalElapsedSec), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Cadence", live.cadenceRpm?.let { "${it.toInt()}" } ?: "--", Modifier.weight(1f))
            StatTile("HR", live.heartRateBpm?.let { "$it" } ?: "--", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                label = "Target watts",
                value = "$target",
                modifier = Modifier.weight(1f),
                zoneLabel = zone.label,
                zoneColor = zone.color,
            )
            StatTile("Watts", "$actual", Modifier.weight(1f), valueColor = powerColor)
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ErgOnSurface,
    zoneLabel: String? = null,
    zoneColor: Color = ErgOnSurface,
) {
    Column(
        modifier = modifier
            .background(ErgSurface, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
        Text(
            value,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp),
        )
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
private fun ChartCard(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ErgSurface, RoundedCornerShape(14.dp))
            .padding(top = 10.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendKey(Color.White, "Power")
            Spacer(Modifier.width(14.dp))
            LegendKey(ErgAboveTarget, "HR")
            Spacer(Modifier.width(14.dp))
            LegendKey(ErgWarn, "Cadence")
            Spacer(Modifier.weight(1f))
            Text(
                "Tap to zoom",
                style = MaterialTheme.typography.labelSmall,
                color = ErgOnSurface.copy(alpha = 0.5f),
            )
        }
        WorkoutProfileChart(
            steps = steps,
            currentStepIndex = currentStepIndex,
            totalElapsedSec = totalElapsedSec,
            totalDurationSec = totalDurationSec,
            samples = samples,
            ftpWatts = ftpWatts,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(3.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ErgOnSurface.copy(alpha = 0.7f))
    }
}

/** Headroom left empty above the tallest interval bar, as a fraction of the chart height. */
private const val CHART_TOP_HEADROOM = 0.20f

/** Blends a zone's bright accent color toward near-black so bar fills read as muted background,
 *  never as bright as the power/HR/cadence trace lines drawn on top of them. */
private fun mutedZoneColor(zoneColor: Color, active: Boolean): Color {
    val base = Color(0xFF14171D)
    val t = if (active) 0.55f else 0.28f
    return lerp(base, zoneColor, t)
}

@Composable
private fun WorkoutProfileChart(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
) {
    val maxTargetWatts = remember(steps) {
        steps.maxOfOrNull { max(it.startWatts, it.endWatts) }?.coerceAtLeast(1) ?: 1
    }
    val maxSampleWatts = remember(samples) { samples.maxOfOrNull { it.watts } ?: 0 }
    // Divide by (1 - headroom) so the tallest bar/trace reaches only that fraction of the height,
    // leaving CHART_TOP_HEADROOM free at the top.
    val wattsScale = (max(maxTargetWatts, maxSampleWatts).coerceAtLeast(1) / (1f - CHART_TOP_HEADROOM))
    val bpmScale = 200f
    val cadScale = 160f

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
        fun yWatts(watts: Int): Float = h - h * (watts.toFloat() / wattsScale).coerceIn(0f, 1f)
        fun yBpm(bpm: Int): Float = h - h * (bpm.toFloat() / bpmScale).coerceIn(0f, 1f)
        fun yCad(rpm: Int): Float = h - h * (rpm.toFloat() / cadScale).coerceIn(0f, 1f)

        var acc = 0
        steps.forEachIndexed { index, step ->
            val stepStart = acc
            val stepEnd = acc + step.durationSec
            acc = stepEnd
            if (stepEnd < windowStart || stepStart > windowEnd) return@forEachIndexed

            val x0 = xAt(stepStart).coerceIn(0f, w)
            val x1 = xAt(stepEnd).coerceIn(0f, w)
            val barHeight = h * (max(step.startWatts, step.endWatts).toFloat() / wattsScale).coerceIn(0.05f, 1f)
            val zone = zoneFor(max(step.startWatts, step.endWatts), ftpWatts)
            val color = mutedZoneColor(zone.color, active = index == currentStepIndex)
            drawRect(color = color, topLeft = Offset(x0, h - barHeight), size = Size((x1 - x0).coerceAtLeast(1f), barHeight))

            // Thin light-blue divider between consecutive intervals.
            if (stepStart in windowStart..windowEnd && index > 0) {
                drawLine(color = ErgDivider, start = Offset(x0, 0f), end = Offset(x0, h), strokeWidth = 1.5f)
            }
        }

        // Live traces recorded during the workout: cadence (dashed) under HR under power.
        val visibleSamples = samples.filter { it.tSec in windowStart..windowEnd }
        if (visibleSamples.size >= 2) {
            val cadPoints = visibleSamples.mapNotNull { s -> s.cadenceRpm?.let { Offset(xAt(s.tSec), yCad(it)) } }
            val hrPoints = visibleSamples.mapNotNull { s -> s.hrBpm?.let { Offset(xAt(s.tSec), yBpm(it)) } }
            val powerPoints = visibleSamples.map { Offset(xAt(it.tSec), yWatts(it.watts)) }

            if (cadPoints.size >= 2) {
                drawPoints(
                    points = cadPoints,
                    pointMode = PointMode.Polygon,
                    color = ErgWarn,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
            if (hrPoints.size >= 2) {
                drawPoints(
                    points = hrPoints,
                    pointMode = PointMode.Polygon,
                    color = ErgAboveTarget,
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round,
                )
            }
            if (powerPoints.size >= 2) {
                drawPoints(
                    points = powerPoints,
                    pointMode = PointMode.Polygon,
                    color = Color.White,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (totalElapsedSec in windowStart..windowEnd) {
            val progressX = xAt(totalElapsedSec).coerceIn(0f, w)
            drawLine(color = Color.White, start = Offset(progressX, 0f), end = Offset(progressX, h), strokeWidth = 2f, alpha = 0.55f)
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

private fun wattsLabel(startWatts: Int, endWatts: Int): String =
    if (startWatts == endWatts) "$endWatts W" else "$startWatts–$endWatts W"

@Composable
private fun IntervalDetailsSection(
    current: WorkoutStep?,
    currentRemainingSec: Int,
    next: WorkoutStep?,
    ftpWatts: Int,
    intensityPercent: Int,
) {
    if (current == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErgSurface, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntervalDetailBlock(
            label = "Now",
            step = current,
            remainingSec = currentRemainingSec,
            ftpWatts = ftpWatts,
            intensityPercent = intensityPercent,
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
                remainingSec = next.durationSec,
                ftpWatts = ftpWatts,
                intensityPercent = intensityPercent,
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
    intensityPercent: Int,
    modifier: Modifier = Modifier,
) {
    val scaledStart = (step.startWatts * intensityPercent / 100f).roundToInt()
    val scaledEnd = (step.endWatts * intensityPercent / 100f).roundToInt()
    val zone = zoneFor(scaledEnd, ftpWatts)
    Row(
        modifier = modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ErgOnSurface)
        Text(formatTime(remainingSec), style = MaterialTheme.typography.bodyMedium)
        Text(wattsLabel(scaledStart, scaledEnd), style = MaterialTheme.typography.bodyMedium)
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

/**
 * Main action button cycles Start -> Pause -> Stop: pausing a started workout doesn't offer a
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
) {
    val isPaused = hasStarted && !isRunning
    val mainIcon = when { isRunning -> Icons.Filled.Pause; isPaused -> Icons.Filled.Stop; else -> Icons.Filled.PlayArrow }
    val mainAction = when { isRunning -> onPause; isPaused -> onExit; else -> onPlay }
    val mainDescription = when { isRunning -> "Pause"; isPaused -> "Stop"; else -> "Start" }
    val mainContainerColor = if (isPaused) ErgAboveTarget else ErgSurface2

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillIconButton(
            icon = mainIcon,
            contentDescription = mainDescription,
            onClick = mainAction,
            enabled = hasWorkout,
            containerColor = mainContainerColor,
            modifier = Modifier.weight(1f),
        )
        PillIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add 5 minutes to the interval",
            onClick = onExtend,
            enabled = hasWorkout,
            modifier = Modifier.width(60.dp),
        )
        PillIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Skip step",
            onClick = onSkip,
            enabled = hasWorkout,
            modifier = Modifier.weight(1f),
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
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) iconColor else iconColor.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun IntensityRow(
    intensityPercent: Int,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val adjusted = intensityPercent != 100
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Decrease intensity",
            onClick = onDecrease,
            enabled = enabled,
            modifier = Modifier.width(50.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(if (adjusted) ErgAccent else ErgSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$intensityPercent%",
                fontWeight = FontWeight.Bold,
                color = if (adjusted) Color.Black else ErgOnSurface,
            )
        }
        PillIconButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Increase intensity",
            onClick = onIncrease,
            enabled = enabled,
            modifier = Modifier.width(50.dp),
        )
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
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose workout")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Today (Intervals.icu)") },
                onClick = {
                    expanded = false
                    onPickFromToday()
                },
            )
            DropdownMenuItem(
                text = { Text("From library") },
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

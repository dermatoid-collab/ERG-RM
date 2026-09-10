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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ergrm.trainer.ble.TrainerSample
import com.ergrm.trainer.ui.theme.ErgAboveTarget
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgAtTarget
import com.ergrm.trainer.ui.theme.ErgBelowTarget
import com.ergrm.trainer.ui.theme.ErgDivider
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.ui.theme.ErgProgressLine
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
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
            intensityPercent = workoutState.intensityPercent,
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Interval", formatTime(workoutState.remainingInStepSec), Modifier.weight(1f))
            StatTile("Total", formatTime(workoutState.totalElapsedSec), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Cadence", live.cadenceRpm?.let { "${it.toInt()}" } ?: "--", Modifier.weight(1f))
            StatTile("HR", live.heartRateBpm?.let { "$it" } ?: "--", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
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
        Text(
            value,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * Chart zoom window, cycled by tapping the chart's empty area above the bars: full workout ->
 * 20 min -> 5 min -> full. [scrollThresholdSec] is how far into a zoomed window the progress
 * line travels (pinned at the left edge) before the window starts scrolling to keep it in place.
 */
private enum class ChartZoom(val windowSec: Int?, val scrollThresholdSec: Int) {
    FULL(null, 0),
    TWENTY_MIN(20 * 60, 5 * 60),
    FIVE_MIN(5 * 60, 60);

    fun next(): ChartZoom = when (this) {
        FULL -> TWENTY_MIN
        TWENTY_MIN -> FIVE_MIN
        FIVE_MIN -> FULL
    }
}

/** The window [start, end) the chart currently shows, in elapsed seconds. In a zoomed level the
 *  progress line stays pinned [ChartZoom.scrollThresholdSec] from the window's left edge (or at
 *  elapsed time if less has passed) — i.e. it sits at the left edge until that much time has
 *  passed, then the window scrolls to keep it fixed there. */
private fun computeChartWindow(zoom: ChartZoom, totalElapsedSec: Int, totalDurationSec: Int): Pair<Int, Int> {
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
)

@Composable
private fun ChartCard(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    intensityPercent: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(ErgSurface, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        WorkoutProfileChart(
            steps = steps,
            currentStepIndex = currentStepIndex,
            totalElapsedSec = totalElapsedSec,
            totalDurationSec = totalDurationSec,
            samples = samples,
            ftpWatts = ftpWatts,
            intensityPercent = intensityPercent,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/** Headroom left empty above the chart's max watts, as a fraction of the chart height — just
 *  enough for the top axis labels, so the 550W/210bpm labels sit near the very top edge instead
 *  of wasting vertical space above them. */
private const val CHART_TOP_HEADROOM = 0.08f

/** Fraction of the chart's height, from the top, that's the tap-to-zoom target — deliberately
 *  independent of and much larger than [CHART_TOP_HEADROOM] (which only sizes the axis-label
 *  margin). Most real workouts spend most of their profile well under the 550W ceiling, so a
 *  generous fixed band is a far more reliable zoom target than trying to track each bar's own
 *  height; the bottom quarter selects whatever interval is at that x for its tooltip instead. */
private const val CHART_ZOOM_TAP_FRACTION = 0.75f

/**
 * Fixed watts ceiling for the chart's Y axis — deliberately not auto-fit to the data, so that
 * raising or lowering the live %FTP intensity actually changes bar heights against a stable
 * reference instead of the axis rescaling to compensate and hiding the change.
 */
private const val CHART_MAX_WATTS = 550f

/** Blends a zone's bright accent color toward near-black so bar fills read as muted background,
 *  never as bright as the power/HR/cadence trace lines drawn on top of them. */
private fun mutedZoneColor(zoneColor: Color, active: Boolean): Color {
    val base = Color(0xFF14171D)
    val t = if (active) 0.55f else 0.28f
    return lerp(base, zoneColor, t)
}

/** Steps at or after [currentStepIndex] reflect a live intensity change; earlier ones are
 *  history and stay as originally ridden. */
private fun displayWatts(rawWatts: Int, stepIndex: Int, currentStepIndex: Int, intensityPercent: Int): Int =
    if (stepIndex >= currentStepIndex) (rawWatts * intensityPercent / 100f).roundToInt() else rawWatts

@Composable
private fun WorkoutProfileChart(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    intensityPercent: Int,
    modifier: Modifier = Modifier,
) {
    // Divide by (1 - headroom) so the fixed ceiling reaches only that fraction of the height,
    // leaving CHART_TOP_HEADROOM free at the top. A bar or trace above CHART_MAX_WATTS (e.g. a
    // high intensity multiplier pushed it past 550W) is simply clipped rather than rescaling
    // the whole axis — that's the point: the axis stays put so intensity changes are visible.
    val wattsScale = CHART_MAX_WATTS / (1f - CHART_TOP_HEADROOM)
    // Right (HR) axis lines up with the left (watts) axis's 4 gridlines: 90/130/170/210 bpm sit
    // at the same heights as 138/275/413/550 W, a 160bpm range topping out at 210 with the same
    // headroom fraction free above it.
    val bpmMin = 50f
    val bpmRange = 160f
    val cadScale = 160f

    var zoom by remember { mutableStateOf(ChartZoom.FULL) }
    var selectedStepIndex by remember(steps) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val latestInputs = rememberUpdatedState(
        ChartInputs(steps, currentStepIndex, totalElapsedSec, totalDurationSec, intensityPercent, ftpWatts),
    )

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { offset ->
                val inputs = latestInputs.value
                if (inputs.steps.isEmpty() || inputs.totalDurationSec <= 0) return@detectTapGestures
                // Top 75% of the chart cycles zoom; the bottom quarter selects whatever interval
                // sits at that x for its tooltip, even where that particular bar falls short of
                // the tap — matching TrainerDay, where you don't have to land precisely on a
                // short bar's tip.
                if (offset.y < size.height * CHART_ZOOM_TAP_FRACTION) {
                    selectedStepIndex = null
                    zoom = zoom.next()
                    return@detectTapGestures
                }
                val (windowStart, windowEnd) = computeChartWindow(zoom, inputs.totalElapsedSec, inputs.totalDurationSec)
                val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
                val tSec = windowStart + ((offset.x / size.width) * windowLen).roundToInt()
                val tappedIndex = stepIndexAt(tSec, inputs.steps)
                selectedStepIndex = if (tappedIndex != null && selectedStepIndex != tappedIndex) tappedIndex else null
            })
        },
    ) {
        if (steps.isEmpty() || totalDurationSec <= 0) return@Canvas
        val w = size.width
        val h = size.height

        val (windowStart, windowEnd) = computeChartWindow(zoom, totalElapsedSec, totalDurationSec)
        val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
        fun xAt(t: Int): Float = w * (t - windowStart) / windowLen.toFloat()
        fun yWatts(watts: Int): Float = h - h * (watts.toFloat() / wattsScale).coerceIn(0f, 1f)
        fun yBpm(bpm: Int): Float = h - h * (((bpm - bpmMin) / bpmRange) * (1f - CHART_TOP_HEADROOM)).coerceIn(0f, 1f)
        fun yCad(rpm: Int): Float = h - h * (rpm.toFloat() / cadScale).coerceIn(0f, 1f)

        var acc = 0
        var previousBarHeight = 0f
        steps.forEachIndexed { index, step ->
            val stepStart = acc
            val stepEnd = acc + step.durationSec
            acc = stepEnd
            if (stepEnd < windowStart || stepStart > windowEnd) return@forEachIndexed

            val x0 = xAt(stepStart).coerceIn(0f, w)
            val x1 = xAt(stepEnd).coerceIn(0f, w)
            val dispStart = displayWatts(step.startWatts, index, currentStepIndex, intensityPercent)
            val dispEnd = displayWatts(step.endWatts, index, currentStepIndex, intensityPercent)
            val barHeight = h * (max(dispStart, dispEnd).toFloat() / wattsScale).coerceIn(0.05f, 1f)
            val zone = zoneFor(max(dispStart, dispEnd), ftpWatts)
            val color = mutedZoneColor(zone.color, active = index == currentStepIndex)
            drawRect(color = color, topLeft = Offset(x0, h - barHeight), size = Size((x1 - x0).coerceAtLeast(1f), barHeight))

            // Thin light-blue divider between consecutive intervals — stops at the top of the
            // taller of the two adjacent bars instead of running into the empty area above them.
            if (stepStart in windowStart..windowEnd && index > 0) {
                val dividerHeight = max(previousBarHeight, barHeight)
                drawLine(color = ErgDivider, start = Offset(x0, h - dividerHeight), end = Offset(x0, h), strokeWidth = 1.5f)
            }
            previousBarHeight = barHeight
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

        // Progress line: a thin light-blue line at full chart height marking elapsed time —
        // pinned at the window's left edge until computeChartWindow starts scrolling to keep it
        // at its scroll threshold position (see ChartZoom/computeChartWindow).
        if (totalElapsedSec in windowStart..windowEnd) {
            val progressX = xAt(totalElapsedSec).coerceIn(0f, w)
            drawLine(color = ErgProgressLine, start = Offset(progressX, 0f), end = Offset(progressX, h), strokeWidth = 2.5f)
        }

        // Axis gridlines + labels: watts on the left, heart rate on the right, sharing the same
        // 4 height positions.
        val wattsTicks = listOf(138, 275, 413, 550)
        val bpmTicks = listOf(90, 130, 170, 210)
        wattsTicks.forEachIndexed { i, watts ->
            val y = yWatts(watts)
            drawLine(color = ErgOnSurface.copy(alpha = 0.12f), start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
            val wattsLabelResult = textMeasurer.measure("$watts", TextStyle(fontSize = 10.sp, color = ErgOnSurface.copy(alpha = 0.85f)))
            drawText(wattsLabelResult, topLeft = Offset(4.dp.toPx(), y - wattsLabelResult.size.height - 2f))
            val bpmLabelResult = textMeasurer.measure("${bpmTicks[i]}", TextStyle(fontSize = 10.sp, color = ErgAboveTarget))
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
                val selZone = zoneFor(max(selDispStart, selDispEnd), ftpWatts)
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

                pillX += drawPill(durationText, pillX, pillY, ErgSurface2, ErgOnSurface, textMeasurer) + gap
                drawPill(wattsText, pillX, pillY, selZone.color, Color.Black, textMeasurer)
            }
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

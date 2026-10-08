package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.ergrm.trainer.ui.theme.ErgIntensityDownGlyph
import com.ergrm.trainer.ui.theme.ErgIntensityUpGlyph
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
 *  Both charts share one all/20min/5min zoom (tapping either cycles it for both, same as Screen
 *  1's real chart) rather than zooming independently — see the [zoom]/[onZoomChange] params. */
@Composable
fun VitalsScreen(
    viewModel: MainViewModel,
    // Hoisted to AppNav's ActiveWorkoutPager (this screen's only caller) so it survives swiping
    // to the dashboard and back instead of resetting every time the pager disposes/recreates this
    // composable.
    zoom: ChartZoom,
    onZoomChange: (ChartZoom) -> Unit,
) {
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
                totalElapsedSec = workoutState.totalElapsedSec,
                totalDurationSec = workoutState.totalDurationSec,
                zoom = zoom,
                onTap = { onZoomChange(zoom.next()) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(ErgOnSurface.copy(alpha = 0.3f)),
            )
            VitalsPowerChart(
                steps = workoutState.steps,
                currentStepIndex = workoutState.currentStepIndex,
                totalElapsedSec = workoutState.totalElapsedSec,
                totalDurationSec = workoutState.totalDurationSec,
                samples = samples,
                ftpWatts = settings.ftpWatts,
                lthrBpm = settings.lthrBpm,
                intensityPercent = workoutState.intensityPercent,
                zoom = zoom,
                onTap = { onZoomChange(zoom.next()) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            ChartTimeAxis(zoom = zoom, totalElapsedSec = workoutState.totalElapsedSec, totalDurationSec = workoutState.totalDurationSec)
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
    // Left-to-right, top-to-bottom order and HR-red/cadence-blue coloring as requested, echoing
    // the same colors their respective chart lines/axes use below.
    val tiles = listOf(
        Triple("Duration", formatTime(durationSec), ErgOnSurface),
        Triple("Distance", stats.distanceKm?.let { "%.1f km".format(it) } ?: "--", ErgOnSurface),
        Triple("Avg Speed", stats.avgSpeedKmh?.let { "%.1f".format(it) } ?: "--", ErgOnSurface),
        Triple("Work", "${stats.totalKj} kJ", ErgOnSurface),
        Triple("Avg Watts", "${stats.avgWatts} W", ErgOnSurface),
        Triple("NP", stats.normalizedWatts?.let { "$it W" } ?: "--", ErgOnSurface),
        Triple("Max HR", stats.maxHrBpm?.toString() ?: "--", ErgHrLine),
        Triple("Avg HR", stats.avgHrBpm?.toString() ?: "--", ErgHrLine),
        Triple("Avg Cad", stats.avgCadenceRpm?.toString() ?: "--", ErgCadenceLine),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value, valueColor) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(ErgSurface, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label.uppercase(),
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = ErgOnSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** CORE/SKIN/HSI over the whole ride. CORE and HSI are drawn one short segment at a time so each
 *  can take the exact same color its tile would show for that instant (same thresholds as
 *  [coreColor]/[hsiColor]) — SKIN stays [ErgSkinTemp] throughout, since its tile has no
 *  thresholds either. CORE (36.5–39.5°C) and SKIN (28–39.5°C) each get their own range so SKIN's
 *  much wider real-world swing doesn't flatten CORE's narrow one onto a few pixels. */
@Composable
private fun CoreSkinHsiChart(
    samples: List<CoreSamplePoint>,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    zoom: ChartZoom,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = samples.lastOrNull()
    val coreMin = 36.5f
    val coreMax = 39.5f
    val skinMin = 28f
    val skinMax = 39.5f
    val hsiMin = 0f
    val hsiMax = 10f
    fun coreFrac(v: Float) = (1f - (v - coreMin) / (coreMax - coreMin)).coerceIn(0f, 1f)
    fun skinFrac(v: Float) = (1f - (v - skinMin) / (skinMax - skinMin)).coerceIn(0f, 1f)
    fun hsiFrac(v: Float) = (1f - (v - hsiMin) / (hsiMax - hsiMin)).coerceIn(0f, 1f)
    fun ticksFor(min: Float, max: Float) = listOf(0f, 0.25f, 0.5f, 0.75f).map { frac ->
        "%.1f°".format(max - frac * (max - min))
    }
    val coreTicks = ticksFor(coreMin, coreMax)
    val skinTicks = ticksFor(skinMin, skinMax)
    val textMeasurer = rememberTextMeasurer()
    // pointerInput(Unit) below launches its gesture-detection coroutine once and never restarts
    // it, so without rememberUpdatedState it would keep calling the onTap lambda instance (and
    // the zoom value it closed over) from whenever that coroutine first launched — every tap
    // after the first recomputed .next() from the same stale zoom, so the chart appeared to
    // "freeze" after one zoom change.
    val latestOnTap = rememberUpdatedState(onTap)

    Row(modifier = modifier.padding(bottom = NAME_RESERVED_HEIGHT)) {
        AxisColumn(
            name = "CORE",
            lineColor = ErgBelowTarget,
            ticks = coreTicks,
            liveValueText = last?.coreTempC?.let { "%.1f°".format(floorToOneDecimal(it)) } ?: "--",
            liveFraction = last?.coreTempC?.let { coreFrac(it) } ?: 1f,
            valueColor = last?.coreTempC?.let { coreColor(floorToOneDecimal(it)) } ?: ErgOnSurface,
        )
        AxisColumn(
            name = "SKIN",
            lineColor = ErgSkinTemp,
            ticks = skinTicks,
            liveValueText = last?.skinTempC?.let { "%.1f°".format(it) } ?: "--",
            liveFraction = last?.skinTempC?.let { skinFrac(it) } ?: 1f,
            valueColor = ErgSkinTemp,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) { detectTapGestures { latestOnTap.value() } },
        ) {
            val w = size.width
            val h = size.height
            // Same horizontal gridlines, at the same 4 tick heights, as the real chart on Screen 1.
            listOf(0f, 0.25f, 0.5f, 0.75f).forEach { frac ->
                drawLine(ErgOnSurface.copy(alpha = 0.12f), Offset(0f, h * frac), Offset(w, h * frac), strokeWidth = 2.25f)
            }
            if (samples.size < 2 || totalDurationSec <= 0) return@Canvas
            val (windowStart, windowEnd) = computeChartWindow(zoom, totalElapsedSec, totalDurationSec)
            val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
            fun xAt(t: Int) = w * (t - windowStart) / windowLen.toFloat()
            val strokeW = 2.dp.toPx()
            for (i in 0 until samples.size - 1) {
                val a = samples[i]
                val b = samples[i + 1]
                if (b.tSec < windowStart || a.tSec > windowEnd) continue
                val x0 = xAt(a.tSec)
                val x1 = xAt(b.tSec)
                val ac = a.coreTempC
                val bc = b.coreTempC
                if (ac != null && bc != null) {
                    drawLine(coreColor(floorToOneDecimal(bc)), Offset(x0, h * coreFrac(ac)), Offset(x1, h * coreFrac(bc)), strokeWidth = strokeW)
                }
                val askin = a.skinTempC
                val bskin = b.skinTempC
                if (askin != null && bskin != null) {
                    drawLine(ErgSkinTemp, Offset(x0, h * skinFrac(askin)), Offset(x1, h * skinFrac(bskin)), strokeWidth = strokeW)
                }
                val ah = a.heatStrainIndex
                val bh = b.heatStrainIndex
                if (ah != null && bh != null) {
                    drawLine(hsiColor(floorToOneDecimal(bh)), Offset(x0, h * hsiFrac(ah)), Offset(x1, h * hsiFrac(bh)), strokeWidth = strokeW)
                }
            }

            // Live value repeated right at the current point on each trace, not just on the axis
            // pill — easier to read at a glance which number belongs to which of the 3 lines when
            // they're close together, without hunting back to the axis gutter.
            val liveSample = last
            if (liveSample != null && liveSample.tSec in windowStart..windowEnd) {
                val x = xAt(liveSample.tSec)
                fun drawInlineLabel(text: String, frac: Float, color: Color) {
                    val measured = textMeasurer.measure(text, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = color))
                    val labelX = (x + 6.dp.toPx()).coerceAtMost(w - measured.size.width - 2.dp.toPx())
                    val labelY = (h * frac - measured.size.height / 2f).coerceIn(0f, h - measured.size.height)
                    drawText(measured, topLeft = Offset(labelX, labelY))
                }
                // Canvas draws paint over earlier ones, so later calls here end up frontmost:
                // SKIN first (backmost), then HSI, then CORE last (frontmost).
                liveSample.skinTempC?.let { v -> drawInlineLabel("%.1f°".format(v), skinFrac(v), ErgSkinTemp) }
                liveSample.heatStrainIndex?.let { v ->
                    val floored = floorToOneDecimal(v)
                    drawInlineLabel("%.1f".format(floored), hsiFrac(v), hsiColor(floored))
                }
                liveSample.coreTempC?.let { v ->
                    val floored = floorToOneDecimal(v)
                    drawInlineLabel("%.1f°".format(floored), coreFrac(v), coreColor(floored))
                }
            }
        }
        AxisColumn(
            name = "HSI",
            lineColor = ErgAccent,
            ticks = listOf("10", "7.5", "5", "2.5"),
            liveValueText = last?.heatStrainIndex?.let { "%.1f".format(floorToOneDecimal(it)) } ?: "--",
            liveFraction = last?.heatStrainIndex?.let { hsiFrac(it) } ?: 1f,
            valueColor = last?.heatStrainIndex?.let { hsiColor(floorToOneDecimal(it)) } ?: ErgOnSurface,
        )
    }
}

/** The familiar power/HR/cadence chart — same tap-to-cycle all/20min/5min zoom as Screen 1's real
 *  chart (shared with [CoreSkinHsiChart] above it, see [VitalsScreen]) — with a new CADENCE axis
 *  column added alongside WATTS: cadence is already plotted today (just like
 *  [com.ergrm.trainer.ui.WorkoutProfileChart]'s own `ErgCadenceLine` trace) but has never had a
 *  visible axis of its own until now. */
@Composable
private fun VitalsPowerChart(
    steps: List<WorkoutStep>,
    currentStepIndex: Int,
    totalElapsedSec: Int,
    totalDurationSec: Int,
    samples: List<SamplePoint>,
    ftpWatts: Int,
    lthrBpm: Int,
    intensityPercent: Int,
    zoom: ChartZoom,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wattsScale = chartMaxWatts(ftpWatts) / (1f - CHART_TOP_HEADROOM)
    val maxWatts = chartMaxWatts(ftpWatts)
    val bpmMin = CHART_BPM_MIN
    val bpmRange = chartMaxBpm(lthrBpm) - bpmMin
    val cadScale = CHART_MAX_CADENCE / (1f - CHART_TOP_HEADROOM)
    val last = samples.lastOrNull()
    // Same stale-closure fix as CoreSkinHsiChart above: pointerInput(Unit) never restarts its
    // gesture coroutine, so onTap must be read through rememberUpdatedState to see later taps'
    // current zoom instead of freezing after the first.
    val latestOnTap = rememberUpdatedState(onTap)

    Row(modifier = modifier.padding(bottom = NAME_RESERVED_HEIGHT)) {
        AxisColumn(
            name = "CAD",
            lineColor = ErgCadenceLine,
            ticks = listOf("140", "105", "70", "35"),
            liveValueText = last?.cadenceRpm?.toString() ?: "--",
            liveFraction = last?.cadenceRpm?.let { (1f - it / cadScale).coerceIn(0f, 1f) } ?: 1f,
            valueColor = ErgCadenceLine,
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
            // Same white as the power trace it sits beside.
            valueColor = Color.White,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) { detectTapGestures { latestOnTap.value() } },
        ) {
            val w = size.width
            val h = size.height
            // Same horizontal gridlines, at the same 4 tick heights, as the real chart on Screen 1.
            listOf(0f, 0.25f, 0.5f, 0.75f).forEach { frac ->
                drawLine(ErgOnSurface.copy(alpha = 0.12f), Offset(0f, h * frac), Offset(w, h * frac), strokeWidth = 2.25f)
            }
            if (steps.isEmpty() || totalDurationSec <= 0) return@Canvas
            val (windowStart, windowEnd) = computeChartWindow(zoom, totalElapsedSec, totalDurationSec)
            val windowLen = (windowEnd - windowStart).coerceAtLeast(1)
            fun xAt(t: Int) = w * (t - windowStart) / windowLen.toFloat()
            fun yWatts(watts: Int) = h - h * (watts.toFloat() / wattsScale).coerceIn(0f, 1f)
            fun yBpm(bpm: Int) = h - h * (((bpm - bpmMin) / bpmRange) * (1f - CHART_TOP_HEADROOM)).coerceIn(0f, 1f)
            fun yCad(rpm: Int) = h - h * (rpm.toFloat() / cadScale).coerceIn(0f, 1f)

            var acc = 0
            steps.forEachIndexed { index, step ->
                val stepStart = acc
                val stepEnd = acc + step.durationSec
                acc = stepEnd
                if (stepEnd < windowStart || stepStart > windowEnd) return@forEachIndexed

                val dispStart = displayWatts(step.startWatts, index, currentStepIndex, intensityPercent)
                val dispEnd = displayWatts(step.endWatts, index, currentStepIndex, intensityPercent)
                // Same 5%-of-height floor as Screen 1's real chart's bars (distinct from yWatts(),
                // which floors at 0 for the power TRACE) — without it, a ramp step whose start (or
                // end) is near 0W draws that edge at zero height, i.e. the bar visibly collapses to
                // nothing right where the ramp is lowest, usually its left edge.
                val startBarHeight = h * (dispStart.toFloat() / wattsScale).coerceIn(0.05f, 1f)
                val endBarHeight = h * (dispEnd.toFloat() / wattsScale).coerceIn(0.05f, 1f)

                // Same ramp-truncation interpolation as Screen 1's real chart (see
                // WorkoutProfileChart) — when the zoomed window cuts a ramp step partway through,
                // the trapezoid's visible edge needs the interpolated watts at that exact cut
                // point, not the step's true start/end, or the edge won't line up with the power
                // line under it.
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

                // mutedZoneColor (not the bright zone.color used for the live traces' thresholds)
                // — matches Screen 1's muted bar fill, brightened only for the current step.
                val zone = zoneFor(max(dispStart, dispEnd), ftpWatts)
                val color = mutedZoneColor(zone.color, active = index == currentStepIndex)
                val path = Path().apply {
                    moveTo(x0, h - drawStartHeight)
                    lineTo(x1, h - drawEndHeight)
                    lineTo(x1, h)
                    lineTo(x0, h)
                    close()
                }
                drawPath(path, color = color)
                if (stepStart in windowStart..windowEnd && index > 0) {
                    drawLine(ErgDivider.copy(alpha = 0.5f), Offset(x0, h - startBarHeight), Offset(x0, h), strokeWidth = 1.dp.toPx())
                }
            }
            // Same 3 live traces, same draw order (cadence under HR under power) and the same
            // drawPoints/Polygon/round-cap approach, as Screen 1's real chart — continuous solid
            // lines throughout, including cadence (no dashing), and the actual achieved power
            // drawn in white on top of the target-zone bars below it.
            val visibleSamples = samples.filter { it.tSec in windowStart..windowEnd }
            if (visibleSamples.size >= 2) {
                val cadPoints = visibleSamples.mapNotNull { s -> s.cadenceRpm?.let { Offset(xAt(s.tSec), yCad(it)) } }
                val hrPoints = visibleSamples.mapNotNull { s -> s.hrBpm?.let { Offset(xAt(s.tSec), yBpm(it)) } }
                val powerPoints = visibleSamples.map { Offset(xAt(it.tSec), yWatts(it.watts)) }

                if (cadPoints.size >= 2) {
                    drawPoints(points = cadPoints, pointMode = PointMode.Polygon, color = ErgCadenceLine, strokeWidth = 3f, cap = StrokeCap.Round)
                }
                if (hrPoints.size >= 2) {
                    drawPoints(points = hrPoints, pointMode = PointMode.Polygon, color = ErgHrLine, strokeWidth = 4f, cap = StrokeCap.Round)
                }
                if (powerPoints.size >= 2) {
                    drawPoints(points = powerPoints, pointMode = PointMode.Polygon, color = Color.White, strokeWidth = 4f, cap = StrokeCap.Round)
                }
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
            valueColor = ErgHrLine,
        )
    }
}

/** One axis of the two charts above: a thin vertical line the full height of this column, 4 tick
 *  values at 25/50/75/100% of the axis's range (same convention, and now the same 10sp size, as
 *  Screen 1's real chart axis labels), the axis's name written vertically at the very bottom, and
 *  the live value as plain colored text on the chart's own background — centered on the line,
 *  free to cover a tick when they coincide, and free to spill past this column's own width (see
 *  below) rather than ever truncating.
 *
 *  The name sits bottom-aligned INSIDE this column, but rotate() paints outside its own layout
 *  bounds — the caller (the chart's Row) reserves [NAME_RESERVED_HEIGHT] of blank space below via
 *  padding for the rotated text to spill into, so the Canvas it sits beside (which must keep
 *  using this same column's full height as its own coordinate space, or the two would no longer
 *  agree on where each tick height falls) isn't shortened to make room for it. */
@Composable
private fun AxisColumn(
    name: String,
    lineColor: Color,
    ticks: List<String>,
    liveValueText: String,
    liveFraction: Float,
    valueColor: Color,
) {
    BoxWithConstraints(modifier = Modifier.width(30.dp).fillMaxHeight()) {
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
                fontSize = 10.sp,
                color = ErgOnSurface.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (h * fracs[i] - 6.dp))
                    .background(ErgBackground)
                    .padding(horizontal = 1.dp),
            )
        }
        Text(
            name,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = lineColor,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .rotate(-90f)
                .background(ErgBackground)
                .padding(horizontal = 1.dp),
        )
        // Not a colored badge any more (it couldn't fit this column's 30dp width without
        // truncating the text — see Screen 2's device feedback): a plain label in the chart's own
        // background color, slightly transparent so the gridline/trace underneath still shows
        // through a little, sized to whatever the full value needs via wrapContentWidth(unbounded
        // = true) rather than being capped at the column's own width — it's allowed to spill into
        // the plot area beside it, same as the axis name below does vertically.
        Text(
            liveValueText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = valueColor,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (h * liveFraction - 9.dp).coerceAtLeast(0.dp))
                .wrapContentWidth(unbounded = true)
                .background(ErgBackground.copy(alpha = 0.82f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp),
        )
    }
}

private val NAME_RESERVED_HEIGHT = 18.dp

/** Combines what are today ControlsRow + IntensityRow (two separate rows) into one, at
 *  IntensityRow's own height (38.4dp, already 0.8x-scaled per Task #65) — Start/+5min/Skip as 3
 *  fixed-size circles on the left (only as wide as they need to be), the ERG/HR+ pill taking
 *  all the width that frees up on the right, same order the user specified. */
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
        // All 3 fixed-size circles now (not stretched to weight(1f)), so this group only takes
        // the width it actually needs — the space that frees up goes to the ERG/HR+ side below
        // via its own weight(1f), the only one left in this outer Row.
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactButton(
                icon = mainIcon,
                description = "Start/pause/stop",
                onClick = mainAction,
                enabled = hasWorkout,
                containerColor = mainColor,
                modifier = Modifier.size(38.4.dp),
                // Plain tap in all 3 states (Start/Pause/Stop) — only +5min and Skip require the
                // 2s hold now.
            )
            CompactButton(
                icon = Icons.Filled.Add,
                description = "Add 5 minutes",
                onClick = onExtend,
                enabled = hasWorkout,
                modifier = Modifier.size(38.4.dp),
                requireLongPressMillis = 2000L,
            )
            CompactButton(
                icon = Icons.Filled.SkipNext,
                description = "Skip step",
                onClick = onSkip,
                enabled = hasWorkout,
                modifier = Modifier.size(38.4.dp),
                requireLongPressMillis = 2000L,
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
                // A perfect circle (width == height == the row's own 38.4dp) instead of an oval
                // sized by its padding.
                modifier = Modifier
                    .size(38.4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(modeColor)
                    .clickable(enabled = hasWorkout, onClick = onToggleMode),
                contentAlignment = Alignment.Center,
            ) {
                // Same font size as the percentage next to it.
                Text(if (isHrPlus) "HR+" else "ERG", fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
            // Decrease sits closest to the mode circle; increase sits on the far side, next to
            // where the direction triangle appears — same pairing as the main Workout screen's
            // own pill (increase/▲ together on the right, decrease/▼ together on the left).
            IconButton(
                onClick = onDecrease,
                enabled = hasWorkout,
                modifier = Modifier.padding(start = 3.dp).size(30.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Decrease intensity",
                    tint = ErgOnSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("$intensityPercent%", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            // Same width as the ERG/HR+ circle (balances it so the % box above stays centered in
            // the whole pill) — also where the ▲/▼ direction triangle shows, when intensity isn't
            // 100%, same colors/shape as the main Workout screen's own pill. Sits BEFORE the
            // increase arrow (not after it) so the arrow stays the pill's true rightmost element,
            // same as screen 1 — the triangle is adjacent to it, not past it.
            Box(modifier = Modifier.size(38.4.dp), contentAlignment = Alignment.Center) {
                if (intensityPercent != 100) {
                    IntensityTriangle(
                        pointingUp = intensityPercent > 100,
                        color = if (intensityPercent > 100) ErgIntensityUpGlyph else ErgIntensityDownGlyph,
                        sizeDp = 14.4.dp,
                    )
                }
            }
            IconButton(
                onClick = onIncrease,
                enabled = hasWorkout,
                modifier = Modifier.padding(end = 3.dp).size(30.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Increase intensity",
                    tint = ErgOnSurface,
                    modifier = Modifier.size(26.dp),
                )
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
    // Start/Pause/Stop, +5min and Skip pass a duration here so a brush of the screen mid-ride
    // can't trigger them — only a deliberate, held-down press does (see requireLongPress in
    // WorkoutScreen.kt, shared with this screen's own Start/Pause/Stop/+5/Skip row).
    requireLongPressMillis: Long? = null,
) {
    Box(
        modifier = modifier
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
            contentDescription = description,
            tint = if (enabled) ErgOnSurface else ErgOnSurface.copy(alpha = 0.4f),
            // 22dp inside a 38.4dp circle — still well clear of the edge.
            modifier = Modifier.size(22.dp),
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

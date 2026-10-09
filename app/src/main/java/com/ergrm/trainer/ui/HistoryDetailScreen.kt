package com.ergrm.trainer.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ergrm.trainer.history.CORE_TEMP_ALERT_C
import com.ergrm.trainer.history.SessionSample
import com.ergrm.trainer.history.SessionStats
import com.ergrm.trainer.history.WorkoutSession
import com.ergrm.trainer.history.computeSessionStats
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgBelowTarget
import com.ergrm.trainer.ui.theme.ErgHrLine
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.ui.theme.ErgProgressLine
import com.ergrm.trainer.ui.theme.ErgSkinTemp
import com.ergrm.trainer.ui.theme.ErgSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Opened from a History row: the full recorded trace plus a summary stats grid. No colored
 *  zone bars — a session only stores what was recorded (watts/HR/cadence/speed), not the
 *  original plan, so this mirrors the live workout chart's traces without the interval backdrop. */
@Composable
fun SessionDetailScreen(session: WorkoutSession, viewModel: MainViewModel, onBack: () -> Unit) {
    val stats = remember(session.id) { computeSessionStats(session.samples) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(session.workoutName ?: "Free ride", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatSessionDate(session.startEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                )
            }
            IconButton(onClick = {
                viewModel.exportSessionTcx(session) { file ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.garmin.tcx+xml"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    AppNavigationEvents.pickerLaunchInFlight = true
                    context.startActivity(Intent.createChooser(sendIntent, "Export session"))
                }
            }) {
                Icon(Icons.Filled.Share, contentDescription = "Export as TCX")
            }
        }

        // No scroll here (unlike most other screens): charts get weight(1f) each so they share
        // whatever vertical space is left after the summary tiles below them — which stay at
        // their natural height — rather than pushing the tiles off-screen under a scrollbar.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hasCoreData = session.samples.any {
                it.coreTempC != null || it.skinTempC != null || it.heatStrainIndex != null
            }
            if (session.samples.size >= 2) {
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.padding(10.dp, 10.dp, 10.dp, 4.dp).fillMaxSize()) {
                        SessionDetailChart(
                            samples = session.samples,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        ChartLegend(hasHr = stats.avgHrBpm != null, hasCadence = stats.avgCadenceRpm != null)
                    }
                }
            }

            if (hasCoreData) {
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.padding(10.dp, 10.dp, 10.dp, 4.dp).fillMaxSize()) {
                        CoreSkinHsiDetailChart(
                            samples = session.samples,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        CoreSkinHsiLegend()
                    }
                }
            }

            Text(
                "SUMMARY",
                style = MaterialTheme.typography.labelMedium,
                color = ErgOnSurface.copy(alpha = 0.7f),
            )
            StatsGrid(session, stats)
        }
    }
}

@Composable
private fun ChartLegend(hasHr: Boolean, hasCadence: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
    ) {
        LegendEntry("Watts", Color.White)
        if (hasHr) LegendEntry("HR", ErgHrLine)
        if (hasCadence) LegendEntry("Cadence", ErgProgressLine)
    }
}

@Composable
private fun LegendEntry(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = ErgOnSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun SessionDetailChart(samples: List<SessionSample>, modifier: Modifier = Modifier) {
    // Which sample the scrub line/tooltip currently points at — set on first touch, updated as
    // the finger drags, and then left as-is (not cleared) until the next touch moves it again.
    var selectedIndex by remember(samples) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier.pointerInput(samples) {
            val lastIndex = samples.size - 1
            if (lastIndex < 1) return@pointerInput
            fun indexAt(touchX: Float) = (touchX / size.width * lastIndex).roundToInt().coerceIn(0, lastIndex)
            detectDragGestures(
                onDragStart = { offset -> selectedIndex = indexAt(offset.x) },
                onDrag = { change, _ ->
                    change.consume()
                    selectedIndex = indexAt(change.position.x)
                },
            )
        },
    ) {
        val n = samples.size
        if (n < 2) return@Canvas
        val w = size.width
        val h = size.height
        fun x(i: Int) = w * i / (n - 1).toFloat()

        // Smoothed (3-sample ≈ 3s centered moving average) for the drawn traces only — the scrub
        // tooltip below still reports each sample's own exact reading, unsmoothed.
        val smoothedWatts = smoothed(samples.map { it.watts.toFloat() })
        val smoothedHr = smoothed(samples.map { it.hrBpm?.toFloat() })
        val smoothedCad = smoothed(samples.map { it.cadenceRpm?.toFloat() })

        val maxWatts = samples.maxOf { it.watts }.coerceAtLeast(50)
        fun yWatts(v: Float) = h - h * (v / maxWatts).coerceIn(0f, 1f)

        val hrs = samples.mapNotNull { it.hrBpm }
        if (hrs.size >= 2) {
            val hrMin = (hrs.min() - 10).coerceAtLeast(0)
            val hrRange = (hrs.max() - hrMin).coerceAtLeast(1)
            fun yHr(v: Float) = h - h * ((v - hrMin) / hrRange).coerceIn(0f, 1f)
            val points = smoothedHr.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yHr(it)) } }
            drawPoints(points = points, pointMode = PointMode.Polygon, color = ErgHrLine, strokeWidth = 3f, cap = StrokeCap.Round)
        }

        val cadences = samples.mapNotNull { it.cadenceRpm }
        if (cadences.size >= 2) {
            val cadMax = cadences.max().coerceAtLeast(10)
            fun yCad(v: Float) = h - h * (v / cadMax).coerceIn(0f, 1f)
            val points = smoothedCad.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yCad(it)) } }
            drawPoints(points = points, pointMode = PointMode.Polygon, color = ErgProgressLine, strokeWidth = 2.5f, cap = StrokeCap.Round)
        }

        val wattsPoints = smoothedWatts.mapIndexed { i, v -> Offset(x(i), yWatts(v ?: 0f)) }
        drawPoints(points = wattsPoints, pointMode = PointMode.Polygon, color = Color.White, strokeWidth = 3f, cap = StrokeCap.Round)

        // Scrub indicator: vertical line at the selected sample + a tooltip box with that
        // sample's time, watts, HR and cadence, each colored to match its own trace above.
        selectedIndex?.let { idx ->
            val sample = samples.getOrNull(idx) ?: return@let
            val lineX = x(idx)
            drawLine(color = ErgOnSurface.copy(alpha = 0.5f), start = Offset(lineX, 0f), end = Offset(lineX, h), strokeWidth = 1.5f)

            val lines = buildList {
                add(formatScrubTime(sample.tSec) to ErgOnSurface)
                add("${sample.watts}W" to Color.White)
                sample.hrBpm?.let { add("$it bpm" to ErgHrLine) }
                sample.cadenceRpm?.let { add("$it rpm" to ErgProgressLine) }
            }
            val measured = lines.map { (text, color) -> textMeasurer.measure(text, TextStyle(fontSize = 11.sp, color = color)) }
            val padding = 6.dp.toPx()
            val rowHeight = measured.maxOf { it.size.height }
            val boxWidth = measured.maxOf { it.size.width } + padding * 2
            val boxHeight = rowHeight * measured.size + padding * 2
            // Tooltip sits to the right of the line, flipping to the left once there's no room —
            // never lets it run off either edge of the chart.
            val gap = 4.dp.toPx()
            val boxX = if (lineX + gap + boxWidth > w) lineX - gap - boxWidth else lineX + gap
            val boxXClamped = boxX.coerceIn(0f, (w - boxWidth).coerceAtLeast(0f))
            drawRect(color = ErgSurface, topLeft = Offset(boxXClamped, 0f), size = Size(boxWidth, boxHeight))
            measured.forEachIndexed { i, result ->
                drawText(result, topLeft = Offset(boxXClamped + padding, padding + rowHeight * i))
            }
        }
    }
}

private fun formatScrubTime(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

/** 3-sample (≈3s at this app's 1Hz sampling) centered moving average, for softening raw
 *  per-second sensor noise in a drawn chart trace — never used for the scrub tooltip, which
 *  always reports a sample's own exact reading. A null sample stays null rather than being
 *  interpolated from its neighbors, so a genuine sensor gap still shows as a gap. */
private fun smoothed(values: List<Float?>): List<Float?> = values.indices.map { i ->
    val v = values[i] ?: return@map null
    val neighbors = listOfNotNull(values.getOrNull(i - 1), values.getOrNull(i + 1))
    (v + neighbors.sum()) / (1 + neighbors.size)
}

@Composable
private fun CoreSkinHsiLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
    ) {
        LegendEntry("Core", ErgBelowTarget)
        LegendEntry("Skin", ErgSkinTemp)
        LegendEntry("HSI", ErgAccent)
    }
}

/** Same scrub-chart pattern as [SessionDetailChart], for the CORE sensor's 3 traces instead of
 *  power/HR/cadence. Fixed ranges (not auto-fit to the data) match the live Vitals chart's own
 *  CORE/SKIN/HSI scale, so a ride looks the same whether read live or from history. */
@Composable
private fun CoreSkinHsiDetailChart(samples: List<SessionSample>, modifier: Modifier = Modifier) {
    var selectedIndex by remember(samples) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val coreMin = 34.5f
    val coreMax = 39.5f
    val skinMin = 28f
    val skinMax = 39.5f
    val hsiMin = 0f
    val hsiMax = 10f

    Canvas(
        modifier = modifier.pointerInput(samples) {
            val lastIndex = samples.size - 1
            if (lastIndex < 1) return@pointerInput
            fun indexAt(touchX: Float) = (touchX / size.width * lastIndex).roundToInt().coerceIn(0, lastIndex)
            detectDragGestures(
                onDragStart = { offset -> selectedIndex = indexAt(offset.x) },
                onDrag = { change, _ ->
                    change.consume()
                    selectedIndex = indexAt(change.position.x)
                },
            )
        },
    ) {
        val n = samples.size
        if (n < 2) return@Canvas
        val w = size.width
        val h = size.height
        fun x(i: Int) = w * i / (n - 1).toFloat()
        fun yFor(v: Float, min: Float, max: Float) = h - h * ((v - min) / (max - min)).coerceIn(0f, 1f)

        val smoothedCore = smoothed(samples.map { it.coreTempC })
        val smoothedSkin = smoothed(samples.map { it.skinTempC })
        val smoothedHsi = smoothed(samples.map { it.heatStrainIndex })

        // SKIN behind, HSI in the middle, CORE in front — same stacking as the live chart.
        val skinPoints = smoothedSkin.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yFor(it, skinMin, skinMax)) } }
        if (skinPoints.size >= 2) {
            drawPoints(points = skinPoints, pointMode = PointMode.Polygon, color = ErgSkinTemp, strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
        val hsiPoints = smoothedHsi.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yFor(it, hsiMin, hsiMax)) } }
        if (hsiPoints.size >= 2) {
            drawPoints(points = hsiPoints, pointMode = PointMode.Polygon, color = ErgAccent, strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
        val corePoints = smoothedCore.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), yFor(it, coreMin, coreMax)) } }
        if (corePoints.size >= 2) {
            drawPoints(points = corePoints, pointMode = PointMode.Polygon, color = ErgBelowTarget, strokeWidth = 3f, cap = StrokeCap.Round)
        }

        selectedIndex?.let { idx ->
            val sample = samples.getOrNull(idx) ?: return@let
            val lineX = x(idx)
            drawLine(color = ErgOnSurface.copy(alpha = 0.5f), start = Offset(lineX, 0f), end = Offset(lineX, h), strokeWidth = 1.5f)

            val lines = buildList {
                add(formatScrubTime(sample.tSec) to ErgOnSurface)
                sample.coreTempC?.let { add("%.1f°C".format(it) to ErgBelowTarget) }
                sample.skinTempC?.let { add("%.1f°C".format(it) to ErgSkinTemp) }
                sample.heatStrainIndex?.let { add("%.1f HSI".format(it) to ErgAccent) }
            }
            if (lines.size <= 1) return@let
            val measured = lines.map { (text, color) -> textMeasurer.measure(text, TextStyle(fontSize = 11.sp, color = color)) }
            val padding = 6.dp.toPx()
            val rowHeight = measured.maxOf { it.size.height }
            val boxWidth = measured.maxOf { it.size.width } + padding * 2
            val boxHeight = rowHeight * measured.size + padding * 2
            val gap = 4.dp.toPx()
            val boxX = if (lineX + gap + boxWidth > w) lineX - gap - boxWidth else lineX + gap
            val boxXClamped = boxX.coerceIn(0f, (w - boxWidth).coerceAtLeast(0f))
            drawRect(color = ErgSurface, topLeft = Offset(boxXClamped, 0f), size = Size(boxWidth, boxHeight))
            measured.forEachIndexed { i, result ->
                drawText(result, topLeft = Offset(boxXClamped + padding, padding + rowHeight * i))
            }
        }
    }
}

@Composable
private fun StatsGrid(session: WorkoutSession, stats: SessionStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile("Duration", formatWorkoutDuration(session.durationSec), modifier = Modifier.weight(1f))
            DetailTile("Distance", stats.distanceKm?.let { "%.1f".format(it) } ?: "n/a", unit = "km", modifier = Modifier.weight(1f))
            DetailTile("Avg speed", stats.avgSpeedKmh?.let { "%.1f".format(it) } ?: "n/a", unit = "km/h", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile("Work", "${stats.totalKj}", unit = "kJ", modifier = Modifier.weight(1f))
            DetailTile("Avg watts", "${stats.avgWatts}", unit = "W", modifier = Modifier.weight(1f))
            DetailTile("NP", stats.normalizedWatts?.let { "$it" } ?: "n/a", unit = "W", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile(
                "Max HR", stats.maxHrBpm?.let { "$it" } ?: "n/a", unit = "bpm", modifier = Modifier.weight(1f),
                valueColor = if (stats.maxHrBpm != null) ErgHrLine else ErgOnSurface,
            )
            DetailTile(
                "Avg HR", stats.avgHrBpm?.let { "$it" } ?: "n/a", unit = "bpm", modifier = Modifier.weight(1f),
                valueColor = if (stats.avgHrBpm != null) ErgHrLine else ErgOnSurface,
            )
            DetailTile("Avg cadence", stats.avgCadenceRpm?.let { "$it" } ?: "n/a", unit = "rpm", modifier = Modifier.weight(1f))
        }
        if (stats.avgCoreTempC != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                DetailTile("Avg core temp", "%.2f".format(stats.avgCoreTempC), unit = "°C", modifier = Modifier.weight(1f))
                DetailTile(
                    "Max core temp", stats.maxCoreTempC?.let { "%.2f".format(it) } ?: "n/a", unit = "°C",
                    modifier = Modifier.weight(1f),
                )
                DetailTile(
                    "Mins > ${CORE_TEMP_ALERT_C}°", formatMmSs(stats.timeAboveCoreTempSec),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        stats.heatTrainingLoad?.let { htl ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                // Estimate of CORE's Heat Training Load, see HeatTrainingLoad.kt.
                DetailTile("Heat Training Load", "%.1f".format(htl), unit = "/ 10", modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

@Composable
private fun DetailTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = ErgOnSurface,
) {
    Column(
        modifier = modifier
            .background(ErgSurface, RoundedCornerShape(13.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = ErgOnSurface.copy(alpha = 0.6f),
        )
        Row {
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
            if (unit != null && value != "n/a") {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatSessionDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))

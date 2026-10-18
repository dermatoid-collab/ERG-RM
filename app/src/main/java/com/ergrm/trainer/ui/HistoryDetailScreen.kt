package com.ergrm.trainer.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
import com.ergrm.trainer.ui.theme.ErgHeatLoad
import com.ergrm.trainer.ui.theme.ErgHrLine
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.ui.theme.ErgProgressLine
import com.ergrm.trainer.ui.theme.ErgSkinTemp
import com.ergrm.trainer.ui.theme.ErgSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Opened from a History row: swipeable between every session in the list it was opened from
 *  (same list, same order the History screen is currently showing — sorted/filtered exactly as
 *  it was there), same left/right gesture as HRV-RM's own measurement-history swipe. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionDetailScreen(
    sessions: List<WorkoutSession>,
    initialIndex: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { sessions.size }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        SessionDetailPage(session = sessions[page], viewModel = viewModel, onBack = onBack, onImported = onImported)
    }
}

/** The full recorded trace plus a summary stats grid, for one session. No colored zone bars — a
 *  session only stores what was recorded (watts/HR/cadence/speed), not the original plan, so this
 *  mirrors the live workout chart's traces without the interval backdrop. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDetailPage(session: WorkoutSession, viewModel: MainViewModel, onBack: () -> Unit, onImported: () -> Unit) {
    val stats = remember(session.id) { computeSessionStats(session.samples, ftpWatts = session.ftpWatts) }
    val context = LocalContext.current
    val loadState by viewModel.workoutLoadState.collectAsState()
    var pendingImport by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    // Moved here from the History list row (Task: declutter the list, confirm-delete only once
    // you've actually opened the session) — onBack() after deleting, since the pager this screen
    // lives in (SessionDetailScreen) still holds the stale sessions list otherwise.
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(loadState) {
        if (pendingImport && loadState is WorkoutLoadState.Loaded) {
            pendingImport = false
            onImported()
        }
    }

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
                Text(
                    formatSessionDate(session.startEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                )
                // The import icon beside Share can squeeze this — clip + marquee instead of
                // wrapping or truncating with an ellipsis, same treatment as the Workout screen's
                // own Now/Next pill for a long name in a tight space.
                Text(
                    session.workoutName ?: "Free ride",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(delayMillis = 5_000, velocity = 15.dp),
                )
            }
            IconButton(onClick = {
                val name = session.workoutName
                if (name == null) {
                    importMessage = "This session has no recorded workout name, so it can't be matched to a library entry."
                } else {
                    pendingImport = true
                    viewModel.importOriginalWorkout(name, session.source) { message ->
                        pendingImport = false
                        importMessage = message
                    }
                }
            }) {
                Icon(Icons.Filled.Restore, contentDescription = "Re-import original workout")
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete session")
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

        importMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { importMessage = null },
                title = { Text("Can't re-import") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { importMessage = null }) { Text("OK") }
                },
            )
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete session?") },
                text = { Text("This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.deleteSession(session.id)
                        onBack()
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                },
            )
        }

        val hasCoreData = session.samples.any {
            it.coreTempC != null || it.skinTempC != null || it.heatStrainIndex != null
        }
        val hasWattsChart = session.samples.size >= 2

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Same breakpoint as Workout/Vitals: wider-than-tall means the phone is rotated, not
            // a configuration lookup, so this also behaves sensibly on a tablet.
            if (maxWidth > maxHeight) {
                SessionDetailLandscape(session = session, stats = stats, hasWattsChart = hasWattsChart, hasCoreData = hasCoreData)
            } else {
                // No scroll here (unlike most other screens): charts get weight(1f) each so they
                // share whatever vertical space is left after the summary tiles below them —
                // which stay at their natural height — rather than pushing the tiles off-screen
                // under a scrollbar.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasWattsChart) {
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
    }
}

/** Landscape session detail: header + SUMMARY stats (the "data") on the left, the recorded-trace
 *  charts side by side on the right — whichever of the two is actually present takes the full
 *  chart zone alone rather than splitting it with an empty neighbor. */
@Composable
private fun SessionDetailLandscape(session: WorkoutSession, stats: SessionStats, hasWattsChart: Boolean, hasCoreData: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "SUMMARY",
                style = MaterialTheme.typography.labelMedium,
                color = ErgOnSurface.copy(alpha = 0.7f),
            )
            SessionStatsLines(session, stats)
        }

        if (hasWattsChart || hasCoreData) {
            Row(
                modifier = Modifier.weight(0.7f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasWattsChart) {
                    Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                    Card(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
            }
        }
    }
}

@Composable
private fun ChartLegend(hasHr: Boolean, hasCadence: Boolean) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
    ) {
        LegendEntry("Watts", powerTraceColor(isLightTheme))
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
    // Canvas{} below is a plain DrawScope lambda, not @Composable, so it can't read these
    // theme-reactive tokens itself — resolved here in the composable body and captured instead.
    val onSurfaceColor = ErgOnSurface
    val surfaceColor = ErgSurface
    val powerColor = powerTraceColor(MaterialTheme.colorScheme.background.luminance() > 0.5f)

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
        drawPoints(points = wattsPoints, pointMode = PointMode.Polygon, color = powerColor, strokeWidth = 3f, cap = StrokeCap.Round)

        // Scrub indicator: vertical line at the selected sample + a tooltip box with that
        // sample's time, watts, HR and cadence, each colored to match its own trace above.
        selectedIndex?.let { idx ->
            val sample = samples.getOrNull(idx) ?: return@let
            val lineX = x(idx)
            drawLine(color = onSurfaceColor.copy(alpha = 0.5f), start = Offset(lineX, 0f), end = Offset(lineX, h), strokeWidth = 1.5f)

            val lines = buildList {
                add(formatScrubTime(sample.tSec) to onSurfaceColor)
                add("${sample.watts}W" to powerColor)
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
            drawRect(color = surfaceColor, topLeft = Offset(boxXClamped, 0f), size = Size(boxWidth, boxHeight))
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
    // Canvas{} below is a plain DrawScope lambda, not @Composable, so it can't read these
    // theme-reactive tokens itself — resolved here in the composable body and captured instead.
    val onSurfaceColor = ErgOnSurface
    val surfaceColor = ErgSurface

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
            drawLine(color = onSurfaceColor.copy(alpha = 0.5f), start = Offset(lineX, 0f), end = Offset(lineX, h), strokeWidth = 1.5f)

            val lines = buildList {
                add(formatScrubTime(sample.tSec) to onSurfaceColor)
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
            drawRect(color = surfaceColor, topLeft = Offset(boxXClamped, 0f), size = Size(boxWidth, boxHeight))
            measured.forEachIndexed { i, result ->
                drawText(result, topLeft = Offset(boxXClamped + padding, padding + rowHeight * i))
            }
        }
    }
}

/** Plain "label: value" line — the landscape data column's compact stand-in for a boxed
 *  DetailTile, so the stack of stats takes a fraction of the vertical space the grid of tiles
 *  would, leaving more width for the charts beside it via this column's own (smaller) weight. */
@Composable
private fun StatLine(label: String, value: String, valueColor: Color = ErgOnSurface, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = ErgOnSurface.copy(alpha = 0.65f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

/** The full stat set for a session, as (label, value, color) in display order — shared by
 *  [SessionStatsLines] (landscape, one column) and [StatsGrid] (portrait, split into two columns),
 *  so the two orientations can never drift out of sync on what they show or in what order. SS Time
 *  and Z4+ Time sit right after NP in both, per request. @Composable because ErgOnSurface/ErgHrLine/
 *  ErgProgressLine/ErgHeatLoad are themselves @Composable getters (theme-reactive). */
@Composable
private fun sessionStatLines(session: WorkoutSession, stats: SessionStats): List<Triple<String, String, Color>> = buildList {
    add(Triple("Duration", formatWorkoutDuration(session.durationSec), ErgOnSurface))
    add(Triple("Distance", stats.distanceKm?.let { "%.1f km".format(it) } ?: "n/a", ErgOnSurface))
    add(Triple("Avg speed", stats.avgSpeedKmh?.let { "%.1f km/h".format(it) } ?: "n/a", ErgOnSurface))
    add(Triple("Work", "${stats.totalKj} kJ", ErgOnSurface))
    add(Triple("Avg watts", "${stats.avgWatts} W", ErgOnSurface))
    add(Triple("NP", stats.normalizedWatts?.let { "$it W" } ?: "n/a", ErgOnSurface))
    add(Triple("SS Time", stats.ssTimeSec?.let { formatMmSs(it) } ?: "n/a", ErgOnSurface))
    add(Triple("Z4+ Time", stats.z4PlusTimeSec?.let { formatMmSs(it) } ?: "n/a", ErgOnSurface))
    add(Triple("Max HR", stats.maxHrBpm?.let { "$it bpm" } ?: "n/a", if (stats.maxHrBpm != null) ErgHrLine else ErgOnSurface))
    add(Triple("Avg HR", stats.avgHrBpm?.let { "$it bpm" } ?: "n/a", if (stats.avgHrBpm != null) ErgHrLine else ErgOnSurface))
    add(
        Triple(
            "Avg cadence", stats.avgCadenceRpm?.let { "$it rpm" } ?: "n/a",
            if (stats.avgCadenceRpm != null) ErgProgressLine else ErgOnSurface,
        ),
    )
    if (stats.avgCoreTempC != null) {
        add(Triple("Avg core temp", "%.2f°C".format(stats.avgCoreTempC), ErgOnSurface))
        add(Triple("Max core temp", stats.maxCoreTempC?.let { "%.2f°C".format(it) } ?: "n/a", ErgOnSurface))
        add(Triple("Mins > ${CORE_TEMP_ALERT_C}°", formatMmSs(stats.timeAboveCoreTempSec), ErgOnSurface))
    }
    // Prefers the value stored on the session itself (computed once at save time, see
    // MainViewModel.exitWorkout) over recomputing it here — falls back to the live derivation
    // only for sessions saved before that field existed.
    val htl = session.heatTrainingLoad ?: stats.heatTrainingLoad
    if (htl != null || stats.tss != null) {
        add(Triple("Core HTL", htl?.let { "%.1f/10".format(it) } ?: "n/a", if (htl != null) ErgHeatLoad else ErgOnSurface))
        add(Triple("TSS", stats.tss?.toString() ?: "n/a", ErgOnSurface))
    }
}

/** Landscape stand-in for [StatsGrid]: the same stats as one flat stack of "label: value" lines
 *  instead of a grid of tiles. */
@Composable
private fun SessionStatsLines(session: WorkoutSession, stats: SessionStats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        sessionStatLines(session, stats).forEach { (label, value, color) -> StatLine(label, value, color) }
    }
}

/** Portrait's own stat list, as two side-by-side columns instead of [SessionStatsLines]' single
 *  one — a 3-tiles-per-row DetailTile grid ran out of width the moment SS Time/Z4+ Time were added,
 *  so this switches to the same compact StatLine style landscape already uses, split right after
 *  Z4+ Time: everything through it on the left, everything after it on the right. */
@Composable
private fun StatsGrid(session: WorkoutSession, stats: SessionStats) {
    val lines = sessionStatLines(session, stats)
    val splitIndex = lines.indexOfFirst { it.first == "Z4+ Time" } + 1
    val (left, right) = lines.take(splitIndex) to lines.drop(splitIndex)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            left.forEach { (label, value, color) -> StatLine(label, value, color) }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            right.forEach { (label, value, color) -> StatLine(label, value, color) }
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatSessionDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))

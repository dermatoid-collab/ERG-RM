package com.ergrm.trainer.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ergrm.trainer.history.SessionSample
import com.ergrm.trainer.history.SessionStats
import com.ergrm.trainer.history.WorkoutSession
import com.ergrm.trainer.history.computeSessionStats
import com.ergrm.trainer.ui.theme.ErgAboveTarget
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.ui.theme.ErgProgressLine
import com.ergrm.trainer.ui.theme.ErgSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    context.startActivity(Intent.createChooser(sendIntent, "Export session"))
                }
            }) {
                Icon(Icons.Filled.Share, contentDescription = "Export as TCX")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (session.samples.size >= 2) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp, 10.dp, 10.dp, 4.dp)) {
                        SessionDetailChart(
                            samples = session.samples,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                        ChartLegend(hasHr = stats.avgHrBpm != null, hasCadence = stats.avgCadenceRpm != null)
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
        if (hasHr) LegendEntry("HR", ErgAboveTarget)
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
    Canvas(modifier = modifier) {
        val n = samples.size
        if (n < 2) return@Canvas
        val w = size.width
        val h = size.height
        fun x(i: Int) = w * i / (n - 1).toFloat()

        val maxWatts = samples.maxOf { it.watts }.coerceAtLeast(50)
        fun yWatts(v: Int) = h - h * (v.toFloat() / maxWatts).coerceIn(0f, 1f)

        val hrs = samples.mapNotNull { it.hrBpm }
        if (hrs.size >= 2) {
            val hrMin = (hrs.min() - 10).coerceAtLeast(0)
            val hrRange = (hrs.max() - hrMin).coerceAtLeast(1)
            fun yHr(v: Int) = h - h * ((v - hrMin).toFloat() / hrRange).coerceIn(0f, 1f)
            val points = samples.mapIndexedNotNull { i, s -> s.hrBpm?.let { Offset(x(i), yHr(it)) } }
            drawPoints(points = points, pointMode = PointMode.Polygon, color = ErgAboveTarget, strokeWidth = 3f, cap = StrokeCap.Round)
        }

        val cadences = samples.mapNotNull { it.cadenceRpm }
        if (cadences.size >= 2) {
            val cadMax = cadences.max().coerceAtLeast(10)
            fun yCad(v: Int) = h - h * (v.toFloat() / cadMax).coerceIn(0f, 1f)
            val points = samples.mapIndexedNotNull { i, s -> s.cadenceRpm?.let { Offset(x(i), yCad(it)) } }
            drawPoints(points = points, pointMode = PointMode.Polygon, color = ErgProgressLine, strokeWidth = 2.5f, cap = StrokeCap.Round)
        }

        val wattsPoints = samples.mapIndexed { i, s -> Offset(x(i), yWatts(s.watts)) }
        drawPoints(points = wattsPoints, pointMode = PointMode.Polygon, color = Color.White, strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

@Composable
private fun StatsGrid(session: WorkoutSession, stats: SessionStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile("Duration", formatWorkoutDuration(session.durationSec), modifier = Modifier.weight(1f))
            DetailTile("Avg watts", "${stats.avgWatts}", unit = "W", modifier = Modifier.weight(1f))
            DetailTile("NP", stats.normalizedWatts?.let { "$it" } ?: "n/a", unit = "W", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile("Work", "${stats.totalKj}", unit = "kJ", modifier = Modifier.weight(1f))
            DetailTile(
                "Avg HR", stats.avgHrBpm?.let { "$it" } ?: "n/a", unit = "bpm", modifier = Modifier.weight(1f),
                valueColor = if (stats.avgHrBpm != null) ErgAboveTarget else ErgOnSurface,
            )
            DetailTile(
                "Max HR", stats.maxHrBpm?.let { "$it" } ?: "n/a", unit = "bpm", modifier = Modifier.weight(1f),
                valueColor = if (stats.maxHrBpm != null) ErgAboveTarget else ErgOnSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DetailTile("Avg cadence", stats.avgCadenceRpm?.let { "$it" } ?: "n/a", unit = "rpm", modifier = Modifier.weight(1f))
            DetailTile("Avg speed", stats.avgSpeedKmh?.let { "%.1f".format(it) } ?: "n/a", unit = "km/h", modifier = Modifier.weight(1f))
            DetailTile("Distance", stats.distanceKm?.let { "%.1f".format(it) } ?: "n/a", unit = "km", modifier = Modifier.weight(1f))
        }
    }
}

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

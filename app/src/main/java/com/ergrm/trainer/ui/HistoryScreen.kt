package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.history.SessionSample
import com.ergrm.trainer.history.WorkoutSession
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgOnSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val sessions by viewModel.sessionHistory.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshHistory() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("History", style = MaterialTheme.typography.titleLarge)

        if (sessions.isEmpty()) {
            Text(
                "No recorded sessions yet — completed workouts are saved here automatically when you tap Stop.",
                style = MaterialTheme.typography.bodyMedium,
                color = ErgOnSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session, onDelete = { viewModel.deleteSession(session.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkoutSession, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.workoutName ?: "Free ride", style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatDate(session.startEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatDuration(session.durationSec), style = MaterialTheme.typography.bodySmall)
                    Text("${session.avgWatts}W avg", style = MaterialTheme.typography.bodySmall)
                    Text("${session.maxWatts}W max", style = MaterialTheme.typography.bodySmall)
                    session.avgHrBpm?.let {
                        Text("$it bpm avg", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (session.samples.size >= 2) {
                SessionSparkline(
                    session.samples,
                    modifier = Modifier
                        .width(70.dp)
                        .height(36.dp)
                        .padding(horizontal = 8.dp),
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete session")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete session?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionSparkline(samples: List<SessionSample>, modifier: Modifier = Modifier) {
    val maxWatts = remember(samples) { samples.maxOfOrNull { it.watts }?.coerceAtLeast(1) ?: 1 }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lastIndex = (samples.size - 1).coerceAtLeast(1)
        val points = samples.mapIndexed { i, s ->
            Offset(w * i / lastIndex.toFloat(), h - h * (s.watts.toFloat() / maxWatts).coerceIn(0f, 1f))
        }
        drawPoints(points = points, pointMode = PointMode.Polygon, color = ErgAccent, strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

private fun formatDate(epochMillis: Long): String {
    val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm".format(m)
}

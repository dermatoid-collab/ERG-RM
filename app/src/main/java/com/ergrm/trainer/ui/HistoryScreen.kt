package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.ergrm.trainer.history.totalKj
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.ui.theme.ErgOnSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistorySortField(val label: String) {
    DATE("Date"),
    DURATION("Duration"),
    AVG_WATTS("W avg"),
    AVG_HR("BPM avg"),
    KJ("kJ"),
}

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val sessions by viewModel.sessionHistory.collectAsState()
    var selectedSession by remember { mutableStateOf<WorkoutSession?>(null) }
    // Newest-first by default — the same order the underlying list already came in, so picking
    // this as the default keeps existing behavior for anyone who never opens the sort menu.
    var sortField by remember { mutableStateOf(HistorySortField.DATE) }
    var sortDescending by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.refreshHistory()
        // Silently pulls in anything backed up from another device or restored after a
        // reinstall — see MainViewModel.syncHistoryFromBackupFolder for why this is cheap.
        viewModel.syncHistoryFromBackupFolder()
    }

    val selected = selectedSession
    if (selected != null) {
        SessionDetailScreen(session = selected, viewModel = viewModel, onBack = { selectedSession = null })
        return
    }

    val sortedSessions = remember(sessions, sortField, sortDescending) {
        val comparator: Comparator<WorkoutSession> = when (sortField) {
            HistorySortField.DATE -> compareBy { it.startEpochMillis }
            HistorySortField.DURATION -> compareBy { it.durationSec }
            HistorySortField.AVG_WATTS -> compareBy { it.avgWatts }
            // compareValues is nulls-first by default, same as every other nullable stat sorted
            // anywhere else in the app.
            HistorySortField.AVG_HR -> Comparator { a, b -> compareValues(a.avgHrBpm, b.avgHrBpm) }
            HistorySortField.KJ -> compareBy { it.totalKj }
        }
        val sorted = sessions.sortedWith(comparator)
        if (sortDescending) sorted.reversed() else sorted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("History", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HistorySortMenu(
                    field = sortField,
                    descending = sortDescending,
                    onSelect = { field ->
                        if (field == sortField) {
                            sortDescending = !sortDescending
                        } else {
                            sortField = field
                            sortDescending = true
                        }
                    },
                )
                IconButton(onClick = { viewModel.syncHistoryFromBackupFolder() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sync from backup folder")
                }
            }
        }

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
                items(sortedSessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onClick = { selectedSession = session },
                        onDelete = { viewModel.deleteSession(session.id) },
                    )
                }
            }
        }
    }
}

/** Tapping the already-selected field flips its direction instead of re-selecting it, so toggling
 *  sort order never needs a second menu open. A freshly-picked field always starts descending
 *  (newest/highest first) — the more useful default for a date or any of these stats. */
@Composable
private fun HistorySortMenu(field: HistorySortField, descending: Boolean, onSelect: (HistorySortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(field.label)
            Icon(
                if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = if (descending) "Descending" else "Ascending",
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HistorySortField.entries.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.label) },
                    onClick = {
                        expanded = false
                        onSelect(f)
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkoutSession, onClick: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatDate(session.startEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                )
                Text(session.workoutName ?: "Free ride", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatDuration(session.durationSec), style = MaterialTheme.typography.bodySmall)
                    Text("${session.avgWatts}W avg", style = MaterialTheme.typography.bodySmall)
                    session.avgHrBpm?.let {
                        Text("$it bpm avg", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${session.totalKj}kJ", style = MaterialTheme.typography.bodySmall)
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

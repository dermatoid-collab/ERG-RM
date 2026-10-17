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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
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
    NAME("Name"),
    DATE("Date"),
    DURATION("Duration"),
    AVG_WATTS("W avg"),
    AVG_HR("BPM avg"),
    KJ("kJ"),
}

@Composable
fun HistoryScreen(viewModel: MainViewModel, onImported: () -> Unit = {}) {
    val sessions by viewModel.sessionHistory.collectAsState()
    // The index into sortedSessions (below) that's open, not the session itself — so the detail
    // screen's own left/right swipe (Task: swipe through history like HRV-RM) can page through
    // this exact list/order without this screen needing to track "what comes next" itself.
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // Date, descending (most recent first) — History's own default, unlike the Library screens'
    // (name, ascending).
    var sortField by remember { mutableStateOf(HistorySortField.DATE) }
    var sortAscending by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshHistory()
        // Silently pulls in anything backed up from another device or restored after a
        // reinstall — see MainViewModel.syncHistoryFromBackupFolder for why this is cheap.
        viewModel.syncHistoryFromBackupFolder()
    }

    val visibleSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) {
            sessions
        } else {
            sessions.filter { (it.workoutName ?: "Free ride").contains(searchQuery, ignoreCase = true) }
        }
    }
    val sortedSessions = remember(visibleSessions, sortField, sortAscending) {
        val primary: Comparator<WorkoutSession> = when (sortField) {
            HistorySortField.NAME -> compareBy { (it.workoutName ?: "").lowercase() }
            HistorySortField.DATE -> compareBy { it.startEpochMillis }
            HistorySortField.DURATION -> compareBy { it.durationSec }
            HistorySortField.AVG_WATTS -> compareBy { it.avgWatts }
            // compareValues is nulls-first by default, same as every other nullable stat sorted
            // anywhere else in the app.
            HistorySortField.AVG_HR -> Comparator { a, b -> compareValues(a.avgHrBpm, b.avgHrBpm) }
            HistorySortField.KJ -> compareBy { it.totalKj }
        }
        // Two sessions tied on the primary field (e.g. the same workout name ridden twice) fall
        // back to date-time, so they still land in a stable, predictable order instead of
        // whatever sortedWith happens to leave them in.
        val comparator = if (sortField == HistorySortField.DATE) primary else primary.thenBy { it.startEpochMillis }
        val sorted = visibleSessions.sortedWith(comparator)
        if (sortAscending) sorted else sorted.reversed()
    }

    val selected = selectedIndex
    if (selected != null && selected in sortedSessions.indices) {
        SessionDetailScreen(
            sessions = sortedSessions,
            initialIndex = selected,
            viewModel = viewModel,
            onBack = { selectedIndex = null },
            onImported = {
                selectedIndex = null
                onImported()
            },
        )
        return
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
            if (!searchActive) {
                Text("History", style = MaterialTheme.typography.titleLarge)
            }
            // Icon-only sort/direction controls (no field name shown inline) so sorting doesn't
            // cost any extra header width. Search expands into the same full-width field, with
            // the same close-to-collapse behavior, as every other workout-source list screen's
            // own SearchableHeaderRow — just inlined here since History isn't one of those
            // screens (no "Other sources" menu) and shares this single title+icons row instead of
            // that row's separate one above its own title.
            Row(
                modifier = if (searchActive) Modifier.weight(1f) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchActive) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Search") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = ErgOnSurface.copy(alpha = 0.3f),
                        ),
                    )
                    IconButton(onClick = {
                        searchQuery = ""
                        searchActive = false
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                } else {
                    HistorySortFieldMenu(selected = sortField, onSelect = { sortField = it })
                    IconButton(onClick = { sortAscending = !sortAscending }) {
                        Icon(
                            if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = if (sortAscending) "Sort ascending" else "Sort descending",
                        )
                    }
                    IconButton(onClick = { searchActive = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { viewModel.syncHistoryFromBackupFolder() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync from backup folder")
                    }
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
        } else if (sortedSessions.isEmpty()) {
            Text(
                "No results for \"$searchQuery\".",
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
                itemsIndexed(sortedSessions, key = { _, it -> it.id }) { index, session ->
                    SessionRow(
                        session = session,
                        onClick = { selectedIndex = index },
                    )
                }
            }
        }
    }
}

/** A single Sort icon instead of a labeled button — the active field only shows once the menu is
 *  open (bolded there), so this costs no header width of its own. Direction is its own separate
 *  icon button next to this one, not folded in here. */
@Composable
private fun HistorySortFieldMenu(selected: HistorySortField, onSelect: (HistorySortField) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = "Sort by")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HistorySortField.entries.forEach { f ->
                DropdownMenuItem(
                    text = {
                        Text(f.label, fontWeight = if (f == selected) FontWeight.Bold else FontWeight.Normal)
                    },
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
private fun SessionRow(session: WorkoutSession, onClick: () -> Unit) {
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
        }
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

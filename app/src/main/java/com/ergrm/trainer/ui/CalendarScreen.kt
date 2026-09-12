package com.ergrm.trainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.intervals.CalendarWorkout
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.workout.WorkoutStep
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun CalendarScreen(viewModel: MainViewModel, onPicked: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val calendarState by viewModel.calendarState.collectAsState()

    LaunchedEffect(Unit) { viewModel.fetchCalendarWorkouts() }

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
            Text("Calendar", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.fetchCalendarWorkouts() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        if (!settings.intervalsConfigured) {
            Text(
                "Set your Intervals.icu API key and athlete ID in Settings first.",
                style = MaterialTheme.typography.bodyMedium,
                color = ErgOnSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        when (val state = calendarState) {
            CalendarUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
            is CalendarUiState.Error -> {
                Text(
                    "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is CalendarUiState.Loaded -> {
                if (state.workouts.isEmpty()) {
                    Text(
                        "No planned bike workouts this week or next.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErgOnSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    CalendarWorkoutList(
                        workouts = state.workouts,
                        ftpWatts = settings.ftpWatts,
                        fetchPreview = viewModel::fetchCalendarWorkoutPreview,
                        onPick = { workout ->
                            viewModel.loadCalendarWorkout(workout)
                            onPicked()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarWorkoutList(
    workouts: List<CalendarWorkout>,
    ftpWatts: Int,
    fetchPreview: suspend (Long) -> List<WorkoutStep>,
    onPick: (CalendarWorkout) -> Unit,
) {
    val today = LocalDate.now()
    val sundayThisWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val (currentWeek, nextWeek) = workouts.partition { it.date <= sundayThisWeek }
    val currentWeekByDay = currentWeek.groupBy { it.date }.toSortedMap()
    val nextWeekByDay = nextWeek.groupBy { it.date }.toSortedMap()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (currentWeekByDay.isNotEmpty()) {
            item { WeekHeader("Current Week") }
            currentWeekByDay.forEach { (date, dayWorkouts) -> daySection(date, today, dayWorkouts, ftpWatts, fetchPreview, onPick) }
        }
        if (nextWeekByDay.isNotEmpty()) {
            item { WeekHeader("Next Week") }
            nextWeekByDay.forEach { (date, dayWorkouts) -> daySection(date, today, dayWorkouts, ftpWatts, fetchPreview, onPick) }
        }
    }
}

private fun LazyListScope.daySection(
    date: LocalDate,
    today: LocalDate,
    dayWorkouts: List<CalendarWorkout>,
    ftpWatts: Int,
    fetchPreview: suspend (Long) -> List<WorkoutStep>,
    onPick: (CalendarWorkout) -> Unit,
) {
    item(key = "day-$date") { DayHeader(date, today) }
    items(dayWorkouts, key = { it.eventId }) { workout ->
        CalendarWorkoutRow(
            workout = workout,
            isPast = date < today,
            ftpWatts = ftpWatts,
            fetchPreview = fetchPreview,
            onPick = { onPick(workout) },
        )
    }
}

@Composable
private fun WeekHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = ErgOnSurface,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val label = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        else -> date.format(dayHeaderFormatter)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = ErgOnSurface.copy(alpha = if (date < today) 0.5f else 0.8f),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/** Past days are dimmed to show they've already gone by, but stay loadable — e.g. to redo a
 *  skipped session or repeat an earlier one on demand. */
@Composable
private fun CalendarWorkoutRow(
    workout: CalendarWorkout,
    isPast: Boolean,
    ftpWatts: Int,
    fetchPreview: suspend (Long) -> List<WorkoutStep>,
    onPick: () -> Unit,
) {
    val alpha = if (isPast) 0.55f else 1f
    var previewSteps by remember(workout.eventId) { mutableStateOf<List<WorkoutStep>?>(null) }
    LaunchedEffect(workout.eventId) { previewSteps = fetchPreview(workout.eventId) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // weight(1f) + maxLines/ellipsis: an unweighted wrapping Text in a Row can report
                // its measured width as the full row width and squeeze the Button that follows it
                // down to nothing — confirmed on a real device for the Library's equivalent row
                // with a long title. Bound the text instead of letting a long name risk the same.
                Text(
                    workout.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                Button(onClick = onPick) { Text("Load") }
            }
            // Duration prefers the parsed preview's own total (once it arrives) over the
            // calendar event's movingTimeSec, so it stays consistent with the chart drawn from
            // the same steps; movingTimeSec is just what's shown while the preview is loading.
            val steps = previewSteps
            val durationSec = steps?.sumOf { it.durationSec }?.takeIf { it > 0 } ?: workout.movingTimeSec
            if (durationSec != null && durationSec > 0) {
                Text(
                    formatWorkoutDuration(durationSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface.copy(alpha = alpha),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!steps.isNullOrEmpty()) {
                WorkoutMiniChart(
                    steps = steps,
                    ftpWatts = ftpWatts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

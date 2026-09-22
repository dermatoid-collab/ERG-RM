package com.ergrm.trainer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.intervals.LibraryFolderGroup
import com.ergrm.trainer.intervals.LibraryWorkout
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.workout.WorkoutStep
import kotlinx.coroutines.delay

@Composable
fun IntervalsLibraryScreen(
    viewModel: MainViewModel,
    onPicked: () -> Unit = {},
    otherSources: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.intervalsLibraryState.collectAsState()
    val loadState by viewModel.workoutLoadState.collectAsState()
    // Tracks the workout we asked to load, so this screen can show its own progress/error
    // instead of closing immediately and hoping the result is visible elsewhere — closing
    // unconditionally on tap used to hide load failures (and made "nothing happened" reports
    // impossible to tell apart from a real failure without a second screenshot round-trip).
    var pendingWorkoutId by remember { mutableStateOf<Long?>(null) }
    // Which folders are expanded — session-only (resets to all-collapsed whenever this
    // composable enters composition fresh), not persisted to disk.
    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) { viewModel.fetchIntervalsLibrary() }

    LaunchedEffect(loadState) {
        if (pendingWorkoutId != null && loadState is WorkoutLoadState.Loaded) {
            pendingWorkoutId = null
            onPicked()
        }
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
            Text(
                "Library (Intervals.icu)",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkoutSourceMenu(otherSources)
                IconButton(onClick = { viewModel.fetchIntervalsLibrary() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
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

        when (val s = state) {
            IntervalsLibraryUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
            is IntervalsLibraryUiState.Error -> {
                Text(
                    "Error: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is IntervalsLibraryUiState.Loaded -> {
                if (s.folders.isEmpty()) {
                    Text(
                        "No saved workouts found in your Intervals.icu library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErgOnSurface,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    val sortedFolders = remember(s.folders) { s.folders.sortedBy { it.name.lowercase() } }
                    val rows = remember(sortedFolders, expandedFolders) {
                        buildLibraryRows(sortedFolders, expandedFolders)
                    }
                    val listState = rememberLazyListState()

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(rows, key = { it.key }) { row ->
                                when (row) {
                                    is LibraryRow.Header -> FolderHeader(
                                        name = row.folderName,
                                        expanded = row.folderName in expandedFolders,
                                        onToggle = {
                                            expandedFolders = if (row.folderName in expandedFolders) {
                                                expandedFolders - row.folderName
                                            } else {
                                                expandedFolders + row.folderName
                                            }
                                        },
                                    )
                                    is LibraryRow.Empty -> Text(
                                        "No workouts in this folder.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErgOnSurface,
                                    )
                                    is LibraryRow.Item -> {
                                        val workout = row.workout
                                        val isPending = pendingWorkoutId == workout.workoutId
                                        LibraryWorkoutRow(
                                            workout = workout,
                                            ftpWatts = settings.ftpWatts,
                                            fetchPreview = viewModel::fetchLibraryWorkoutPreview,
                                            isLoading = isPending && loadState is WorkoutLoadState.Loading,
                                            errorMessage = (loadState as? WorkoutLoadState.Error)?.message.takeIf { isPending },
                                            onPick = {
                                                pendingWorkoutId = workout.workoutId
                                                viewModel.loadIntervalsLibraryWorkout(workout)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        AutoHideScrollbar(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 12.dp, horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed class LibraryRow {
    abstract val key: String

    data class Header(val folderName: String) : LibraryRow() {
        override val key get() = "header-$folderName"
    }
    data class Empty(val folderName: String) : LibraryRow() {
        override val key get() = "empty-$folderName"
    }
    data class Item(val workout: LibraryWorkout) : LibraryRow() {
        override val key get() = "item-${workout.workoutId}"
    }
}

private fun buildLibraryRows(folders: List<LibraryFolderGroup>, expanded: Set<String>): List<LibraryRow> = buildList {
    folders.forEach { folder ->
        add(LibraryRow.Header(folder.name))
        if (folder.name in expanded) {
            if (folder.workouts.isEmpty()) {
                add(LibraryRow.Empty(folder.name))
            } else {
                folder.workouts.forEach { add(LibraryRow.Item(it)) }
            }
        }
    }
}

@Composable
private fun FolderHeader(name: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = ErgOnSurface,
            modifier = Modifier.size(20.dp),
        )
        Text(
            name.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = ErgOnSurface,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** A thin vertical scroll-position indicator that fades in while [listState] is scrolling and
 *  fades out shortly after it stops — just a "where am I" cue, not a draggable thumb. */
@Composable
private fun AutoHideScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            visible = true
        } else if (visible) {
            delay(800)
            visible = false
        }
    }
    val alpha by animateFloatAsState(targetValue = if (visible) 0.55f else 0f, label = "scrollbarAlpha")
    val totalItems = listState.layoutInfo.totalItemsCount
    if (alpha <= 0.01f || totalItems <= 0) return

    BoxWithConstraints(modifier = modifier.width(4.dp)) {
        val trackHeight = maxHeight
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val fractionSize = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.06f, 1f)
        val maxStartFraction = (1f - fractionSize).coerceAtLeast(0f)
        val fractionStart = (listState.firstVisibleItemIndex.toFloat() / totalItems.toFloat())
            .coerceIn(0f, maxStartFraction)

        Box(
            modifier = Modifier
                .offset(y = trackHeight * fractionStart)
                .height(trackHeight * fractionSize)
                .width(4.dp)
                .alpha(alpha)
                .background(ErgOnSurface, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun LibraryWorkoutRow(
    workout: LibraryWorkout,
    ftpWatts: Int,
    fetchPreview: suspend (LibraryWorkout) -> List<WorkoutStep>,
    isLoading: Boolean,
    errorMessage: String?,
    onPick: () -> Unit,
) {
    var previewSteps by remember(workout.workoutId) { mutableStateOf<List<WorkoutStep>?>(null) }
    LaunchedEffect(workout.workoutId) { previewSteps = fetchPreview(workout) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The real cause of the "invisible" Load button: a long title wraps to 2 lines,
                // and an un-weighted Text in a Row reports its measured width as the full
                // available width whenever it wraps — squeezing the Button that follows it down
                // to nothing. weight(1f) bounds Text to the space left after the Button's own
                // (fixed) size instead, and maxLines/overflow keeps very long titles in check.
                Text(
                    workout.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                // A real device showed this row's trailing slot completely blank (but still
                // clickable!) when it alternated between two different composables (Button vs.
                // CircularProgressIndicator) here — keeping one Button always composed and only
                // swapping its inner content avoids whatever that was.
                Button(onClick = onPick, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Load")
                    }
                }
            }
            val steps = previewSteps
            if (!steps.isNullOrEmpty()) {
                Text(
                    formatWorkoutDuration(steps.sumOf { it.durationSec }),
                    style = MaterialTheme.typography.bodySmall,
                    color = ErgOnSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                WorkoutMiniChart(
                    steps = steps,
                    ftpWatts = ftpWatts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(top = 6.dp),
                )
            }
            if (errorMessage != null) {
                Text(
                    "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

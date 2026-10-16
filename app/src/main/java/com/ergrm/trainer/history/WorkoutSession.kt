package com.ergrm.trainer.history

import kotlinx.serialization.Serializable

@Serializable
data class SessionSample(
    val tSec: Int,
    val watts: Int,
    val hrBpm: Int?,
    val cadenceRpm: Int?,
    // Absent (null) on sessions saved before speed capture was added — the detail view shows
    // average speed/distance as unavailable for those rather than a misleading zero.
    val speedKmh: Float? = null,
    // CORE sensor readings, time-aligned with the rest of this sample — absent (default null) on
    // sessions saved before this was added, or any second with no CORE sensor connected.
    val coreTempC: Float? = null,
    val skinTempC: Float? = null,
    val heatStrainIndex: Float? = null,
)

/** Where a loaded workout actually came from, if it's traceable at all — recorded on
 *  [WorkoutSession] at save time from the active [com.ergrm.trainer.ui.WorkoutLoadState.Loaded]'s
 *  own source, so a later re-import (MainViewModel.importOriginalWorkout) can go straight back to
 *  the exact same workout by id instead of a name search. Absent on a manual free ride, a GPX
 *  route, or any session saved before this was added — those still fall back to the name search. */
@Serializable
sealed interface WorkoutSource {
    @Serializable
    data class IntervalsLibrary(val workoutId: Long) : WorkoutSource

    @Serializable
    data class IntervalsCalendarEvent(val eventId: Long) : WorkoutSource

    @Serializable
    data class LocalFile(val uri: String) : WorkoutSource
}

/** A completed (started and then stopped) workout session, saved locally for the history list. */
@Serializable
data class WorkoutSession(
    val id: String,
    val startEpochMillis: Long,
    val durationSec: Int,
    val workoutName: String?,
    val avgWatts: Int,
    val maxWatts: Int,
    val avgHrBpm: Int?,
    val avgCadenceRpm: Int?,
    val samples: List<SessionSample> = emptyList(),
    // Computed once at save time (see HeatTrainingLoad.compute) and stored here rather than only
    // derived live via SessionStats, so it round-trips through the JSON backup/export — absent
    // (default null) on sessions saved before this was added, or with no usable CORE + HR data.
    val heatTrainingLoad: Float? = null,
    // The rider's FTP in effect *during* this ride, not whatever it is now — so TSS (see
    // SessionStats.tss) stays correct for an old session even after the rider's FTP changes.
    // Absent (default null) on sessions saved before this was added.
    val ftpWatts: Int? = null,
    val source: WorkoutSource? = null,
)

/** Energy for the whole ride from its stored average power and duration — the same "watts summed
 *  over ~1-second samples" assumption [computeSessionStats]'s own totalKj relies on, without
 *  re-walking every sample just to show this on a history list row. */
val WorkoutSession.totalKj: Int get() = (avgWatts.toLong() * durationSec / 1000).toInt()

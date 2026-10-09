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
)

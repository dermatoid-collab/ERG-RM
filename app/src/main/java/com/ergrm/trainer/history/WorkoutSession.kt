package com.ergrm.trainer.history

import kotlinx.serialization.Serializable

@Serializable
data class SessionSample(
    val tSec: Int,
    val watts: Int,
    val hrBpm: Int?,
    val cadenceRpm: Int?,
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
)

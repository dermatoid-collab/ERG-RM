package com.ergrm.trainer.history

import kotlin.math.pow
import kotlin.math.roundToInt

/** Derived summary numbers for the history detail view, computed straight from a session's
 *  recorded samples rather than duplicating them into stored fields — one source of truth. */
data class SessionStats(
    val avgWatts: Int,
    val maxWatts: Int,
    val normalizedWatts: Int?,
    val totalKj: Int,
    val avgHrBpm: Int?,
    val maxHrBpm: Int?,
    val avgCadenceRpm: Int?,
    val avgSpeedKmh: Float?,
    val distanceKm: Float?,
)

fun computeSessionStats(samples: List<SessionSample>): SessionStats {
    val watts = samples.map { it.watts }
    val hrs = samples.mapNotNull { it.hrBpm }
    val cadences = samples.mapNotNull { it.cadenceRpm }
    val speeds = samples.mapNotNull { it.speedKmh }
    return SessionStats(
        avgWatts = if (watts.isNotEmpty()) watts.average().roundToInt() else 0,
        maxWatts = watts.maxOrNull() ?: 0,
        normalizedWatts = normalizedPower(watts),
        // Each sample is ~1 second of recorded riding, so summing watts directly gives joules.
        totalKj = (watts.sumOf { it.toLong() } / 1000.0).roundToInt(),
        avgHrBpm = if (hrs.isNotEmpty()) hrs.average().roundToInt() else null,
        maxHrBpm = hrs.maxOrNull(),
        avgCadenceRpm = if (cadences.isNotEmpty()) cadences.average().roundToInt() else null,
        avgSpeedKmh = if (speeds.isNotEmpty()) speeds.average().toFloat() else null,
        // Each sample contributes speedKmh/3600 km for its ~1 second — absent (not zero) when no
        // session sample ever carried a speed, e.g. one saved before speed capture was added.
        distanceKm = if (speeds.isNotEmpty()) (speeds.sum() / 3600.0).toFloat() else null,
    )
}

/** Standard Normalized Power: a 30-second rolling average of power, raised to the 4th power,
 *  averaged, then 4th-rooted — this rewards the physiological cost of surges that a plain average
 *  hides. Needs at least 30 one-second samples; shorter sessions have no meaningful window. */
private fun normalizedPower(watts: List<Int>): Int? {
    val window = 30
    if (watts.size < window) return null
    var windowSum = watts.take(window).sum()
    val rollingAverages = mutableListOf(windowSum / window.toDouble())
    for (i in window until watts.size) {
        windowSum += watts[i] - watts[i - window]
        rollingAverages += windowSum / window.toDouble()
    }
    val meanFourthPower = rollingAverages.sumOf { it.pow(4) } / rollingAverages.size
    return meanFourthPower.pow(0.25).roundToInt()
}

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
    val avgCoreTempC: Float?,
    val maxCoreTempC: Float?,
    // Seconds spent above CORE_TEMP_ALERT_C — each sample is ~1 recorded second, so this is just
    // a count of samples over the threshold, same assumption [totalKj] already relies on.
    val timeAboveCoreTempSec: Int,
    // Estimated CORE Heat Training Load (0-10) — null without CORE + heart-rate data.
    val heatTrainingLoad: Float?,
    // Training Stress Score (Coggan) — null without both an FTP and enough samples for NP.
    val tss: Int?,
)

/** Heat-strain alert threshold for [SessionStats.timeAboveCoreTempSec], as requested. */
const val CORE_TEMP_ALERT_C = 38.3f

/** [ftpWatts] should be the FTP in effect *during* the ride, not necessarily the rider's current
 *  one — see [WorkoutSession.ftpWatts], which is what the history view passes in. */
fun computeSessionStats(samples: List<SessionSample>, ftpWatts: Int? = null): SessionStats {
    val watts = samples.map { it.watts }
    val hrs = samples.mapNotNull { it.hrBpm }
    val cadences = samples.mapNotNull { it.cadenceRpm }
    val speeds = samples.mapNotNull { it.speedKmh }
    val coreTemps = samples.mapNotNull { it.coreTempC }
    val normalizedWatts = normalizedPower(watts)
    return SessionStats(
        avgWatts = if (watts.isNotEmpty()) watts.average().roundToInt() else 0,
        maxWatts = watts.maxOrNull() ?: 0,
        normalizedWatts = normalizedWatts,
        // Each sample is ~1 second of recorded riding, so summing watts directly gives joules.
        totalKj = (watts.sumOf { it.toLong() } / 1000.0).roundToInt(),
        avgHrBpm = if (hrs.isNotEmpty()) hrs.average().roundToInt() else null,
        maxHrBpm = hrs.maxOrNull(),
        avgCadenceRpm = if (cadences.isNotEmpty()) cadences.average().roundToInt() else null,
        avgSpeedKmh = if (speeds.isNotEmpty()) speeds.average().toFloat() else null,
        // Each sample contributes speedKmh/3600 km for its ~1 second — absent (not zero) when no
        // session sample ever carried a speed, e.g. one saved before speed capture was added.
        distanceKm = if (speeds.isNotEmpty()) (speeds.sum() / 3600.0).toFloat() else null,
        avgCoreTempC = if (coreTemps.isNotEmpty()) coreTemps.average().toFloat() else null,
        maxCoreTempC = coreTemps.maxOrNull(),
        timeAboveCoreTempSec = samples.count { (it.coreTempC ?: 0f) > CORE_TEMP_ALERT_C },
        heatTrainingLoad = HeatTrainingLoad.compute(samples),
        // TSS = duration_sec x NP x IF / (FTP x 3600) x 100, IF = NP/FTP — each sample is ~1
        // second, same assumption totalKj already relies on.
        tss = if (ftpWatts != null && ftpWatts > 0 && normalizedWatts != null) {
            val intensityFactor = normalizedWatts.toDouble() / ftpWatts
            (samples.size * normalizedWatts * intensityFactor / (ftpWatts * 3600.0) * 100.0).roundToInt()
        } else {
            null
        },
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

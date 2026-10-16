package com.ergrm.trainer.workout

import com.ergrm.trainer.history.normalizedPower
import kotlin.math.roundToInt

/**
 * A single block of a structured workout, expressed in absolute watts.
 * [startWatts] and [endWatts] differ only for ramps (warmup/cooldown/ramp blocks);
 * for steady blocks they are equal.
 */
data class WorkoutStep(
    val durationSec: Int,
    val startWatts: Int,
    val endWatts: Int,
    val label: String,
) {
    /** Linearly interpolated target power at [elapsedSec] into this step. */
    fun targetWattsAt(elapsedSec: Int): Int {
        if (durationSec <= 0) return startWatts
        val fraction = (elapsedSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)
        return (startWatts + (endWatts - startWatts) * fraction).toInt()
    }
}

/** A plan's target numbers, for library/preview tiles shown before the workout is ever ridden —
 *  same shape as [com.ergrm.trainer.history.SessionStats] but computed from the planned steps
 *  instead of recorded samples. */
data class PlanPreviewStats(val avgWatts: Int, val normalizedWatts: Int?, val totalKj: Int)

/** [avgWatts]/[totalKj] use each step's trapezoid area (exact for ramps, not just its endpoints);
 *  [normalizedWatts] needs an actual 1Hz series, so it synthesizes one via [WorkoutStep.targetWattsAt]
 *  and reuses the same rolling-4th-power math a recorded ride's NP uses. */
fun List<WorkoutStep>.previewStats(): PlanPreviewStats {
    if (isEmpty()) return PlanPreviewStats(0, null, 0)
    val totalDurationSec = sumOf { it.durationSec }
    val totalJoules = sumOf { step -> (step.startWatts + step.endWatts) / 2.0 * step.durationSec }
    val avgWatts = if (totalDurationSec > 0) (totalJoules / totalDurationSec).roundToInt() else 0
    val wattsSeries = flatMap { step -> (0 until step.durationSec).map(step::targetWattsAt) }
    return PlanPreviewStats(
        avgWatts = avgWatts,
        normalizedWatts = normalizedPower(wattsSeries),
        totalKj = (totalJoules / 1000.0).roundToInt(),
    )
}

package com.ergrm.trainer.workout

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

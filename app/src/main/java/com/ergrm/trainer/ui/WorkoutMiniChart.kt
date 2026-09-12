package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.ergrm.trainer.workout.WorkoutStep
import kotlin.math.max

/** Compact, non-interactive preview of a workout's structure for a picker list row — colored bars
 *  by power zone (same palette as the live workout chart), scaled to this workout's own peak
 *  power rather than a fixed ceiling so a short recovery-only ride doesn't render flat. */
@Composable
fun WorkoutMiniChart(steps: List<WorkoutStep>, ftpWatts: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (steps.isEmpty()) return@Canvas
        val totalDuration = steps.sumOf { it.durationSec }.coerceAtLeast(1)
        val maxWatts = steps.maxOf { max(it.startWatts, it.endWatts) }.coerceAtLeast(1)
        val w = size.width
        val h = size.height

        var acc = 0
        steps.forEach { step ->
            val x0 = w * acc / totalDuration.toFloat()
            acc += step.durationSec
            val x1 = w * acc / totalDuration.toFloat()
            val peak = max(step.startWatts, step.endWatts)
            val barHeight = h * (peak.toFloat() / maxWatts).coerceIn(0.08f, 1f)
            val color = zoneFor(peak, ftpWatts).color
            drawRect(
                color = color,
                topLeft = Offset(x0, h - barHeight),
                size = Size((x1 - x0).coerceAtLeast(0.5f), barHeight),
            )
        }
    }
}

/** "1h25m" / "45min" — shared across the workout pickers (Calendar, Intervals.icu Library, local
 *  files) so a workout's duration always reads the same way regardless of source. */
fun formatWorkoutDuration(totalSec: Int): String {
    if (totalSec <= 0) return ""
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "%dh%02dmin".format(h, m) else "%dmin".format(m)
}

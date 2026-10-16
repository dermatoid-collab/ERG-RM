package com.ergrm.trainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import com.ergrm.trainer.ui.theme.ErgOnSurface
import com.ergrm.trainer.workout.WorkoutStep
import kotlin.math.max

/** Compact, non-interactive preview of a workout's structure for a picker list row — colored bars
 *  by power zone (same palette as the live workout chart), scaled to this workout's own peak
 *  power rather than a fixed ceiling so a short recovery-only ride doesn't render flat. A ramp
 *  step (startWatts != endWatts) draws as a sloped trapezoid from its start height to its end
 *  height, same as the live chart's own bars — previously drawn as one flat rect at the step's
 *  peak watts, which made every ramp/slope interval look identical to a steady one. */
@Composable
fun WorkoutMiniChart(steps: List<WorkoutStep>, ftpWatts: Int, modifier: Modifier = Modifier) {
    val noDataColor = ErgOnSurface
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
            val startHeight = h * (step.startWatts.toFloat() / maxWatts).coerceIn(0.08f, 1f)
            val endHeight = h * (step.endWatts.toFloat() / maxWatts).coerceIn(0.08f, 1f)
            val peak = max(step.startWatts, step.endWatts)
            val color = zoneFor(peak, ftpWatts, noDataColor).color
            val barPath = Path().apply {
                moveTo(x0, h - startHeight)
                lineTo(x1, h - endHeight)
                lineTo(x1, h)
                lineTo(x0, h)
                close()
            }
            drawPath(path = barPath, color = color)
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

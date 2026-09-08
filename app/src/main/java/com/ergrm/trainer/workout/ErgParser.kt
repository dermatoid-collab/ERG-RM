package com.ergrm.trainer.workout

/**
 * Parses the classic ErgDB/TrainerRoad plain-text course formats: ".erg" (absolute watts) and
 * ".mrc" (percent of FTP). Both describe a workout as a list of (minute, power) breakpoints in a
 * [COURSE DATA]/[END COURSE DATA] block; consecutive breakpoints at the same timestamp form a
 * step change, otherwise the power ramps linearly between them. This is converted into the same
 * flat [WorkoutStep] list used by [ZwoParser].
 */
object ErgParser {

    private data class Point(val minutes: Float, val power: Float)

    fun parse(text: String, ftpWatts: Int): List<WorkoutStep> {
        val lines = text.lines()
        val isPercent = lines.any { line ->
            val upper = line.trim().uppercase()
            upper.startsWith("UNITS") && upper.contains("PERCENT") ||
                upper == "MINUTES PERCENT" ||
                upper.startsWith("MINUTES") && upper.contains("PERCENT")
        }

        val points = mutableListOf<Point>()
        var insideData = false
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.equals("[COURSE DATA]", ignoreCase = true) -> insideData = true
                line.equals("[END COURSE DATA]", ignoreCase = true) -> insideData = false
                insideData -> parsePoint(line)?.let { points += it }
            }
        }
        if (points.size < 2) return emptyList()

        val steps = mutableListOf<WorkoutStep>()
        for (i in 0 until points.size - 1) {
            val from = points[i]
            val to = points[i + 1]
            val durationSec = ((to.minutes - from.minutes) * 60f).toInt()
            if (durationSec <= 0) continue
            val startWatts = toWatts(from.power, isPercent, ftpWatts)
            val endWatts = toWatts(to.power, isPercent, ftpWatts)
            val label = if (startWatts == endWatts) "Steady" else "Ramp"
            steps += WorkoutStep(durationSec, startWatts, endWatts, label)
        }
        return steps
    }

    private fun toWatts(power: Float, isPercent: Boolean, ftpWatts: Int): Int =
        if (isPercent) (power / 100f * ftpWatts).toInt() else power.toInt()

    private fun parsePoint(line: String): Point? {
        val tokens = line.split(Regex("[\\s\\t]+")).filter { it.isNotBlank() }
        if (tokens.size < 2) return null
        val minutes = tokens[0].toFloatOrNull() ?: return null
        val power = tokens[1].toFloatOrNull() ?: return null
        return Point(minutes, power)
    }
}

package com.ergrm.trainer.workout

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.roundToInt

/**
 * Parses a structured workout in .zwo XML format — the format intervals.icu exposes for
 * downloading a planned workout's structure, also commonly used for local workout files —
 * into a flat list of [WorkoutStep]s. Power attributes are fractions of FTP (e.g. 0.65 ==
 * 65% FTP) and are converted to absolute watts using [ftpWatts].
 */
object ZwoParser {

    fun parse(xml: String, ftpWatts: Int): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var insideWorkout = false
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "workout" -> insideWorkout = true
                    "Warmup" -> if (insideWorkout) steps += rampStep(parser, ftpWatts, "Warmup")
                    "Cooldown" -> if (insideWorkout) steps += rampStep(parser, ftpWatts, "Cooldown")
                    "Ramp" -> if (insideWorkout) steps += rampStep(parser, ftpWatts, "Ramp")
                    "SteadyState" -> if (insideWorkout) steps += steadyStep(parser, ftpWatts)
                    "FreeRide" -> if (insideWorkout) steps += freeRideStep(parser, ftpWatts)
                    "IntervalsT" -> if (insideWorkout) steps += intervalSteps(parser, ftpWatts)
                }
            } else if (eventType == XmlPullParser.END_TAG && parser.name == "workout") {
                insideWorkout = false
            }
            eventType = parser.next()
        }
        return steps
    }

    private fun attrFloat(parser: XmlPullParser, name: String, default: Float = 0f): Float =
        parser.getAttributeValue(null, name)?.toFloatOrNull() ?: default

    private fun attrInt(parser: XmlPullParser, name: String, default: Int = 0): Int =
        attrFloat(parser, name, default.toFloat()).roundToInt()

    /** Like [attrFloat], but for power-fraction attributes: a step authored on Intervals.icu as
     *  a target range (e.g. "88-90% FTP") is exported as a single attribute holding both bounds
     *  as text (e.g. "0.88-0.9") rather than a plain number. A plain [String.toFloatOrNull] on
     *  that fails and would otherwise silently fall back to a default miles off the real target
     *  — average the two bounds instead. */
    private fun attrPowerFraction(parser: XmlPullParser, name: String, default: Float): Float {
        val raw = parser.getAttributeValue(null, name) ?: return default
        raw.toFloatOrNull()?.let { return it }
        val bounds = raw.split("-").mapNotNull { it.trim().toFloatOrNull() }
        return if (bounds.size == 2) (bounds[0] + bounds[1]) / 2f else default
    }

    private fun watts(fraction: Float, ftpWatts: Int): Int = (fraction * ftpWatts).roundToInt()

    private fun rampStep(parser: XmlPullParser, ftpWatts: Int, label: String): WorkoutStep {
        val duration = attrInt(parser, "Duration")
        val powerLow = attrPowerFraction(parser, "PowerLow", 0.5f)
        val powerHigh = attrPowerFraction(parser, "PowerHigh", 0.5f)
        return WorkoutStep(
            durationSec = duration,
            startWatts = watts(powerLow, ftpWatts),
            endWatts = watts(powerHigh, ftpWatts),
            label = label,
        )
    }

    private fun steadyStep(parser: XmlPullParser, ftpWatts: Int): WorkoutStep {
        val duration = attrInt(parser, "Duration")
        val power = watts(attrPowerFraction(parser, "Power", 0.6f), ftpWatts)
        return WorkoutStep(duration, power, power, "Steady")
    }

    private fun freeRideStep(parser: XmlPullParser, ftpWatts: Int): WorkoutStep {
        val duration = attrInt(parser, "Duration")
        val power = watts(0.5f, ftpWatts)
        return WorkoutStep(duration, power, power, "Free Ride")
    }

    private fun intervalSteps(parser: XmlPullParser, ftpWatts: Int): List<WorkoutStep> {
        val repeatCount = attrInt(parser, "Repeat", 1).coerceAtLeast(1)
        val onDuration = attrInt(parser, "OnDuration")
        val offDuration = attrInt(parser, "OffDuration")
        val onPower = watts(attrPowerFraction(parser, "OnPower", 1.0f), ftpWatts)
        val offPower = watts(attrPowerFraction(parser, "OffPower", 0.5f), ftpWatts)

        val result = mutableListOf<WorkoutStep>()
        repeat(repeatCount) { i ->
            result += WorkoutStep(onDuration, onPower, onPower, "Interval ${i + 1}/$repeatCount")
            if (offDuration > 0) {
                result += WorkoutStep(offDuration, offPower, offPower, "Recovery ${i + 1}/$repeatCount")
            }
        }
        return result
    }
}

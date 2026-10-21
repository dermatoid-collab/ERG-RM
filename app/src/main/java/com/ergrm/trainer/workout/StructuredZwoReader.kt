package com.ergrm.trainer.workout

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.roundToInt

/**
 * Reads a .zwo file into a [WorkoutDraft] for the Workout Builder's Import — unlike [ZwoParser]
 * (which flattens straight to playback [WorkoutStep]s and is fine staying that way), this keeps
 * an <IntervalsT> as one repeated 2-step [WorkoutBlock] instead of exploding it, so importing a
 * "4x(5m on/3m off)" file gives back one editable 4x block, not eight flattened step lines.
 *
 * A <textevent> is ERG-RM's own writer's way of carrying a step's free-text label (see
 * [ZwoWriter]'s doc comment) — read back here by matching its timeoffset against the step whose
 * time window contains it, so a file this app wrote round-trips its labels too. A file from
 * another tool simply has no textevents to match, and every step just comes back unlabeled.
 */
object StructuredZwoReader {

    private data class RawElement(val tag: String, val attrs: Map<String, String>)
    private data class RawTextEvent(val offsetSec: Int, val message: String)

    private val NUMBER_REGEX = Regex("[0-9]*\\.?[0-9]+")

    fun read(xml: String, ftpWatts: Int, fallbackName: String): WorkoutDraft {
        val elements = mutableListOf<RawElement>()
        val textEvents = mutableListOf<RawTextEvent>()
        var name = ""
        var description = ""

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var insideWorkout = false
        var capturing: String? = null
        var captured = StringBuilder()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "workout" -> insideWorkout = true
                    "name", "description" -> {
                        capturing = parser.name
                        captured = StringBuilder()
                    }
                    "textevent" -> if (insideWorkout) {
                        val offset = parser.getAttributeValue(null, "timeoffset")?.toIntOrNull() ?: 0
                        val message = parser.getAttributeValue(null, "message") ?: ""
                        if (message.isNotBlank()) textEvents += RawTextEvent(offset, message)
                    }
                    else -> if (insideWorkout) {
                        val attrs = (0 until parser.attributeCount).associate { i ->
                            parser.getAttributeName(i) to parser.getAttributeValue(i)
                        }
                        elements += RawElement(parser.name, attrs)
                    }
                }
                XmlPullParser.TEXT -> if (capturing != null) captured.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "name" -> { name = captured.toString().trim(); capturing = null }
                    "description" -> { description = captured.toString().trim(); capturing = null }
                    "workout" -> insideWorkout = false
                }
            }
            eventType = parser.next()
        }

        val blocks = elements.mapNotNull { el -> blockFor(el, ftpWatts) }
        val labeled = attachLabels(blocks, textEvents)
        return WorkoutDraft(name.ifBlank { fallbackName }, description, labeled)
    }

    private fun blockFor(el: RawElement, ftpWatts: Int): WorkoutBlock? = when (el.tag) {
        "IntervalsT" -> {
            val repeat = el.attrs["Repeat"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val onDuration = el.attrs["OnDuration"]?.toIntOrNull() ?: 0
            val offDuration = el.attrs["OffDuration"]?.toIntOrNull() ?: 0
            val onPower = powerFraction(el.attrs, "OnPower", 1f)
            val offPower = powerFraction(el.attrs, "OffPower", 0.5f)
            val steps = mutableListOf(DraftStep("", onDuration, PowerTarget.PercentFtp(onPower, onPower)))
            if (offDuration > 0) steps += DraftStep("", offDuration, PowerTarget.PercentFtp(offPower, offPower))
            WorkoutBlock("", repeat, steps)
        }
        "Warmup", "Cooldown", "Ramp" -> {
            val duration = el.attrs["Duration"]?.toIntOrNull() ?: 0
            val low = powerFraction(el.attrs, "PowerLow", 0.5f)
            val high = powerFraction(el.attrs, "PowerHigh", 0.5f)
            WorkoutBlock(el.tag, 1, listOf(DraftStep("", duration, PowerTarget.PercentFtp(low, high), cadence = cadenceFrom(el.attrs))))
        }
        "SteadyState" -> {
            val duration = el.attrs["Duration"]?.toIntOrNull() ?: 0
            val fraction = when {
                el.attrs["Power"] != null -> powerFraction(el.attrs, "Power", 0.6f)
                el.attrs["PowerLow"] != null || el.attrs["PowerHigh"] != null ->
                    (powerFraction(el.attrs, "PowerLow", 0.6f) + powerFraction(el.attrs, "PowerHigh", 0.6f)) / 2f
                else -> 0.6f
            }
            WorkoutBlock("", 1, listOf(DraftStep("", duration, PowerTarget.PercentFtp(fraction, fraction), cadence = cadenceFrom(el.attrs))))
        }
        "FreeRide" -> {
            val duration = el.attrs["Duration"]?.toIntOrNull() ?: 0
            WorkoutBlock("Free Ride", 1, listOf(DraftStep("", duration, PowerTarget.PercentFtp(0.5f, 0.5f))))
        }
        else -> null
    }

    private fun cadenceFrom(attrs: Map<String, String>): CadenceTarget? {
        attrs["Cadence"]?.toIntOrNull()?.let { return CadenceTarget(it, it) }
        val low = attrs["CadenceLow"]?.toIntOrNull()
        val high = attrs["CadenceHigh"]?.toIntOrNull()
        if (low != null || high != null) return CadenceTarget(low ?: high!!, high ?: low!!)
        return null
    }

    /** Same tolerance as [ZwoParser]'s own private attrPowerFraction: a target authored as a
     *  range on Intervals.icu can come back as text holding both bounds (e.g. "0.88-0.9") rather
     *  than a plain number — pull out every number in the attribute and average them. */
    private fun powerFraction(attrs: Map<String, String>, name: String, default: Float): Float {
        val raw = attrs[name] ?: return default
        raw.toFloatOrNull()?.let { return normalize(it) }
        val numbers = NUMBER_REGEX.findAll(raw).mapNotNull { it.value.toFloatOrNull() }.toList()
        if (numbers.isEmpty()) return default
        return normalize(numbers.average().toFloat())
    }

    private fun normalize(value: Float): Float = if (value > 1.5f) value / 100f else value

    private fun attachLabels(blocks: List<WorkoutBlock>, textEvents: List<RawTextEvent>): List<WorkoutBlock> {
        var elapsedSec = 0
        return blocks.map { block ->
            val oneRepeatDuration = block.steps.sumOf { it.durationSec }
            val labeledSteps = mutableListOf<DraftStep>()
            var cursor = elapsedSec
            for (step in block.steps) {
                val label = textEvents.firstOrNull { it.offsetSec >= cursor && it.offsetSec < cursor + step.durationSec }?.message
                labeledSteps += if (label != null) step.copy(label = label) else step
                cursor += step.durationSec
            }
            elapsedSec += block.repeatCount * oneRepeatDuration
            block.copy(steps = labeledSteps)
        }
    }
}

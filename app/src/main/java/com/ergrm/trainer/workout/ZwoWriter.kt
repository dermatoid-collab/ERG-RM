package com.ergrm.trainer.workout

import kotlin.math.roundToInt

/**
 * Writes a [WorkoutDraft] out as Zwift's .zwo XML — the format used for both the Workout
 * Builder's "Save as…" and the structured side of Import/export. Power is always written as a
 * fraction of FTP (ZWO has no native absolute-watts field, confirmed by [ZwoParser]'s own reading
 * side), so a [PowerTarget.Watts] step is converted using [ftpWatts].
 *
 * A repeated block (repeatCount > 1) whose two steps are both flat-power (no ramp) and carry no
 * HR/cadence target is written as a single compact <IntervalsT> — the common on/off interval
 * case, and the only shape that round-trips back through [StructuredZwoReader] as one block
 * instead of several. Anything else (ramps inside a repeat, more than two steps, HR/cadence
 * targets) is written as its repeated-out individual elements instead: still correct Zwift XML,
 * just not the compact shorthand.
 *
 * A step's free-text label has nowhere to live in ZWO's own step attributes, so it's carried as a
 * <textevent> at that step's start offset instead (the same mechanism Zwift itself uses for an
 * on-screen message during a workout) — ZWO/MRC/ERG comment support was confirmed to not include
 * a dedicated per-step field, this is the closest native equivalent.
 */
object ZwoWriter {

    fun write(draft: WorkoutDraft, ftpWatts: Int): String {
        val body = StringBuilder()
        var elapsedSec = 0
        for (block in draft.blocks) {
            val isCompactInterval = block.repeatCount > 1 && block.steps.size == 2 &&
                block.steps.all { it.hr == null && it.cadence == null && isFlat(it.power) }
            if (isCompactInterval) {
                val on = block.steps[0]
                val off = block.steps[1]
                val onFraction = fractionRange(on.power, ftpWatts).first
                val offFraction = fractionRange(off.power, ftpWatts).first
                body.append(
                    "    <IntervalsT Repeat=\"${block.repeatCount}\" OnDuration=\"${on.durationSec}\" " +
                        "OnPower=\"${fmt(onFraction)}\" OffDuration=\"${off.durationSec}\" OffPower=\"${fmt(offFraction)}\"/>\n",
                )
                if (on.label.isNotBlank()) body.append(textEvent(elapsedSec, on.label))
                elapsedSec += block.repeatCount * (on.durationSec + off.durationSec)
                if (off.label.isNotBlank()) body.append(textEvent(elapsedSec - off.durationSec, off.label))
            } else {
                repeat(block.repeatCount) {
                    for (step in block.steps) {
                        body.append(stepElement(step, block.label, ftpWatts))
                        if (step.label.isNotBlank()) body.append(textEvent(elapsedSec, step.label))
                        elapsedSec += step.durationSec
                    }
                }
            }
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<workout_file>
    <author>ERG-RM</author>
    <name>${escape(draft.name)}</name>
    <description>${escape(draft.description)}</description>
    <sportType>bike</sportType>
    <tags/>
    <workout>
$body    </workout>
</workout_file>
"""
    }

    private fun isFlat(power: PowerTarget): Boolean = when (power) {
        is PowerTarget.Watts -> power.low == power.high
        is PowerTarget.PercentFtp -> power.low == power.high
        is PowerTarget.Zone -> true
    }

    private fun fractionRange(power: PowerTarget, ftpWatts: Int): Pair<Float, Float> = when (power) {
        is PowerTarget.Watts -> (power.low.toFloat() / ftpWatts) to (power.high.toFloat() / ftpWatts)
        is PowerTarget.PercentFtp -> power.low to power.high
        is PowerTarget.Zone -> powerZoneRange(power.zone)
    }

    private fun stepElement(step: DraftStep, blockLabel: String, ftpWatts: Int): String {
        val (low, high) = fractionRange(step.power, ftpWatts)
        val cadenceAttr = step.cadence?.let { c ->
            if (c.low == c.high) " Cadence=\"${c.low}\"" else " CadenceLow=\"${c.low}\" CadenceHigh=\"${c.high}\""
        }.orEmpty()
        return if (low == high) {
            "    <SteadyState Duration=\"${step.durationSec}\" Power=\"${fmt(low)}\"$cadenceAttr/>\n"
        } else {
            val tag = when {
                blockLabel.contains("warmup", ignoreCase = true) -> "Warmup"
                blockLabel.contains("cooldown", ignoreCase = true) -> "Cooldown"
                else -> "Ramp"
            }
            "    <$tag Duration=\"${step.durationSec}\" PowerLow=\"${fmt(low)}\" PowerHigh=\"${fmt(high)}\"$cadenceAttr/>\n"
        }
    }

    private fun textEvent(offsetSec: Int, message: String): String =
        "    <textevent timeoffset=\"$offsetSec\" message=\"${escape(message)}\"/>\n"

    private fun fmt(fraction: Float): String {
        val rounded = (fraction * 1000).roundToInt() / 1000f
        return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

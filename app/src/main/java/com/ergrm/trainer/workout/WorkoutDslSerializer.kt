package com.ergrm.trainer.workout

import kotlin.math.roundToInt

/** The reverse of [WorkoutDslParser.parse] — turns a [WorkoutDraft] back into editable DSL text.
 *  Used after an import (so the file becomes an editable starting point, not a locked blob) and
 *  whenever the builder screen needs to re-render its own in-memory draft back to text. */
object WorkoutDslSerializer {

    fun serialize(draft: WorkoutDraft): String =
        draft.blocks.joinToString("\n\n") { block -> serializeBlock(block) }

    private fun serializeBlock(block: WorkoutBlock): String {
        val header = when {
            block.repeatCount > 1 && block.label.isNotBlank() -> "${block.label} ${block.repeatCount}x"
            block.repeatCount > 1 -> "${block.repeatCount}x"
            block.label.isNotBlank() -> block.label
            else -> null
        }
        val stepLines = block.steps.map { "- ${serializeStep(it)}" }
        return if (header != null) (listOf(header) + stepLines).joinToString("\n") else stepLines.joinToString("\n")
    }

    private fun serializeStep(step: DraftStep): String {
        val parts = mutableListOf<String>()
        if (step.label.isNotBlank()) parts += step.label
        parts += formatDuration(step.durationSec)
        parts += formatPower(step.power)
        step.hr?.let { parts += formatHr(it) }
        step.cadence?.let { parts += formatCadence(it) }
        return parts.joinToString(" ")
    }

    private fun formatDuration(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 && s > 0 -> "${h}h${m}m${s}s"
            h > 0 -> "${h}h${m}m"
            m > 0 && s > 0 -> "${m}m$s"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    private fun pct(fraction: Float): String {
        val value = fraction * 100f
        return if (value == value.roundToInt().toFloat()) value.roundToInt().toString() else "%.1f".format(value)
    }

    private fun formatPower(target: PowerTarget): String = when (target) {
        is PowerTarget.Watts -> if (target.low == target.high) "${target.low}w" else "${target.low}-${target.high}w"
        is PowerTarget.PercentFtp -> if (target.low == target.high) "${pct(target.low)}%" else "${pct(target.low)}-${pct(target.high)}%"
        is PowerTarget.Zone -> "Z${target.zone}"
    }

    private fun formatHr(target: HrTarget): String = when (target) {
        is HrTarget.PercentMaxHr -> (if (target.low == target.high) "${pct(target.low)}%" else "${pct(target.low)}-${pct(target.high)}%") + " HR"
        is HrTarget.PercentLthr -> (if (target.low == target.high) "${pct(target.low)}%" else "${pct(target.low)}-${pct(target.high)}%") + " LTHR"
        is HrTarget.Zone -> "Z${target.zone} HR"
    }

    private fun formatCadence(target: CadenceTarget): String =
        if (target.low == target.high) "${target.low}rpm" else "${target.low}-${target.high}rpm"
}

package com.ergrm.trainer.workout

import kotlin.math.roundToInt

/**
 * Parses the Workout Builder's text DSL — the same block/step grammar Intervals.icu's own
 * "Workout Builder" documents at https://forum.intervals.icu/t/workout-builder/1163 — into a
 * [WorkoutDraft]. Grammar, confirmed against that doc:
 *
 * - Blocks are separated by one or more blank lines.
 * - A block's first line is a free-text header, optionally carrying a repeat count ("4x"); that
 *   count — not the text — is the only part of it that affects anything. A block whose first
 *   line already starts with "-" has no header at all (an anonymous, non-repeated block).
 * - Every other line in a block is a step: "- [label] <duration> <power> [hr] [cadence]".
 * - Duration: 30s / 10m / 1m30 / 1h30m.
 * - Power: 220w (watts) / 90% or 60-80% (percent FTP, a range being a ramp) / Z3 (zone).
 * - HR (optional, after power): "70-80% HR" (%max HR), "70-80% LTHR" (%LTHR), "Z2 HR" (HR zone).
 * - Cadence (optional, after HR if present): "85rpm" or "85-95rpm".
 * - Any free text before the duration token becomes the step's own label.
 */
object WorkoutDslParser {

    sealed interface ParseResult {
        data class Success(val draft: WorkoutDraft) : ParseResult
        data class Error(val line: Int, val message: String) : ParseResult
    }

    private val REPEAT_REGEX = Regex("""(\d+)\s*[xX]\b""")
    private val DURATION_REGEX = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s|(\d+))?$""")
    private val ZONE_REGEX = Regex("""^[Zz](\d+)$""")
    private val WATTS_REGEX = Regex("""^(\d+)(?:-(\d+))?[wW]$""")
    private val PERCENT_REGEX = Regex("""^(\d+(?:\.\d+)?)(?:-(\d+(?:\.\d+)?))?%$""")
    private val CADENCE_REGEX = Regex("""^(\d+)(?:-(\d+))?rpm$""", RegexOption.IGNORE_CASE)

    fun parse(text: String, name: String = "", description: String = ""): ParseResult {
        val lines = text.lines()
        val groups = mutableListOf<MutableList<Pair<Int, String>>>()
        for ((index, raw) in lines.withIndex()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                if (groups.isNotEmpty() && groups.last().isNotEmpty()) groups.add(mutableListOf())
            } else {
                if (groups.isEmpty()) groups.add(mutableListOf())
                groups.last().add((index + 1) to trimmed)
            }
        }
        val blocks = mutableListOf<WorkoutBlock>()
        for (group in groups) {
            if (group.isEmpty()) continue
            val (firstLineNo, firstText) = group.first()
            val hasHeader = !firstText.startsWith("-")
            val headerLabel: String
            val repeatCount: Int
            val stepLines: List<Pair<Int, String>>
            if (hasHeader) {
                val match = REPEAT_REGEX.find(firstText)
                repeatCount = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                headerLabel = (if (match != null) firstText.replaceRange(match.range, "") else firstText).trim()
                stepLines = group.drop(1)
            } else {
                headerLabel = ""
                repeatCount = 1
                stepLines = group
            }
            if (stepLines.isEmpty()) {
                return ParseResult.Error(firstLineNo, "Block has no steps")
            }
            val steps = mutableListOf<DraftStep>()
            for ((lineNo, lineText) in stepLines) {
                if (!lineText.startsWith("-")) {
                    return ParseResult.Error(lineNo, "Expected a step line starting with \"-\", or a blank line to end the block")
                }
                when (val result = parseStepLine(lineText.removePrefix("-").trim())) {
                    is StepResult.Ok -> steps += result.step
                    is StepResult.Err -> return ParseResult.Error(lineNo, result.message)
                }
            }
            blocks += WorkoutBlock(headerLabel, repeatCount, steps)
        }
        if (blocks.isEmpty()) return ParseResult.Error(1, "No steps found")
        return ParseResult.Success(WorkoutDraft(name, description, blocks))
    }

    private sealed interface StepResult {
        data class Ok(val step: DraftStep) : StepResult
        data class Err(val message: String) : StepResult
    }

    private fun parseStepLine(body: String): StepResult {
        val tokens = body.split(Regex("\\s+")).filter { it.isNotBlank() }
        val durationIndex = tokens.indexOfFirst { isDurationToken(it) }
        if (durationIndex == -1) return StepResult.Err("No duration found (expected e.g. 10m, 30s, 1h30m)")
        val label = tokens.subList(0, durationIndex).joinToString(" ")
        val durationSec = parseDuration(tokens[durationIndex])
        if (durationIndex + 1 >= tokens.size) return StepResult.Err("No power target found after the duration")
        val powerToken = tokens[durationIndex + 1]
        val power = parsePower(powerToken) ?: return StepResult.Err("Unrecognized power target \"$powerToken\"")

        var cursor = durationIndex + 2
        var hr: HrTarget? = null
        var cadence: CadenceTarget? = null
        while (cursor < tokens.size) {
            val token = tokens[cursor]
            val next = tokens.getOrNull(cursor + 1)
            when {
                hr == null && isHrLead(token) && next != null && (next.equals("HR", true) || next.equals("LTHR", true)) -> {
                    hr = buildHrTarget(token, next)
                    cursor += 2
                }
                cadence == null && CADENCE_REGEX.matches(token) -> {
                    val m = CADENCE_REGEX.find(token)!!
                    val low = m.groupValues[1].toInt()
                    val high = m.groupValues[2].toIntOrNull() ?: low
                    cadence = CadenceTarget(low, high)
                    cursor += 1
                }
                else -> return StepResult.Err("Unrecognized token \"$token\"")
            }
        }
        return StepResult.Ok(DraftStep(label, durationSec, power, hr, cadence))
    }

    private fun isHrLead(token: String): Boolean = ZONE_REGEX.matches(token) || PERCENT_REGEX.matches(token)

    private fun buildHrTarget(token: String, keyword: String): HrTarget {
        ZONE_REGEX.find(token)?.let { return HrTarget.Zone(it.groupValues[1].toInt()) }
        val m = PERCENT_REGEX.find(token)!!
        val low = m.groupValues[1].toFloat() / 100f
        val high = m.groupValues[2].toFloatOrNull()?.div(100f) ?: low
        return if (keyword.equals("LTHR", true)) HrTarget.PercentLthr(low, high) else HrTarget.PercentMaxHr(low, high)
    }

    private fun isDurationToken(token: String): Boolean =
        token.any { it in "hms" } && DURATION_REGEX.matches(token) && token.any { it.isDigit() }

    private fun parseDuration(token: String): Int {
        val m = DURATION_REGEX.find(token)!!
        val hours = m.groupValues[1].toIntOrNull() ?: 0
        val minutes = m.groupValues[2].toIntOrNull() ?: 0
        val seconds = m.groupValues[3].toIntOrNull() ?: m.groupValues[4].toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }

    private fun parsePower(token: String): PowerTarget? {
        ZONE_REGEX.find(token)?.let { return PowerTarget.Zone(it.groupValues[1].toInt()) }
        WATTS_REGEX.find(token)?.let { m ->
            val low = m.groupValues[1].toInt()
            val high = m.groupValues[2].toIntOrNull() ?: low
            return PowerTarget.Watts(low, high)
        }
        PERCENT_REGEX.find(token)?.let { m ->
            val low = m.groupValues[1].toFloat() / 100f
            val high = m.groupValues[2].toFloatOrNull()?.div(100f) ?: low
            return PowerTarget.PercentFtp(low, high)
        }
        return null
    }

    /** Expands a draft into the flat, repeat-exploded [WorkoutStep] list the rest of the app
     *  (playback, preview stats, the mini chart) already works with — the one place a draft's
     *  compact block/repeat shape is deliberately thrown away. */
    fun flatten(draft: WorkoutDraft, ftpWatts: Int): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        for (block in draft.blocks) {
            for (rep in 1..block.repeatCount) {
                for ((stepIndex, step) in block.steps.withIndex()) {
                    val (lowFraction, highFraction) = when (val p = step.power) {
                        is PowerTarget.Watts -> null to null
                        is PowerTarget.PercentFtp -> p.low to p.high
                        is PowerTarget.Zone -> powerZoneRange(p.zone).let { (a, b) -> ((a + b) / 2f) to ((a + b) / 2f) }
                    }
                    val startWatts: Int
                    val endWatts: Int
                    if (step.power is PowerTarget.Watts) {
                        startWatts = step.power.low
                        endWatts = step.power.high
                    } else {
                        startWatts = ((lowFraction ?: 0f) * ftpWatts).roundToInt()
                        endWatts = ((highFraction ?: 0f) * ftpWatts).roundToInt()
                    }
                    val label = step.label.ifBlank {
                        when {
                            block.repeatCount > 1 && block.label.isNotBlank() -> "${block.label} $rep/${block.repeatCount}"
                            block.repeatCount > 1 -> "Rep $rep/${block.repeatCount}"
                            block.label.isNotBlank() -> block.label
                            else -> "Step ${stepIndex + 1}"
                        }
                    }
                    steps += WorkoutStep(step.durationSec, startWatts, endWatts, label)
                }
            }
        }
        return steps
    }
}

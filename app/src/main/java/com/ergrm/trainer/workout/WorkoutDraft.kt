package com.ergrm.trainer.workout

/**
 * In-memory model for a workout being authored in the Workout Builder, distinct from the flat
 * [WorkoutStep] list used for playback/history: a draft keeps each block's repeat count and
 * label intact (a "4x" block stays one block of 2 steps, not 8 flattened [WorkoutStep]s) so the
 * DSL text editor and the structured .zwo import/export can both round-trip it without losing
 * that shape. [WorkoutDslParser.flatten] is the one place a draft becomes playable [WorkoutStep]s.
 */
data class WorkoutDraft(
    val name: String,
    val description: String,
    val blocks: List<WorkoutBlock>,
)

/** [repeatCount] is 1 for a plain, non-repeated block — the DSL's "4x" header syntax is just a
 *  repeat count attached to [label], which is otherwise purely cosmetic (never affects playback). */
data class WorkoutBlock(
    val label: String,
    val repeatCount: Int,
    val steps: List<DraftStep>,
)

data class DraftStep(
    val label: String,
    val durationSec: Int,
    val power: PowerTarget,
    val hr: HrTarget? = null,
    val cadence: CadenceTarget? = null,
)

/** [low]/[high] differ only for a ramp or an explicit range target; equal for a steady one. */
sealed interface PowerTarget {
    data class Watts(val low: Int, val high: Int) : PowerTarget
    data class PercentFtp(val low: Float, val high: Float) : PowerTarget
    data class Zone(val zone: Int) : PowerTarget
}

sealed interface HrTarget {
    data class PercentMaxHr(val low: Float, val high: Float) : HrTarget
    data class PercentLthr(val low: Float, val high: Float) : HrTarget
    data class Zone(val zone: Int) : HrTarget
}

data class CadenceTarget(val low: Int, val high: Int)

/** Local duplicate of [com.ergrm.trainer.ui.POWER_ZONES]' breakpoints (as %FTP upper bounds) —
 *  deliberately not imported from the ui package: workout/history never depend on ui, same
 *  reasoning as [com.ergrm.trainer.history.SessionStats]' own duplicated Z3/Z4 boundary. Only the
 *  numbers are needed here (to turn a "Z3"-style zone token into a %FTP band), not ui's colors. */
internal val DRAFT_POWER_ZONE_BOUNDS = listOf(0f, 0.55f, 0.75f, 0.90f, 1.05f, 1.20f, 1.50f, 2.00f)

/** Midpoint %FTP band for zone 1..7 (Z7 open-ended above 150%, capped at 200% for a usable target). */
internal fun powerZoneRange(zone: Int): Pair<Float, Float> {
    val index = (zone - 1).coerceIn(0, DRAFT_POWER_ZONE_BOUNDS.size - 2)
    return DRAFT_POWER_ZONE_BOUNDS[index] to DRAFT_POWER_ZONE_BOUNDS[index + 1]
}

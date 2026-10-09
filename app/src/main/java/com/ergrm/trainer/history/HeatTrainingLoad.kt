package com.ergrm.trainer.history

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Heat Training Load (HTL, 0-10) — an estimate of the CORE app's metric, reverse-engineered from
 * 2025-2026 CORE exports (average error ~0.1 HTL, ~90% of days within ±0.3). NOT the official
 * CORE algorithm.
 *
 * How it works, minute by minute over the samples that carry heart rate (CORE only counts
 * exercise):
 *  - HSI < 1.0: no contribution.
 *  - 1.0 <= HSI < 2.0: two counters (bands 1.0-1.5 and 1.5-2.0) fill up towards a cap, so long
 *    exposure at low HSI saturates (~1.5 HTL for an hour, not much more for four).
 *  - HSI >= 2.0: load accumulates without a cap, at an hourly rate that grows with HSI along an
 *    S-curve (~3/h near HSI 2.2, ~7.5/h above 4).
 *  - The low-band cap scales with the mean body temperature (0.8·core + 0.2·skin) seen during
 *    those minutes: warmer sessions (typically indoor) are worth more at the same HSI.
 *  - Total capped at 10 and rounded to one decimal, as the CORE app shows it.
 *
 * Uses the sensor's own HSI when present; if a sample only has core/skin temperatures, HSI is
 * reconstructed with CORE's current formula (linear below 1, then max of linear and quadratic).
 */
object HeatTrainingLoad {
    // Low band (HSI 1.0-2.0): saturating counters — cap [HTL] and fill time constant [min].
    private const val A1_CAP = 1.781904      // HSI 1.0-1.5
    private const val A1_TAU_MIN = 37.68987
    private const val A2_CAP = 0.5455651     // HSI 1.5-2.0
    private const val A2_TAU_MIN = 16.25901

    // High band (HSI >= 2.0): hourly rate = R_MAX / (1 + exp(-(HSI - R_MID) / R_SLOPE)).
    private const val R_MAX_PER_HOUR = 7.674072
    private const val R_MID = 2.239308
    private const val R_SLOPE = 0.7209091

    // Low-band cap multiplier: exp(K_TB * (mean Tb - TB_REF)), Tb = 0.8·core + 0.2·skin.
    private const val K_TB = 2.983223
    private const val TB_REF = 0.8 * 38.3 + 0.2 * 32.5   // 37.14 °C

    private const val HTL_MAX = 10.0
    private const val MAX_SAMPLE_GAP_SEC = 120            // longer gaps count as 2 minutes at most

    // Physiologically implausible readings (sensor warm-up/removal) are ignored.
    private const val MIN_VALID_CORE_C = 35f
    private const val MIN_VALID_SKIN_C = 20f

    /** HTL for one session, or null when the session has no usable CORE + heart-rate data. */
    fun compute(samples: List<SessionSample>): Float? {
        var a1 = 0.0
        var a2 = 0.0
        var high = 0.0
        var tbSum = 0.0
        var tbMinutes = 0.0
        var usable = false

        for (i in samples.indices) {
            val s = samples[i]
            val hr = s.hrBpm ?: continue
            if (hr <= 0) continue
            val hsi = hsiOf(s) ?: continue
            usable = true

            // Duration of this sample in minutes: time to the next one (samples are ~1 s apart).
            val gapSec = if (i < samples.lastIndex) samples[i + 1].tSec - s.tSec else 1
            if (gapSec <= 0) continue
            val dtMin = min(gapSec, MAX_SAMPLE_GAP_SEC) / 60.0

            when {
                hsi < 1.0 -> Unit
                hsi < 2.0 -> {
                    val core = s.coreTempC
                    val skin = s.skinTempC
                    if (core != null && skin != null && core >= MIN_VALID_CORE_C && skin >= MIN_VALID_SKIN_C) {
                        tbSum += (0.8 * core + 0.2 * skin) * dtMin
                        tbMinutes += dtMin
                    }
                    if (hsi < 1.5) a1 += (A1_CAP - a1) * (1 - exp(-dtMin / A1_TAU_MIN))
                    else a2 += (A2_CAP - a2) * (1 - exp(-dtMin / A2_TAU_MIN))
                }
                else -> high += dtMin / 60.0 * R_MAX_PER_HOUR / (1 + exp(-(hsi - R_MID) / R_SLOPE))
            }
        }
        if (!usable) return null

        val tbFactor = if (tbMinutes > 0) exp(K_TB * (tbSum / tbMinutes - TB_REF)) else 1.0
        val htl = min(HTL_MAX, (a1 + a2) * tbFactor + high)
        return (floor(htl * 10 + 0.5) / 10).toFloat()
    }

    /** Sensor HSI if present, else reconstructed from core/skin with CORE's current formula. */
    private fun hsiOf(s: SessionSample): Double? {
        s.heatStrainIndex?.let { return it.toDouble() }
        val core = s.coreTempC?.toDouble() ?: return null
        val skin = s.skinTempC?.toDouble() ?: return null
        if (core < MIN_VALID_CORE_C || skin < MIN_VALID_SKIN_C) return null
        val linear = 1.4 * core + 0.2 * skin - 58.85
        if (linear < 1.0) return max(0.0, linear)
        val quadratic = -17.688844 * core - 12.434519 * skin + 0.1633226 * core * core +
            0.058926962 * skin * skin + 0.23747461 * core * skin + 485.41667
        return max(linear, quadratic)
    }
}

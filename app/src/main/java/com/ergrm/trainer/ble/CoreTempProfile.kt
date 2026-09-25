package com.ergrm.trainer.ble

import java.util.UUID

/**
 * CORE (greenTEG) Core Body Temperature Service — a custom, publicly documented GATT profile
 * (not a Bluetooth SIG standard one, hence the full custom UUIDs rather than the `sig()`-style
 * short ones in [HeartRateProfile]/[Ftms]). Spec: "CORE SENSOR - Core Body Temperature Service
 * Specification" v2.2, published by CoreBodyTemp/greenteg.
 */
object CoreTempProfile {
    val SERVICE_CORE_TEMP: UUID = UUID.fromString("00002100-5B1E-4347-B07C-97B514DAE121")
    val CHAR_CORE_BODY_TEMPERATURE: UUID = UUID.fromString("00002101-5B1E-4347-B07C-97B514DAE121")
}

/** A single Core Body Temperature characteristic notification, decoded. Any field the sensor
 *  didn't flag as present (or reported as its "not available" sentinel) comes back null — the
 *  same optional-reading shape as [com.ergrm.trainer.ble.TrainerSample]'s cadence/HR fields. */
data class CoreTempReading(
    val coreTempC: Float?,
    val skinTempC: Float?,
    val heatStrainIndex: Float?,
    val hrBpm: Int?,
)

/** Parses the Core Body Temperature characteristic's payload: a Flags byte, then a mandatory
 *  core temperature, then whichever optional fields the Flags bits mark as present — in the
 *  fixed order the spec defines: skin temperature, core-reserved (skipped, unused), quality and
 *  state (skipped, unused), heart rate, heat strain index. All multi-byte fields are little-
 *  endian, per the spec's byte transmission order. */
object CoreTempParser {
    private const val NOT_AVAILABLE = 0x7FFF

    private const val FLAG_SKIN_TEMP = 0x01
    private const val FLAG_CORE_RESERVED = 0x02
    private const val FLAG_QUALITY_STATE = 0x04
    private const val FLAG_TEMP_UNIT_FAHRENHEIT = 0x08
    private const val FLAG_HEART_RATE = 0x10
    private const val FLAG_HEAT_STRAIN_INDEX = 0x20

    fun parse(bytes: ByteArray): CoreTempReading? {
        if (bytes.isEmpty()) return null
        var offset = 0
        val flags = bytes[offset].toInt() and 0xFF
        offset += 1

        fun readSInt16(): Int? {
            if (offset + 2 > bytes.size) return null
            val raw = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            return raw.toShort().toInt()
        }
        fun readUInt8(): Int? {
            if (offset >= bytes.size) return null
            val v = bytes[offset].toInt() and 0xFF
            offset += 1
            return v
        }
        fun toCelsius(raw: Int): Float {
            val value = raw / 100f
            return if (flags and FLAG_TEMP_UNIT_FAHRENHEIT != 0) (value - 32f) * 5f / 9f else value
        }

        val coreRaw = readSInt16() ?: return null
        val coreTempC = if (coreRaw == NOT_AVAILABLE) null else toCelsius(coreRaw)

        val skinTempC = if (flags and FLAG_SKIN_TEMP != 0) {
            readSInt16()?.let { if (it == NOT_AVAILABLE) null else toCelsius(it) }
        } else {
            null
        }
        if (flags and FLAG_CORE_RESERVED != 0) readSInt16()
        if (flags and FLAG_QUALITY_STATE != 0) readUInt8()
        val hrBpm = if (flags and FLAG_HEART_RATE != 0) readUInt8() else null
        // Stored in units of 1/10th HSI (0-25.4, capped) per the spec.
        val hsi = if (flags and FLAG_HEAT_STRAIN_INDEX != 0) readUInt8()?.let { it / 10f } else null

        return CoreTempReading(coreTempC, skinTempC, hsi, hrBpm)
    }
}

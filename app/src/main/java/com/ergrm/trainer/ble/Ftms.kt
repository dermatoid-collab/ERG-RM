package com.ergrm.trainer.ble

import java.util.UUID

/**
 * Bluetooth SIG Fitness Machine Service (FTMS) UUIDs and Control Point op codes.
 * Reference: Fitness Machine Service v1.0 / Fitness Machine Control Point spec.
 */
object Ftms {
    private fun sig(shortUuid: String): UUID =
        UUID.fromString("0000$shortUuid-0000-1000-8000-00805f9b34fb")

    val SERVICE_FITNESS_MACHINE: UUID = sig("1826")

    val CHAR_FITNESS_MACHINE_FEATURE: UUID = sig("2acc")
    val CHAR_INDOOR_BIKE_DATA: UUID = sig("2ad2")
    val CHAR_FITNESS_MACHINE_CONTROL_POINT: UUID = sig("2ad9")
    val CHAR_FITNESS_MACHINE_STATUS: UUID = sig("2ada")

    val CLIENT_CHARACTERISTIC_CONFIG: UUID = sig("2902")

    // Fitness Machine Control Point op codes (subset used for ERG mode)
    const val OP_REQUEST_CONTROL: Byte = 0x00
    const val OP_RESET: Byte = 0x01
    const val OP_SET_TARGET_POWER: Byte = 0x05
    const val OP_START_OR_RESUME: Byte = 0x07
    const val OP_STOP_OR_PAUSE: Byte = 0x08
    const val OP_RESPONSE_CODE: Byte = -0x80 // 0x80

    const val STOP_PARAM: Byte = 0x01 // used with OP_STOP_OR_PAUSE to fully stop (vs pause)

    // Result codes returned in a control point response (byte after requested op code)
    const val RESULT_SUCCESS: Byte = 0x01
    const val RESULT_OP_NOT_SUPPORTED: Byte = 0x02
    const val RESULT_INVALID_PARAMETER: Byte = 0x03
    const val RESULT_OPERATION_FAILED: Byte = 0x04
    const val RESULT_CONTROL_NOT_PERMITTED: Byte = 0x05
}

/** A single live data sample decoded from the Indoor Bike Data characteristic. */
data class TrainerSample(
    val speedKmh: Float? = null,
    val cadenceRpm: Float? = null,
    val powerWatts: Int? = null,
    val heartRateBpm: Int? = null,
)

/**
 * Parses the Indoor Bike Data characteristic (0x2AD2) payload per the FTMS spec.
 * Fields appear in a fixed order and are only present when their flag bit is set.
 */
object IndoorBikeDataParser {
    private const val FLAG_MORE_DATA = 0
    private const val FLAG_AVG_SPEED = 1
    private const val FLAG_INST_CADENCE = 2
    private const val FLAG_AVG_CADENCE = 3
    private const val FLAG_TOTAL_DISTANCE = 4
    private const val FLAG_RESISTANCE_LEVEL = 5
    private const val FLAG_INST_POWER = 6
    private const val FLAG_AVG_POWER = 7
    private const val FLAG_EXPENDED_ENERGY = 8
    private const val FLAG_HEART_RATE = 9
    private const val FLAG_METABOLIC_EQUIVALENT = 10
    private const val FLAG_ELAPSED_TIME = 11
    private const val FLAG_REMAINING_TIME = 12

    fun parse(bytes: ByteArray): TrainerSample {
        if (bytes.size < 2) return TrainerSample()
        var offset = 0
        val flags = readUInt16LE(bytes, offset).also { offset += 2 }

        var speedKmh: Float? = null
        var cadenceRpm: Float? = null
        var power: Int? = null
        var heartRate: Int? = null

        // Bit0 = 0 means Instantaneous Speed IS present.
        if (!flags.hasBit(FLAG_MORE_DATA) && offset + 2 <= bytes.size) {
            speedKmh = readUInt16LE(bytes, offset) / 100f
            offset += 2
        }
        if (flags.hasBit(FLAG_AVG_SPEED) && offset + 2 <= bytes.size) {
            offset += 2 // average speed, unused
        }
        if (flags.hasBit(FLAG_INST_CADENCE) && offset + 2 <= bytes.size) {
            cadenceRpm = readUInt16LE(bytes, offset) / 2f
            offset += 2
        }
        if (flags.hasBit(FLAG_AVG_CADENCE) && offset + 2 <= bytes.size) {
            offset += 2 // average cadence, unused
        }
        if (flags.hasBit(FLAG_TOTAL_DISTANCE) && offset + 3 <= bytes.size) {
            offset += 3 // uint24, unused
        }
        if (flags.hasBit(FLAG_RESISTANCE_LEVEL) && offset + 2 <= bytes.size) {
            offset += 2 // sint16, unused
        }
        if (flags.hasBit(FLAG_INST_POWER) && offset + 2 <= bytes.size) {
            power = readSInt16LE(bytes, offset)
            offset += 2
        }
        if (flags.hasBit(FLAG_AVG_POWER) && offset + 2 <= bytes.size) {
            offset += 2 // average power, unused
        }
        if (flags.hasBit(FLAG_EXPENDED_ENERGY) && offset + 5 <= bytes.size) {
            offset += 5 // total(2) + per-hour(2) + per-min(1), unused
        }
        if (flags.hasBit(FLAG_HEART_RATE) && offset + 1 <= bytes.size) {
            heartRate = bytes[offset].toInt() and 0xFF
            offset += 1
        }
        // Remaining fields (metabolic equivalent, elapsed/remaining time) are not read.

        return TrainerSample(
            speedKmh = speedKmh,
            cadenceRpm = cadenceRpm,
            powerWatts = power,
            heartRateBpm = heartRate,
        )
    }

    private fun Int.hasBit(bit: Int): Boolean = (this shr bit) and 1 == 1

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readSInt16LE(bytes: ByteArray, offset: Int): Int {
        val raw = readUInt16LE(bytes, offset)
        return raw.toShort().toInt()
    }
}

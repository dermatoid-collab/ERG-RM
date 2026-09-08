package com.ergrm.trainer.ble

import java.util.UUID

/**
 * Bluetooth SIG Heart Rate Service UUIDs — a standard profile exposed by chest straps and arm
 * bands, independent of the trainer's own FTMS service. Reference: Heart Rate Service v1.0.
 */
object HeartRateProfile {
    private fun sig(shortUuid: String): UUID =
        UUID.fromString("0000$shortUuid-0000-1000-8000-00805f9b34fb")

    val SERVICE_HEART_RATE: UUID = sig("180d")
    val CHAR_HEART_RATE_MEASUREMENT: UUID = sig("2a37")
}

/** Parses the Heart Rate Measurement characteristic (0x2A37) payload per the spec's flags byte. */
object HeartRateMeasurementParser {
    private const val FLAG_VALUE_FORMAT_16BIT = 0

    fun parseBpm(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        val flags = bytes[0].toInt()
        val is16Bit = (flags shr FLAG_VALUE_FORMAT_16BIT) and 1 == 1
        return if (is16Bit) {
            if (bytes.size < 3) return null
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else {
            if (bytes.size < 2) return null
            bytes[1].toInt() and 0xFF
        }
    }
}

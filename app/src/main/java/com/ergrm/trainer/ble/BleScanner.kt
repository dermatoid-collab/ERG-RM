package com.ergrm.trainer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class DiscoveredTrainer(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int,
)

/** Scans for nearby BLE devices advertising the FTMS (Fitness Machine) service. */
class BleScanner(private val adapter: BluetoothAdapter) {

    @SuppressLint("MissingPermission")
    fun scan(): Flow<DiscoveredTrainer> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth adapter unavailable or disabled"))
            return@callbackFlow
        }

        val seen = HashSet<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown trainer"
                if (seen.add(device.address)) {
                    trySend(DiscoveredTrainer(device, name, result.rssi))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed, error=$errorCode"))
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(Ftms.SERVICE_FITNESS_MACHINE))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, callback)

        awaitClose { scanner.stopScan(callback) }
    }
}

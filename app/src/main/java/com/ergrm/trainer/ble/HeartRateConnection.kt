package com.ergrm.trainer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HrConnectionState {
    data object Disconnected : HrConnectionState
    data object Connecting : HrConnectionState
    data object DiscoveringServices : HrConnectionState
    data object Ready : HrConnectionState
    data class Failed(val message: String) : HrConnectionState
}

/**
 * Owns a BLE connection to a standalone Bluetooth Heart Rate Service (0x180D) sensor — a chest
 * strap or arm band — entirely independent of [TrainerConnection]. Read-only: there is no control
 * point, just notifications on the Heart Rate Measurement characteristic.
 */
@SuppressLint("MissingPermission")
class HeartRateConnection(private val context: Context) {
    private val _connectionState = MutableStateFlow<HrConnectionState>(HrConnectionState.Disconnected)
    val connectionState: StateFlow<HrConnectionState> = _connectionState.asStateFlow()

    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm.asStateFlow()

    private var gatt: BluetoothGatt? = null

    fun connect(device: BluetoothDevice) {
        disconnect()
        _connectionState.value = HrConnectionState.Connecting
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.close()
        gatt = null
        _heartRateBpm.value = null
        _connectionState.value = HrConnectionState.Disconnected
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = HrConnectionState.DiscoveringServices
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = HrConnectionState.Disconnected
                    _heartRateBpm.value = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(HeartRateProfile.SERVICE_HEART_RATE)
            val measurement = service?.getCharacteristic(HeartRateProfile.CHAR_HEART_RATE_MEASUREMENT)
            if (measurement == null) {
                _connectionState.value = HrConnectionState.Failed("Heart Rate Service not found on device")
                return
            }
            g.setCharacteristicNotification(measurement, true)
            val descriptor = measurement.getDescriptor(Ftms.CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor != null) {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
            _connectionState.value = HrConnectionState.Ready
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HeartRateProfile.CHAR_HEART_RATE_MEASUREMENT) {
                _heartRateBpm.value = HeartRateMeasurementParser.parseBpm(characteristic.value ?: ByteArray(0))
            }
        }
    }
}

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

sealed interface CoreTempConnectionState {
    data object Disconnected : CoreTempConnectionState
    data object Connecting : CoreTempConnectionState
    data object DiscoveringServices : CoreTempConnectionState
    data object Ready : CoreTempConnectionState
    data class Failed(val message: String) : CoreTempConnectionState
}

/**
 * Owns a BLE connection to a CORE body temperature sensor's custom GATT service (core/skin
 * temperature, Heat Strain Index) — entirely independent of [TrainerConnection] and
 * [HeartRateConnection], same shape as the latter: read-only, no control point in use, just
 * notifications on the Core Body Temperature characteristic.
 */
@SuppressLint("MissingPermission")
class CoreTempConnection(private val context: Context) {
    private val _connectionState = MutableStateFlow<CoreTempConnectionState>(CoreTempConnectionState.Disconnected)
    val connectionState: StateFlow<CoreTempConnectionState> = _connectionState.asStateFlow()

    private val _reading = MutableStateFlow<CoreTempReading?>(null)
    val reading: StateFlow<CoreTempReading?> = _reading.asStateFlow()

    private var gatt: BluetoothGatt? = null

    /** See [TrainerConnection.connect] for what [autoConnect] does. */
    fun connect(device: BluetoothDevice, autoConnect: Boolean = false) {
        disconnect()
        _connectionState.value = CoreTempConnectionState.Connecting
        gatt = device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.close()
        gatt = null
        _reading.value = null
        _connectionState.value = CoreTempConnectionState.Disconnected
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = CoreTempConnectionState.DiscoveringServices
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = CoreTempConnectionState.Disconnected
                    _reading.value = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(CoreTempProfile.SERVICE_CORE_TEMP)
            val measurement = service?.getCharacteristic(CoreTempProfile.CHAR_CORE_BODY_TEMPERATURE)
            if (measurement == null) {
                _connectionState.value = CoreTempConnectionState.Failed("Core Body Temperature Service not found on device")
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
            _connectionState.value = CoreTempConnectionState.Ready
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CoreTempProfile.CHAR_CORE_BODY_TEMPERATURE) {
                _reading.value = CoreTempParser.parse(characteristic.value ?: ByteArray(0))
            }
        }
    }
}

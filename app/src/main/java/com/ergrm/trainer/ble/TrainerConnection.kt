package com.ergrm.trainer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed interface TrainerConnectionState {
    data object Disconnected : TrainerConnectionState
    data object Connecting : TrainerConnectionState
    data object DiscoveringServices : TrainerConnectionState
    data object RequestingControl : TrainerConnectionState
    data object Ready : TrainerConnectionState
    data class Failed(val message: String) : TrainerConnectionState
}

/**
 * Owns a single BLE connection to an FTMS-compatible smart trainer (e.g. Elite Direto)
 * and exposes ERG-mode target power control plus live ride data.
 *
 * GATT operations on Android must be serialized (only one outstanding request at a time),
 * so every read/write/descriptor-write goes through [gattMutex] and waits for its callback
 * via a [CompletableDeferred] before the next one is issued.
 */
@SuppressLint("MissingPermission")
class TrainerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val _connectionState = MutableStateFlow<TrainerConnectionState>(TrainerConnectionState.Disconnected)
    val connectionState: StateFlow<TrainerConnectionState> = _connectionState.asStateFlow()

    private val _liveData = MutableStateFlow(TrainerSample())
    val liveData: StateFlow<TrainerSample> = _liveData.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var controlPoint: BluetoothGattCharacteristic? = null

    private val gattMutex = Mutex()
    private var pendingOp: CompletableDeferred<Boolean>? = null
    private var pendingControlResponse: CompletableDeferred<Byte>? = null

    fun connect(device: BluetoothDevice) {
        disconnect()
        _connectionState.value = TrainerConnectionState.Connecting
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.close()
        gatt = null
        controlPoint = null
        _connectionState.value = TrainerConnectionState.Disconnected
    }

    /**
     * Sends an explicit Stop command and waits for it (best-effort, bounded by [stop]'s own
     * timeout) before closing the BLE link, instead of just dropping the connection while the
     * trainer may still be holding an ERG target. FTMS-compliant trainers are required to fall
     * back to a safe state on their own once the control connection is lost, but a clean stop
     * first avoids relying on that fallback's timeout.
     */
    suspend fun stopAndDisconnect() {
        stop()
        disconnect()
    }

    /** Sets the ERG-mode target power. Op code 0x05 + sint16 LE watts. */
    suspend fun setTargetPowerWatts(watts: Int) {
        val cp = controlPoint ?: return
        val payload = ByteBuffer.allocate(3)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(Ftms.OP_SET_TARGET_POWER)
            .putShort(watts.toShort())
            .array()
        writeControlPoint(cp, payload)
    }

    suspend fun startOrResume() {
        val cp = controlPoint ?: return
        writeControlPoint(cp, byteArrayOf(Ftms.OP_START_OR_RESUME))
    }

    suspend fun stop() {
        val cp = controlPoint ?: return
        writeControlPoint(cp, byteArrayOf(Ftms.OP_STOP_OR_PAUSE, Ftms.STOP_PARAM))
    }

    private suspend fun writeControlPoint(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val g = gatt ?: return
        gattMutex.withLock {
            val opDeferred = CompletableDeferred<Boolean>()
            val responseDeferred = CompletableDeferred<Byte>()
            pendingOp = opDeferred
            pendingControlResponse = responseDeferred

            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            val started = g.writeCharacteristic(characteristic)
            if (!started) {
                pendingOp = null
                pendingControlResponse = null
                return
            }

            try {
                withTimeout(4000) { opDeferred.await() }
                withTimeout(4000) { responseDeferred.await() }
            } catch (t: Throwable) {
                // Timed out waiting for write ack or indication response; caller can retry.
            } finally {
                pendingOp = null
                pendingControlResponse = null
            }
        }
    }

    private suspend fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray) {
        val g = gatt ?: return
        gattMutex.withLock {
            val deferred = CompletableDeferred<Boolean>()
            pendingOp = deferred
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            val started = g.writeDescriptor(descriptor)
            if (!started) {
                pendingOp = null
                return
            }
            try {
                withTimeout(4000) { deferred.await() }
            } catch (t: Throwable) {
                // ignore timeout, proceed best-effort
            } finally {
                pendingOp = null
            }
        }
    }

    private suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic, indication: Boolean) {
        val g = gatt ?: return
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(Ftms.CLIENT_CHARACTERISTIC_CONFIG) ?: return
        val value = if (indication) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        writeDescriptor(descriptor, value)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = TrainerConnectionState.DiscoveringServices
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = TrainerConnectionState.Disconnected
                    controlPoint = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(Ftms.SERVICE_FITNESS_MACHINE)
            if (service == null) {
                _connectionState.value = TrainerConnectionState.Failed("Fitness Machine Service not found on device")
                return
            }
            val bikeData = service.getCharacteristic(Ftms.CHAR_INDOOR_BIKE_DATA)
            val cp = service.getCharacteristic(Ftms.CHAR_FITNESS_MACHINE_CONTROL_POINT)
            val status2ada = service.getCharacteristic(Ftms.CHAR_FITNESS_MACHINE_STATUS)
            controlPoint = cp

            scope.launch {
                if (bikeData != null) enableNotifications(bikeData, indication = false)
                if (status2ada != null) enableNotifications(status2ada, indication = false)
                if (cp != null) {
                    enableNotifications(cp, indication = true)
                    _connectionState.value = TrainerConnectionState.RequestingControl
                    writeControlPoint(cp, byteArrayOf(Ftms.OP_REQUEST_CONTROL))
                    startOrResume()
                    _connectionState.value = TrainerConnectionState.Ready
                } else {
                    _connectionState.value = TrainerConnectionState.Failed("Control point characteristic not found")
                }
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChanged(characteristic.uuid, characteristic.value ?: ByteArray(0))
        }

        private fun handleCharacteristicChanged(uuid: java.util.UUID, value: ByteArray) {
            when (uuid) {
                Ftms.CHAR_INDOOR_BIKE_DATA -> {
                    _liveData.value = IndoorBikeDataParser.parse(value)
                }
                Ftms.CHAR_FITNESS_MACHINE_CONTROL_POINT -> {
                    if (value.size >= 3 && value[0] == Ftms.OP_RESPONSE_CODE) {
                        pendingControlResponse?.complete(value[2])
                    }
                }
            }
        }
    }
}

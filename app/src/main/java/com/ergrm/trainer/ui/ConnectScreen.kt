package com.ergrm.trainer.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.ble.DiscoveredDevice
import com.ergrm.trainer.ble.HrConnectionState
import com.ergrm.trainer.ble.TrainerConnectionState

private val requiredBluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

// Requested alongside the required ones, but optional: without it the foreground-service
// notification just stays invisible, scanning and ERG control still work fine.
private val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requiredBluetoothPermissions + Manifest.permission.POST_NOTIFICATIONS
    } else {
        requiredBluetoothPermissions
    }

@Composable
fun ConnectScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val hrConnectionState by viewModel.hrConnectionState.collectAsState()
    val hrScanResults by viewModel.hrScanResults.collectAsState()
    val isHrScanning by viewModel.isHrScanning.collectAsState()

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.startScan() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val requiredGranted = requiredBluetoothPermissions.all { grants[it] == true }
        if (requiredGranted) {
            if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
                enableBtLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                viewModel.startScan()
            }
        }
    }

    val hrPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val requiredGranted = requiredBluetoothPermissions.all { grants[it] == true }
        if (requiredGranted) {
            viewModel.startHrScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(
            text = connectionLabel(connectionState),
            style = MaterialTheme.typography.titleMedium,
        )

        if (connectionState is TrainerConnectionState.Ready) {
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Disconnect trainer")
            }
        } else {
            Button(
                onClick = { permissionLauncher.launch(bluetoothPermissions) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(if (isScanning) "Scanning…" else "Search for trainer")
            }
        }

        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            scanResults.forEach { result ->
                DeviceRow(result) { viewModel.connectToDevice(result) }
            }
        }

        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
        )
        Text(
            text = hrConnectionLabel(hrConnectionState),
            style = MaterialTheme.typography.titleMedium,
        )

        if (hrConnectionState is HrConnectionState.Ready) {
            OutlinedButton(
                onClick = { viewModel.disconnectHrSensor() },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Disconnect heart rate sensor")
            }
        } else {
            Button(
                onClick = { hrPermissionLauncher.launch(bluetoothPermissions) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(if (isHrScanning) "Scanning…" else "Search for heart rate sensor")
            }
        }

        if (isHrScanning) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            hrScanResults.forEach { result ->
                DeviceRow(result) { viewModel.connectHrSensor(result) }
            }
        }
    }
}

@Composable
private fun DeviceRow(result: DiscoveredDevice, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(result.name, style = MaterialTheme.typography.bodyLarge)
                Text(result.device.address, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

private fun connectionLabel(state: TrainerConnectionState): String = when (state) {
    is TrainerConnectionState.Disconnected -> "No trainer connected"
    is TrainerConnectionState.Connecting -> "Connecting…"
    is TrainerConnectionState.DiscoveringServices -> "Discovering FTMS services…"
    is TrainerConnectionState.RequestingControl -> "Requesting ERG control…"
    is TrainerConnectionState.Ready -> "Connected"
    is TrainerConnectionState.Failed -> "Error: ${state.message}"
}

private fun hrConnectionLabel(state: HrConnectionState): String = when (state) {
    is HrConnectionState.Disconnected -> "No heart rate sensor connected"
    is HrConnectionState.Connecting -> "Connecting…"
    is HrConnectionState.DiscoveringServices -> "Discovering heart rate service…"
    is HrConnectionState.Ready -> "Connected"
    is HrConnectionState.Failed -> "Error: ${state.message}"
}

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.ble.DiscoveredTrainer
import com.ergrm.trainer.ble.TrainerConnectionState

private val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun ConnectScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.startScan() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
                enableBtLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                viewModel.startScan()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        Button(
            onClick = { permissionLauncher.launch(bluetoothPermissions) },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(if (isScanning) "Ricerca in corso…" else "Cerca trainer")
        }

        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(scanResults, key = { it.device.address }) { result ->
                TrainerRow(result) { viewModel.connectToDevice(result) }
            }
        }
    }
}

@Composable
private fun TrainerRow(result: DiscoveredTrainer, onConnect: () -> Unit) {
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
            Button(onClick = onConnect) { Text("Connetti") }
        }
    }
}

private fun connectionLabel(state: TrainerConnectionState): String = when (state) {
    is TrainerConnectionState.Disconnected -> "Nessun trainer connesso"
    is TrainerConnectionState.Connecting -> "Connessione in corso…"
    is TrainerConnectionState.DiscoveringServices -> "Rilevamento servizi FTMS…"
    is TrainerConnectionState.RequestingControl -> "Richiesta controllo ERG…"
    is TrainerConnectionState.Ready -> "Connesso"
    is TrainerConnectionState.Failed -> "Errore: ${state.message}"
}

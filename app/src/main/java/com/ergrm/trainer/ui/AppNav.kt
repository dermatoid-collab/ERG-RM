package com.ergrm.trainer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ergrm.trainer.ble.TrainerConnectionState
import com.ergrm.trainer.ui.theme.ErgRmTheme

@Composable
fun ErgRmApp(viewModel: MainViewModel = viewModel()) {
    ErgRmTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var showSettings by remember { mutableStateOf(false) }
            val connectionState by viewModel.connectionState.collectAsState()
            val settings by viewModel.settings.collectAsState()

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("ERG-RM") },
                        actions = {
                            IconButton(onClick = { showSettings = !showSettings }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when {
                        showSettings -> SettingsScreen(
                            settings = settings,
                            onSave = viewModel::saveIntervalsSettings,
                            onClose = { showSettings = false },
                        )
                        connectionState is TrainerConnectionState.Ready -> WorkoutScreen(viewModel)
                        else -> ConnectScreen(viewModel)
                    }
                }
            }
        }
    }
}

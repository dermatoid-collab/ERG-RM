package com.ergrm.trainer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ergrm.trainer.data.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (apiKey: String, athleteId: String, ftpWatts: Int) -> Unit,
    onClose: () -> Unit,
) {
    var apiKey by remember(settings) { mutableStateOf(settings.intervalsApiKey) }
    var athleteId by remember(settings) { mutableStateOf(settings.intervalsAthleteId) }
    var ftpText by remember(settings) { mutableStateOf(settings.ftpWatts.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.titleLarge)

        Text(
            "Intervals.icu",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = athleteId,
            onValueChange = { athleteId = it },
            label = { Text("Athlete ID (es. i123456)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        OutlinedTextField(
            value = ftpText,
            onValueChange = { ftpText = it.filter(Char::isDigit) },
            label = { Text("FTP (watt)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            "La API key si trova in Intervals.icu → Settings → Developer Settings. " +
                "L'FTP viene usato per convertire i target del workout (% FTP) in watt assoluti da inviare al trainer.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        Button(
            onClick = {
                onSave(apiKey.trim(), athleteId.trim(), ftpText.toIntOrNull() ?: settings.ftpWatts)
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salva") }

        TextButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
            Text("Annulla")
        }
    }
}

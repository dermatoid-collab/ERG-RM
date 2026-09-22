package com.ergrm.trainer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ergrm.trainer.BuildConfig
import com.ergrm.trainer.data.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    viewModel: MainViewModel,
    onSave: (apiKey: String, athleteId: String, ftpWatts: Int, lthrBpm: Int) -> Unit,
    onClose: () -> Unit,
) {
    var apiKey by remember(settings) { mutableStateOf(settings.intervalsApiKey) }
    var athleteId by remember(settings) { mutableStateOf(settings.intervalsAthleteId) }
    var ftpText by remember(settings) { mutableStateOf(settings.ftpWatts.toString()) }
    var lthrText by remember(settings) { mutableStateOf(settings.lthrBpm.toString()) }
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) pendingImportUri = uri }

    val backupFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast(':') ?: "Folder"
            viewModel.onBackupFolderPicked(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

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
            label = { Text("Athlete ID (e.g. i123456)") },
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
        OutlinedTextField(
            value = lthrText,
            onValueChange = { lthrText = it.filter(Char::isDigit) },
            label = { Text("LTHR (bpm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        OutlinedButton(
            onClick = {
                viewModel.syncAthleteSettings { syncedFtp, syncedLthr ->
                    syncedFtp?.let { ftpText = it.toString() }
                    syncedLthr?.let { lthrText = it.toString() }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) { Text("Sync from Intervals.icu") }

        Text(
            "You'll find the API key in Intervals.icu → Settings → Developer Settings. " +
                "FTP is used to convert the workout's targets (% FTP) into absolute watts sent to the trainer. " +
                "LTHR (lactate threshold heart rate) sets the live chart's heart rate scale. " +
                "Sync fills these two fields from your Intervals.icu profile (preferring indoor FTP) — " +
                "review them and tap Save, same as editing them by hand.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        Button(
            onClick = {
                onSave(
                    apiKey.trim(),
                    athleteId.trim(),
                    ftpText.toIntOrNull() ?: settings.ftpWatts,
                    lthrText.toIntOrNull() ?: settings.lthrBpm,
                )
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }

        TextButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
            Text("Cancel")
        }

        Text(
            "Backup",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(
            "Save everything on this screen plus your FTP/LTHR, remembered trainer and heart " +
                "rate sensor, and your full workout history to one file — useful before " +
                "reinstalling the app, since that wipes all of it.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedButton(
            onClick = {
                viewModel.exportBackup { file ->
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/gzip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Export backup"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export backup") }
        OutlinedButton(
            onClick = { importLauncher.launch("*/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) { Text("Import backup") }

        Text(
            if (settings.backupFolderUri == null) {
                "Auto-backup is off. Pick a folder (e.g. one synced with Drive) and every " +
                    "saved ride backs up there automatically — no need to remember to export."
            } else {
                "Auto-backup folder: ${settings.backupFolderName}. Every saved ride writes a " +
                    "fresh backup there automatically."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        OutlinedButton(
            onClick = { backupFolderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (settings.backupFolderUri == null) "Choose auto-backup folder" else "Change auto-backup folder")
        }

        Text(
            "Build ${BuildConfig.GIT_SHA}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 24.dp),
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Apply this backup?") },
            text = {
                Text(
                    "This overwrites your current API key, Athlete ID, FTP, LTHR, and " +
                        "remembered trainer/HR sensor with what's in the file, and adds any " +
                        "sessions from it to your history. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackup(uri)
                    pendingImportUri = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            },
        )
    }
}

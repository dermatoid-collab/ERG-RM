package com.ergrm.trainer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.ergrm.trainer.BuildConfig
import com.ergrm.trainer.data.AppSettings
import com.ergrm.trainer.ui.theme.ThemeCategory
import com.ergrm.trainer.ui.theme.ThemeMode
import com.ergrm.trainer.ui.theme.themeCategory
import com.ergrm.trainer.ui.theme.themeSwatches

@Composable
fun SettingsScreen(
    settings: AppSettings,
    viewModel: MainViewModel,
    onSave: (apiKey: String, athleteId: String, ftpWatts: Int, lthrBpm: Int) -> Unit,
    onSaveSim: (riderWeightKg: Float, accessoriesWeightKg: Float, slopeStepPercent: Float, maxGradePercent: Float) -> Unit,
    onClose: () -> Unit,
) {
    var apiKey by remember(settings) { mutableStateOf(settings.intervalsApiKey) }
    var athleteId by remember(settings) { mutableStateOf(settings.intervalsAthleteId) }
    var ftpText by remember(settings) { mutableStateOf(settings.ftpWatts.toString()) }
    var lthrText by remember(settings) { mutableStateOf(settings.lthrBpm.toString()) }
    var riderWeightText by remember(settings) { mutableStateOf(formatDecimal(settings.riderWeightKg)) }
    var accessoriesWeightText by remember(settings) { mutableStateOf(formatDecimal(settings.accessoriesWeightKg)) }
    var slopeStepText by remember(settings) { mutableStateOf(formatDecimal(settings.slopeStepPercent)) }
    var maxGradeText by remember(settings) { mutableStateOf(formatDecimal(settings.maxGradePercent)) }
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) pendingImportUri = uri }

    val backupFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Same fix as LibraryScreen's folder picker: the tree URI's own last path segment is
            // the provider's internal document ID (opaque for Google Drive), not a human name —
            // DocumentFile queries the provider for its real display name instead.
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Folder"
            viewModel.onBackupFolderPicked(uri, name)
        }
    }

    val gpxFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Folder"
            viewModel.onGpxFolderPicked(uri, name)
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
            "SLO (slope simulation)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = riderWeightText,
            onValueChange = { riderWeightText = it.filterDecimal() },
            label = { Text("Rider weight (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = accessoriesWeightText,
            onValueChange = { accessoriesWeightText = it.filterDecimal() },
            label = { Text("Bike + accessories weight (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        OutlinedTextField(
            value = slopeStepText,
            onValueChange = { slopeStepText = it.filterDecimal() },
            label = { Text("Slope step (%)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        OutlinedTextField(
            value = maxGradeText,
            onValueChange = { maxGradeText = it.filterDecimal() },
            label = { Text("Max grade (%)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Text(
            "Rider and bike+accessories (clothing, gear, bottles) weight, sent together, let " +
                "the trainer compute the right resistance for a given grade — the app never " +
                "does that math itself. Slope step sets how much each ↓/↑ tap changes the " +
                "grade in SLO mode. Max grade caps how steep a slope the app will ever " +
                "request, since it depends on which trainer is connected, not on anything the " +
                "app can detect — your Elite Direto (1st gen) tops out at 14%.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Button(
            onClick = {
                onSaveSim(
                    riderWeightText.toFloatOrNull() ?: settings.riderWeightKg,
                    accessoriesWeightText.toFloatOrNull() ?: settings.accessoriesWeightKg,
                    slopeStepText.toFloatOrNull() ?: settings.slopeStepPercent,
                    maxGradeText.toFloatOrNull() ?: settings.maxGradePercent,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        Text(
            "Weight isn't sent to the trainer automatically — it shares a Bluetooth setting " +
                "with the trainer's own app, and an automatic write could silently overwrite " +
                "what you've set there. Tap below only when you want this app's weight to win.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        OutlinedButton(
            onClick = { viewModel.sendWeightToTrainer() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Send weight to trainer now") }

        Text(
            "Settings backup",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(
            "Save your Intervals.icu credentials, FTP/LTHR, remembered trainer/HR sensor and " +
                "library folder to one small file. Workout history isn't included here — it " +
                "backs up separately and automatically, see below.",
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
                    AppNavigationEvents.pickerLaunchInFlight = true
                    context.startActivity(Intent.createChooser(sendIntent, "Export backup"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export backup") }
        OutlinedButton(
            onClick = {
                AppNavigationEvents.pickerLaunchInFlight = true
                importLauncher.launch("*/*")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) { Text("Import backup") }

        Text(
            "Workout history backup",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(
            if (settings.backupFolderUri == null) {
                "Auto-backup is off. Pick a folder (e.g. one synced with Drive) and every saved " +
                    "ride writes its own backup file there automatically — no need to remember " +
                    "to export. The History screen pulls in anything it doesn't already have " +
                    "from this folder, e.g. after a reinstall."
            } else {
                "Auto-backup folder: ${settings.backupFolderName}. Every saved ride writes its " +
                    "own backup file there automatically. The History screen pulls in anything " +
                    "it doesn't already have from this folder, e.g. after a reinstall."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedButton(
            onClick = {
                AppNavigationEvents.pickerLaunchInFlight = true
                backupFolderPicker.launch(null)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (settings.backupFolderUri == null) "Choose auto-backup folder" else "Change auto-backup folder")
        }

        Text(
            "GPX routes folder",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(
            if (settings.gpxFolderUri == null) {
                "No folder set. Pick one (e.g. a synced Drive folder) and \"GPX route\" in " +
                    "Other sources lists its .gpx files directly instead of opening the system " +
                    "file picker each time."
            } else {
                "GPX folder: ${settings.gpxFolderName}. \"GPX route\" in Other sources lists " +
                    "its .gpx files directly."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedButton(
            onClick = {
                AppNavigationEvents.pickerLaunchInFlight = true
                gpxFolderPicker.launch(null)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (settings.gpxFolderUri == null) "Choose GPX folder" else "Change GPX folder")
        }

        Text(
            "Data sources",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(
            "Workouts, calendar and sport settings (FTP/LTHR) are read from your own " +
                "Intervals.icu account via its public API. Some of that data may originate " +
                "from a Garmin device synced to Intervals.icu.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ERG-RM Legacy always first and on its own, outside any category — the original
            // look, not one choice among the 39 others.
            ThemeModeGrid(
                modes = listOf(ThemeMode.ERG_RM_LEGACY),
                selectedMode = settings.themeMode,
                onSelect = { viewModel.setThemeMode(it) },
            )
            ThemeCategory.entries.forEach { category ->
                val modes = ThemeMode.entries.filter { themeCategory(it) == category }
                if (modes.isNotEmpty()) {
                    Text(
                        category.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ThemeModeGrid(
                        modes = modes,
                        selectedMode = settings.themeMode,
                        onSelect = { viewModel.setThemeMode(it) },
                    )
                }
            }
        }

        Text(
            "#${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_DATE} · ©RM",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            textAlign = TextAlign.Center,
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Apply this backup?") },
            text = {
                Text(
                    "This overwrites your current API key, Athlete ID, FTP, LTHR, remembered " +
                        "trainer/HR sensor and library folder with what's in the file. This " +
                        "can't be undone.",
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

/** Drops the trailing ".0" a plain Float->String would otherwise show for whole numbers (78kg
 *  reads as "78", not "78.0"), while still showing real decimals (e.g. a 1.5% slope step). */
private fun formatDecimal(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

/** Keeps digits and at most one decimal point — these fields take a plain positive decimal
 *  number (weight in kg, a percentage), never negative or scientific notation. */
private fun String.filterDecimal(): String {
    var seenDot = false
    return filter { c ->
        when {
            c.isDigit() -> true
            c == '.' && !seenDot -> { seenDot = true; true }
            else -> false
        }
    }
}

/** A category's (or Legacy's own single-item) set of [ThemeOptionCard]s, 3 per row — shared so
 *  the picker's grid sizing/spacing can't drift between Legacy's standalone row and every
 *  category's own grid below it. */
@Composable
private fun ThemeModeGrid(modes: List<ThemeMode>, selectedMode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.chunked(3).forEach { rowModes ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowModes.forEach { mode ->
                    ThemeOptionCard(
                        mode = mode,
                        selected = selectedMode == mode,
                        onClick = { onSelect(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a shorter row's cards the same width as every other row's instead of
                // stretching to fill the gap — including Legacy's own 1-card row.
                repeat(3 - rowModes.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** One selectable theme: its name plus a row of swatches from [themeSwatches] (background,
 *  primary, secondary, tertiary) so a rider can tell palettes apart without applying each one
 *  first. A colored border marks the currently active theme. */
@Composable
private fun ThemeOptionCard(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatches = themeSwatches(mode)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            swatches.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
                )
            }
        }
        Text(
            mode.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

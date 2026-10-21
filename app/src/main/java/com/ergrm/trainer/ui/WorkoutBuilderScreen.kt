package com.ergrm.trainer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ergrm.trainer.ui.theme.ErgAccent
import com.ergrm.trainer.workout.WorkoutDslParser
import com.ergrm.trainer.workout.WorkoutStep
import com.ergrm.trainer.workout.previewStats
import kotlinx.coroutines.launch

/**
 * Text-DSL workout editor — same grammar Intervals.icu's own "Workout Builder" documents
 * (https://forum.intervals.icu/t/workout-builder/1163): blank-line-separated blocks, an optional
 * "4x"-style repeat count on a block's free-text header line, and "- [label] <duration> <power>
 * [hr] [cadence]" step lines. Reached via its own topBar pencil icon (see AppNav), one of the
 * app's swipeable main pages like Library/History/Settings.
 *
 * Save is always "Save As" (ActivityResultContracts.CreateDocument): the Workout Library folder
 * only holds a read [DocumentsContract] tree permission (see AppNav/MainViewModel), so writing
 * into it needs the system's own one-off create-file picker rather than a direct write — hinted
 * to open there via EXTRA_INITIAL_URI, but the rider can always pick somewhere else instead.
 */
@Composable
fun WorkoutBuilderScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val builder by viewModel.builderState.collectAsState()
    var showHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Hints the system Save-As picker to open in the configured Workout Library folder, via the
    // same EXTRA_INITIAL_URI the plain CreateDocument contract doesn't expose a way to set —
    // the rider can still navigate elsewhere in the picker, this only sets where it starts.
    val initialSaveUri = settings.libraryFolderUri?.let { Uri.parse(it) }
    val saveLauncher = rememberLauncherForActivityResult(
        remember(initialSaveUri) { InitialUriCreateDocument(initialSaveUri) },
    ) { uri -> if (uri != null) viewModel.exportBuilderDraft(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "workout"
            viewModel.importBuilderFile(uri, name)
        }
    }

    val draft = viewModel.currentBuilderDraft()
    val previewSteps = viewModel.builderPreviewSteps()
    val parseError = (builder.parseResult as? WorkoutDslParser.ParseResult.Error)
    val canSave = builder.name.isNotBlank() && draft != null

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = builder.name,
                onValueChange = viewModel::updateBuilderName,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Name — required") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                ),
            )
            IconButton(onClick = { showHelp = true }) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Workout syntax help")
            }
            IconButton(onClick = {
                AppNavigationEvents.pickerLaunchInFlight = true
                importLauncher.launch(arrayOf("*/*"))
            }) {
                Icon(Icons.Filled.UploadFile, contentDescription = "Import / Upload")
            }
            IconButton(
                onClick = {
                    AppNavigationEvents.pickerLaunchInFlight = true
                    saveLauncher.launch("${builder.name}.zwo")
                },
                enabled = canSave,
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = "Save as…",
                    tint = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }

        OutlinedTextField(
            value = builder.description,
            onValueChange = viewModel::updateBuilderDescription,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            placeholder = { Text("Description (optional)") },
            minLines = 2,
            maxLines = 3,
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val lineCount = builder.text.count { it == '\n' } + 1
            val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
            Text(
                text = (1..lineCount).joinToString("\n"),
                style = mono.copy(textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
                modifier = Modifier
                    .widthIn(min = 28.dp)
                    .padding(start = 12.dp, top = 12.dp, end = 8.dp),
            )
            TextField(
                value = builder.text,
                onValueChange = viewModel::updateBuilderText,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                textStyle = mono,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        HorizontalDivider()

        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "LIVE PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (parseError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(14.dp))
                        Text(
                            " Line ${parseError.line}: ${parseError.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = ErgAccent, modifier = Modifier.height(14.dp))
                        Text(" Parses OK", style = MaterialTheme.typography.labelSmall, color = ErgAccent)
                    }
                }
            }
            if (previewSteps != null && previewSteps.isNotEmpty()) {
                BuilderPreviewStats(previewSteps, settings.ftpWatts)
            }
        }
    }

    if (showHelp) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { showHelp = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Workout syntax", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { scope.launch { sheetState.hide(); showHelp = false } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                HelpSection(
                    "Blocks",
                    "A blank line starts a new block. The first line of a block is a free-text header — give it a repeat count like \"4x\" to repeat every step under it. A block whose first line already starts with \"-\" has no header: it's anonymous and runs once.",
                    "Warmup\n- 10m 50%\n\n4x\n- 5m 90%\n- 3m 50%\n\nCooldown\n- 10m 40%",
                )
                HelpSection(
                    "Step lines",
                    "Start with \"-\", then duration, then power. Optional HR and cadence follow. Any free text before the duration becomes the step's own label.",
                    "- Big gear 5m 90% 60rpm",
                )
                HelpSection("Duration", "30s · 10m · 1m30 (1 min 30s) · 1h30m", null)
                HelpSection("Power", "220w (watts) · 90% or 60-80% (percent FTP — a range is a ramp) · Z3 (zone)", null)
                HelpSection("Heart rate", "75-85% HR (percent max HR) · 70-80% LTHR (percent LTHR) · Z2 HR (HR zone)", null)
                HelpSection("Cadence", "85rpm · 85-95rpm", null)
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String, example: String?) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        if (example != null) {
            Text(
                example,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun BuilderPreviewStats(steps: List<WorkoutStep>, ftpWatts: Int) {
    val stats = steps.previewStats()
    val durationSec = steps.sumOf { it.durationSec }
    val intensityFactor = if (ftpWatts > 0 && stats.normalizedWatts != null) stats.normalizedWatts.toFloat() / ftpWatts else null
    val load = if (intensityFactor != null && stats.normalizedWatts != null) {
        (durationSec * stats.normalizedWatts * intensityFactor / (ftpWatts * 3600.0) * 100.0).toInt()
    } else null

    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        BuilderStatTile("Duration", formatHms(durationSec), Modifier.weight(1f))
        BuilderStatTile("Load", load?.toString() ?: "–", Modifier.weight(1f))
        BuilderStatTile("Intensity", intensityFactor?.let { "%.2f".format(it) } ?: "–", Modifier.weight(1f))
        BuilderStatTile("NP", stats.normalizedWatts?.let { "${it}W" } ?: "–", Modifier.weight(1f))
    }
    WorkoutMiniChart(steps, ftpWatts, modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 10.dp))

    Row(modifier = Modifier.fillMaxWidth().height(7.dp).padding(top = 8.dp)) {
        steps.forEach { step ->
            val avgWatts = (step.startWatts + step.endWatts) / 2
            val zone = zoneFor(avgWatts, ftpWatts, Color.Gray)
            Canvas(
                modifier = Modifier
                    .weight(step.durationSec.toFloat().coerceAtLeast(0.001f))
                    .fillMaxSize(),
            ) {
                drawRect(color = zone.color)
            }
        }
    }
}

@Composable
private fun BuilderStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun formatHms(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** [ActivityResultContracts.CreateDocument] has no built-in way to hint a starting folder — this
 *  just adds EXTRA_INITIAL_URI (API 26+; a no-op on older Android, which simply opens wherever
 *  the system picker last was) to the Intent it already builds. */
private class InitialUriCreateDocument(private val initialUri: Uri?) : ActivityResultContracts.CreateDocument("application/xml") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).apply {
            if (initialUri != null) putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
}

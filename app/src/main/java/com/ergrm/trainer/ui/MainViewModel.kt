package com.ergrm.trainer.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ergrm.trainer.ble.BleScanner
import com.ergrm.trainer.ble.CoreTempConnection
import com.ergrm.trainer.ble.CoreTempProfile
import com.ergrm.trainer.ble.DiscoveredDevice
import com.ergrm.trainer.ble.DiscoveredTrainer
import com.ergrm.trainer.ble.Ftms
import com.ergrm.trainer.ble.HeartRateConnection
import com.ergrm.trainer.ble.HeartRateProfile
import com.ergrm.trainer.ble.TrainerConnection
import com.ergrm.trainer.ble.TrainerConnectionState
import com.ergrm.trainer.ble.TrainerSample
import com.ergrm.trainer.backup.BackupReminderScheduler
import com.ergrm.trainer.data.AppSettings
import com.ergrm.trainer.data.BackupRepository
import com.ergrm.trainer.data.SettingsRepository
import com.ergrm.trainer.history.HeatTrainingLoad
import com.ergrm.trainer.history.SessionHistoryRepository
import com.ergrm.trainer.history.SessionSample
import com.ergrm.trainer.history.WorkoutSession
import com.ergrm.trainer.history.WorkoutSource
import com.ergrm.trainer.intervals.AthleteSettingsResult
import com.ergrm.trainer.intervals.CalendarCacheRepository
import com.ergrm.trainer.intervals.CalendarFetchResult
import com.ergrm.trainer.intervals.CalendarWorkout
import com.ergrm.trainer.intervals.FetchResult
import com.ergrm.trainer.intervals.IntervalsRepository
import com.ergrm.trainer.intervals.LibraryFetchResult
import com.ergrm.trainer.intervals.LibraryFolderGroup
import com.ergrm.trainer.intervals.LibraryWorkout
import com.ergrm.trainer.library.GpxImportResult
import com.ergrm.trainer.library.GpxLibraryRepository
import com.ergrm.trainer.library.GpxRouteFile
import com.ergrm.trainer.library.LibraryImportResult
import com.ergrm.trainer.library.LibraryRepository
import com.ergrm.trainer.library.LibraryWorkoutFile
import com.ergrm.trainer.library.LocalFolderGroup
import com.ergrm.trainer.notify.AppNotifications
import com.ergrm.trainer.service.TrainerForegroundService
import com.ergrm.trainer.ui.theme.ThemeMode
import com.ergrm.trainer.workout.DraftStep
import com.ergrm.trainer.workout.ErgParser
import com.ergrm.trainer.workout.GpxParser
import com.ergrm.trainer.workout.PowerTarget
import com.ergrm.trainer.workout.StructuredZwoReader
import com.ergrm.trainer.workout.WorkoutBlock
import com.ergrm.trainer.workout.WorkoutDraft
import com.ergrm.trainer.workout.WorkoutDslParser
import com.ergrm.trainer.workout.WorkoutDslSerializer
import com.ergrm.trainer.workout.WorkoutExecutor
import com.ergrm.trainer.workout.WorkoutStep
import com.ergrm.trainer.workout.ZwoWriter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Why [MainViewModel.autoBackupNeeded] fired — WorkoutScreen shows a different dialog message
 *  for each, but both point at the same fix (Settings' backup folder picker). */
enum class AutoBackupNeededReason { NOT_CONFIGURED, WRITE_FAILED }

sealed interface WorkoutLoadState {
    data object Idle : WorkoutLoadState
    data object Loading : WorkoutLoadState
    // [source] is null for a GPX route or anything else with no traceable origin — exitWorkout()
    // carries it straight onto the saved WorkoutSession for a later exact re-import.
    data class Loaded(val name: String, val source: WorkoutSource? = null) : WorkoutLoadState
    data object Empty : WorkoutLoadState
    data class Error(val message: String) : WorkoutLoadState
}

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    // isStale: true means these came from CalendarCacheRepository (the last successful fetch,
    // persisted to disk) rather than this session's own live fetch — e.g. right after launch,
    // before the startup refresh lands, or after a refresh that failed but left the cache as the
    // best available data (see MainViewModel.fetchCalendarWorkouts).
    data class Loaded(val workouts: List<CalendarWorkout>, val isStale: Boolean = false) : CalendarUiState
    data class Error(val message: String) : CalendarUiState
}

sealed interface IntervalsLibraryUiState {
    data object Loading : IntervalsLibraryUiState
    data class Loaded(val folders: List<LibraryFolderGroup>) : IntervalsLibraryUiState
    data class Error(val message: String) : IntervalsLibraryUiState
}

sealed interface LibraryUiState {
    data object NoFolder : LibraryUiState
    data object Loading : LibraryUiState
    data class Loaded(val folders: List<LocalFolderGroup>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

sealed interface GpxLibraryUiState {
    data object NoFolder : GpxLibraryUiState
    data object Loading : GpxLibraryUiState
    data class Loaded(val files: List<GpxRouteFile>) : GpxLibraryUiState
    data class Error(val message: String) : GpxLibraryUiState
}

/** A starter example the Builder always opens with instead of a blank (and immediately
 *  parse-erroring) editor — one of each block shape: a plain warmup, a plain steady block, a
 *  repeated interval block, and a plain cooldown. */
private val DEFAULT_BUILDER_TEMPLATE = """
    Warmup
    - 10m 50%

    Active
    - 20m 70%

    4x
    - 5m 90%
    - 3m 50%

    Cooldown
    - 10m 40%
""".trimIndent()

/** The Workout Builder screen's whole editable state: Name/Description are kept apart from
 *  [text] (the DSL body) since they're separate fields in the UI, but all three end up merged
 *  into one [WorkoutDraft] on save/preview (see MainViewModel.currentBuilderDraft). [parseResult]
 *  is re-derived on every [MainViewModel.updateBuilderText] call, not computed lazily, so the
 *  screen's "Parses OK" indicator and Save-button enablement never lag a keystroke behind. */
data class BuilderDraftState(
    val name: String = "",
    val description: String = "",
    val text: String = DEFAULT_BUILDER_TEMPLATE,
    val parseResult: WorkoutDslParser.ParseResult = WorkoutDslParser.parse(DEFAULT_BUILDER_TEMPLATE),
)

/** One CORE sensor reading, time-aligned with [MainViewModel.sampleHistory] via [tSec] — the
 *  Vitals screen's CORE/SKIN/HSI chart traces this the same way the power/HR/cadence chart
 *  traces [com.ergrm.trainer.workout.SamplePoint]. */
data class CoreSamplePoint(
    val tSec: Int,
    val coreTempC: Float?,
    val skinTempC: Float?,
    val heatStrainIndex: Float?,
)

private const val MAX_CORE_TEMP_HISTORY = 6 * 3600

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val intervalsRepository = IntervalsRepository()
    private val calendarCacheRepository = CalendarCacheRepository(application)
    private val libraryRepository = LibraryRepository(application)
    private val gpxLibraryRepository = GpxLibraryRepository(application)
    private val historyRepository = SessionHistoryRepository(application)
    private val backupRepository = BackupRepository(application, settingsRepository, historyRepository)

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(BluetoothManager::class.java))?.adapter

    val trainerConnection = TrainerConnection(application, viewModelScope)
    val heartRateConnection = HeartRateConnection(application)
    val coreTempConnection = CoreTempConnection(application)

    val connectionState = trainerConnection.connectionState
    val hrConnectionState = heartRateConnection.connectionState
    val coreConnectionState = coreTempConnection.connectionState
    val coreTempReading = coreTempConnection.reading

    // A standalone HR sensor's reading takes priority over whatever heart rate the trainer
    // itself might be forwarding (some trainers bridge an ANT+ strap over FTMS). WorkoutExecutor
    // reads from this merged flow (not trainerConnection.liveData directly) so its recorded
    // samples — and therefore the workout chart's HR trace — actually see the standalone
    // sensor's readings instead of always getting null heart rate.
    val liveData = combine(trainerConnection.liveData, heartRateConnection.heartRateBpm) { sample, hrOverride ->
        if (hrOverride != null) sample.copy(heartRateBpm = hrOverride) else sample
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TrainerSample())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val workoutExecutor = WorkoutExecutor(trainerConnection, liveData, settings, viewModelScope)

    val workoutState = workoutExecutor.state
    val sampleHistory = workoutExecutor.sampleHistory

    // One CORE sensor reading per tick, time-aligned with sampleHistory (the Vitals screen's
    // CORE/SKIN/HSI chart needs a history to trace, same as sampleHistory already gives the
    // power/HR/cadence chart) — piggybacked on sampleHistory's own once-per-second emissions
    // instead of a separate ticker, since WorkoutExecutor already owns that clock. Cleared
    // whenever sampleHistory resets (workout stop/discard), same lifecycle as that history.
    private val _coreTempHistory = MutableStateFlow<List<CoreSamplePoint>>(emptyList())
    val coreTempHistory: StateFlow<List<CoreSamplePoint>> = _coreTempHistory.asStateFlow()

    private val _scanResults = MutableStateFlow<List<DiscoveredTrainer>>(emptyList())
    val scanResults: StateFlow<List<DiscoveredTrainer>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hrScanResults = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val hrScanResults: StateFlow<List<DiscoveredDevice>> = _hrScanResults.asStateFlow()

    private val _isHrScanning = MutableStateFlow(false)
    val isHrScanning: StateFlow<Boolean> = _isHrScanning.asStateFlow()

    private var hrScanJob: Job? = null

    private val _coreScanResults = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val coreScanResults: StateFlow<List<DiscoveredDevice>> = _coreScanResults.asStateFlow()

    private val _isCoreScanning = MutableStateFlow(false)
    val isCoreScanning: StateFlow<Boolean> = _isCoreScanning.asStateFlow()

    private var coreScanJob: Job? = null

    private val _workoutLoadState = MutableStateFlow<WorkoutLoadState>(WorkoutLoadState.Idle)
    val workoutLoadState: StateFlow<WorkoutLoadState> = _workoutLoadState.asStateFlow()

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState.asStateFlow()

    private val _intervalsLibraryState = MutableStateFlow<IntervalsLibraryUiState>(IntervalsLibraryUiState.Loading)
    val intervalsLibraryState: StateFlow<IntervalsLibraryUiState> = _intervalsLibraryState.asStateFlow()

    private val _libraryState = MutableStateFlow<LibraryUiState>(LibraryUiState.NoFolder)
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _gpxLibraryState = MutableStateFlow<GpxLibraryUiState>(GpxLibraryUiState.NoFolder)
    val gpxLibraryState: StateFlow<GpxLibraryUiState> = _gpxLibraryState.asStateFlow()

    private val _builderState = MutableStateFlow(BuilderDraftState())
    val builderState: StateFlow<BuilderDraftState> = _builderState.asStateFlow()

    private val _sessionHistory = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val sessionHistory: StateFlow<List<WorkoutSession>> = _sessionHistory.asStateFlow()

    // One-shot events (not state) so the UI can show a snackbar exactly once per occurrence,
    // rather than re-showing it on every recomposition the way a StateFlow would.
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _autoBackupNeeded = MutableSharedFlow<AutoBackupNeededReason>(extraBufferCapacity = 1)
    val autoBackupNeeded: SharedFlow<AutoBackupNeededReason> = _autoBackupNeeded.asSharedFlow()

    private var scanJob: Job? = null

    // Foreground service: keeps the process alive at high priority (with a status notification)
    // for as long as a trainer is connected, so Android doesn't reclaim it — and the BLE
    // connection along with it — while the app is backgrounded mid-ride.
    private var trainerService: TrainerForegroundService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            trainerService = (binder as TrainerForegroundService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trainerService = null
        }
    }

    init {
        viewModelScope.launch {
            val folder = settingsRepository.settings.map { it.libraryFolderUri }.first()
            if (folder != null) refreshLibrary()
        }
        viewModelScope.launch {
            val folder = settingsRepository.settings.map { it.gpxFolderUri }.first()
            if (folder != null) refreshGpxLibrary()
        }
        viewModelScope.launch {
            combine(connectionState, workoutState) { conn, workout -> conn to workout }
                .collect { (conn, workout) ->
                    val title = when (conn) {
                        is TrainerConnectionState.Ready -> "Connected to trainer"
                        is TrainerConnectionState.Failed -> "Connection error"
                        is TrainerConnectionState.Disconnected -> "Trainer disconnected"
                        else -> "Connecting…"
                    }
                    val target = workout.currentTargetWatts.takeIf { workout.isRunning }
                    trainerService?.updateStatus(title, target)
                }
        }
        viewModelScope.launch {
            sampleHistory.collect { samples ->
                val last = samples.lastOrNull()
                _coreTempHistory.value = if (last == null) {
                    emptyList()
                } else {
                    val reading = coreTempReading.value
                    (_coreTempHistory.value + CoreSamplePoint(
                        tSec = last.tSec,
                        coreTempC = reading?.coreTempC,
                        skinTempC = reading?.skinTempC,
                        heatStrainIndex = reading?.heatStrainIndex,
                    )).takeLast(MAX_CORE_TEMP_HISTORY)
                }
            }
        }
        autoReconnect()
        autoLoadTodayWorkout()
        autoLoadCalendarCache()
        BackupReminderScheduler.scheduleIfNeeded(application)
    }

    /** Loads last session's cached Calendar list immediately (so it's there to see even before
     *  any network call completes, or if one never does — the whole point of caching it), then
     *  kicks off a real refresh same as opening the Calendar screen would. Silent on failure,
     *  same as autoLoadTodayWorkout: a 503 or no signal at launch just leaves the cached list (or
     *  nothing, on a first run) rather than surfacing an error nobody asked for yet. */
    private fun autoLoadCalendarCache() {
        viewModelScope.launch {
            val cached = calendarCacheRepository.load()
            if (cached.isNotEmpty()) {
                _calendarState.value = CalendarUiState.Loaded(cached, isStale = true)
            }
            // No live fetch here on purpose: this only loads what's already on disk, so launch
            // never pays for a Calendar refresh (plus prefetchCalendarStructures' own burst of one
            // request per event) that nobody has asked to see yet. The live fetch happens lazily,
            // the first time the Calendar screen is actually opened — see ensureCalendarLoaded().
        }
    }

    private var calendarFetchedThisSession = false

    /** Calendar screen's own launch-time hook: a live fetch (+ structure prefetch) the first time
     *  it's opened in this app session, relying on the cache the rest of the time it's reopened —
     *  a no-op on every open after the first. The screen's own refresh icon still always calls
     *  [fetchCalendarWorkouts] directly for an explicit, on-demand refresh regardless of this. */
    fun ensureCalendarLoaded() {
        if (calendarFetchedThisSession) return
        calendarFetchedThisSession = true
        fetchCalendarWorkouts(silent = true)
    }

    /** Tries "Intervals WOD" once on launch, so the rider doesn't have to tap for it every time —
     *  but silently, unlike [fetchTodayWorkout]'s manual path: no Intervals.icu credentials, no
     *  network, or no workout scheduled today all just leave the screen in its default "No
     *  workout loaded" state rather than surfacing an error nobody asked for. Skipped outright if
     *  a workout is already loaded (e.g. the rider had one from local files loaded on the last
     *  run — auto-reconnect + auto-start could put them mid-ride before this even runs). */
    private fun autoLoadTodayWorkout() {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            if (!s.intervalsConfigured) return@launch
            if (workoutState.value.steps.isNotEmpty()) return@launch
            val result = intervalsRepository.fetchTodayWorkout(s.intervalsApiKey, s.intervalsAthleteId, s.ftpWatts)
            if (result is FetchResult.Success) {
                workoutExecutor.load(result.workout.steps)
                _workoutLoadState.value = WorkoutLoadState.Loaded(
                    result.workout.name,
                    source = WorkoutSource.IntervalsCalendarEvent(result.workout.id),
                )
            }
        }
    }

    /** Silently reconnects the last-used trainer and/or heart rate sensor on app launch, so the
     *  rider doesn't have to re-pick them from a scan every time — [TrainerConnection.connect]
     *  and [HeartRateConnection.connect]'s autoConnect=true lets Android complete the connection
     *  whenever each device is actually powered on and in range, with no explicit wait here.
     *  Skipped (not just delayed) when Bluetooth permission isn't granted yet — that only happens
     *  before the very first manual "Search" tap, which is what grants it. */
    private fun autoReconnect() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasBluetoothConnectPermission()) return

        viewModelScope.launch {
            // DataStore's first read is async, so wait for the real stored settings here rather
            // than reading settings.value, which would still be the empty AppSettings() default
            // at this point in init.
            val s = settingsRepository.settings.first()
            s.lastDeviceAddress?.let { address ->
                runCatching { adapter.getRemoteDevice(address) }.getOrNull()?.let { device ->
                    startTrainerService()
                    trainerConnection.connect(device, autoConnect = true)
                }
            }
            s.lastHrDeviceAddress?.let { address ->
                runCatching { adapter.getRemoteDevice(address) }.getOrNull()?.let { device ->
                    heartRateConnection.connect(device, autoConnect = true)
                }
            }
            s.lastCoreDeviceAddress?.let { address ->
                runCatching { adapter.getRemoteDevice(address) }.getOrNull()?.let { device ->
                    coreTempConnection.connect(device, autoConnect = true)
                }
            }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val app = getApplication<Application>()
        return ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startTrainerService() {
        val app = getApplication<Application>()
        val intent = Intent(app, TrainerForegroundService::class.java)
        ContextCompat.startForegroundService(app, intent)
        if (!serviceBound) {
            app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            serviceBound = true
        }
    }

    private fun stopTrainerService() {
        val app = getApplication<Application>()
        if (serviceBound) {
            app.unbindService(serviceConnection)
            serviceBound = false
        }
        trainerService = null
        app.stopService(Intent(app, TrainerForegroundService::class.java))
    }

    fun startScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        _scanResults.value = emptyList()
        _isScanning.value = true
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            BleScanner(adapter).scan(Ftms.SERVICE_FITNESS_MACHINE)
                .catch { _isScanning.value = false }
                .collect { found ->
                    _scanResults.value = (_scanResults.value + found).distinctBy { it.device.address }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun connectToDevice(discovered: DiscoveredTrainer) {
        stopScan()
        startTrainerService()
        trainerConnection.connect(discovered.device)
        viewModelScope.launch {
            settingsRepository.rememberDevice(discovered.device.address, discovered.name)
        }
    }

    fun disconnect() {
        workoutExecutor.stop()
        viewModelScope.launch { trainerConnection.stopAndDisconnect() }
        stopTrainerService()
    }

    fun startHrScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        _hrScanResults.value = emptyList()
        _isHrScanning.value = true
        hrScanJob?.cancel()
        hrScanJob = viewModelScope.launch {
            BleScanner(adapter).scan(HeartRateProfile.SERVICE_HEART_RATE)
                .catch { _isHrScanning.value = false }
                .collect { found ->
                    _hrScanResults.value = (_hrScanResults.value + found).distinctBy { it.device.address }
                }
        }
    }

    fun stopHrScan() {
        hrScanJob?.cancel()
        hrScanJob = null
        _isHrScanning.value = false
    }

    fun connectHrSensor(discovered: DiscoveredDevice) {
        stopHrScan()
        heartRateConnection.connect(discovered.device)
        viewModelScope.launch {
            settingsRepository.rememberHrDevice(discovered.device.address, discovered.name)
        }
    }

    fun disconnectHrSensor() {
        heartRateConnection.disconnect()
    }

    fun startCoreScan() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        _coreScanResults.value = emptyList()
        _isCoreScanning.value = true
        coreScanJob?.cancel()
        coreScanJob = viewModelScope.launch {
            BleScanner(adapter).scan(CoreTempProfile.SERVICE_CORE_TEMP)
                .catch { _isCoreScanning.value = false }
                .collect { found ->
                    _coreScanResults.value = (_coreScanResults.value + found).distinctBy { it.device.address }
                }
        }
    }

    fun stopCoreScan() {
        coreScanJob?.cancel()
        coreScanJob = null
        _isCoreScanning.value = false
    }

    fun connectCoreSensor(discovered: DiscoveredDevice) {
        stopCoreScan()
        coreTempConnection.connect(discovered.device)
        viewModelScope.launch {
            settingsRepository.rememberCoreDevice(discovered.device.address, discovered.name)
        }
    }

    fun disconnectCoreSensor() {
        coreTempConnection.disconnect()
    }

    fun setDeviceNickname(nickname: String) {
        viewModelScope.launch { settingsRepository.setDeviceNickname(nickname) }
    }

    fun setHrDeviceNickname(nickname: String) {
        viewModelScope.launch { settingsRepository.setHrDeviceNickname(nickname) }
    }

    fun setCoreDeviceNickname(nickname: String) {
        viewModelScope.launch { settingsRepository.setCoreDeviceNickname(nickname) }
    }

    fun saveIntervalsSettings(apiKey: String, athleteId: String, ftpWatts: Int, lthrBpm: Int) {
        viewModelScope.launch {
            settingsRepository.updateIntervalsCredentials(apiKey, athleteId)
            settingsRepository.updateFtp(ftpWatts)
            settingsRepository.updateLthr(lthrBpm)
        }
    }

    fun saveSimSettings(riderWeightKg: Float, accessoriesWeightKg: Float, slopeStepPercent: Float, maxGradePercent: Float) {
        viewModelScope.launch {
            settingsRepository.updateSimSettings(riderWeightKg, accessoriesWeightKg, slopeStepPercent, maxGradePercent)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    /** Explicit, rider-triggered write of the current rider+accessories weight to the trainer
     *  (see TrainerConnection.setWeightKg) — deliberately NOT automatic (e.g. on entering SLO),
     *  since it writes to a Bluetooth characteristic the trainer's own companion app may also
     *  use, and an automatic write could silently overwrite a value set there without the rider
     *  ever asking for that. The snackbar only confirms the write was attempted, not that the
     *  trainer actually reads or uses it — see Ftms.Uds's own doc comment. */
    fun sendWeightToTrainer() {
        viewModelScope.launch {
            trainerConnection.setWeightKg(settings.value.systemWeightKg)
            _snackbarMessages.emit("Weight sent to trainer")
        }
    }

    /** Pulls FTP/LTHR from Intervals.icu for the Settings screen's Sync button — hands whatever
     *  it got back to [onResult] so the screen can fill its (still freely editable) text fields,
     *  rather than saving straight to DataStore itself. Nothing is overwritten here: the rider
     *  still has to tap Save afterward, same as if they'd typed the numbers in by hand. */
    fun syncAthleteSettings(onResult: (ftpWatts: Int?, lthrBpm: Int?) -> Unit) {
        val s = settings.value
        if (!s.intervalsConfigured) {
            viewModelScope.launch { _snackbarMessages.emit("Set your Intervals.icu API key and athlete ID first") }
            return
        }
        viewModelScope.launch {
            when (val result = intervalsRepository.fetchAthleteSettings(s.intervalsApiKey, s.intervalsAthleteId)) {
                is AthleteSettingsResult.Success -> {
                    if (result.ftpWatts == null && result.lthrBpm == null) {
                        _snackbarMessages.emit("Intervals.icu didn't return an FTP or LTHR")
                    } else {
                        onResult(result.ftpWatts, result.lthrBpm)
                        _snackbarMessages.emit("Synced from Intervals.icu")
                    }
                }
                is AthleteSettingsResult.Error -> _snackbarMessages.emit("Sync failed: ${result.message}")
            }
        }
    }

    /** "Intervals WOD" quick action: loads today's first planned bike workout directly, with no
     *  intermediate picker. If several are scheduled today, use the calendar to pick a specific
     *  one instead. */
    fun fetchTodayWorkout() {
        val s = settings.value
        if (!s.intervalsConfigured) {
            _workoutLoadState.value = WorkoutLoadState.Error("Set your Intervals.icu API key and athlete ID first")
            return
        }
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            when (val result = intervalsRepository.fetchTodayWorkout(s.intervalsApiKey, s.intervalsAthleteId, s.ftpWatts)) {
                is FetchResult.Success -> {
                    workoutExecutor.load(result.workout.steps)
                    _workoutLoadState.value = WorkoutLoadState.Loaded(
                        result.workout.name,
                        source = WorkoutSource.IntervalsCalendarEvent(result.workout.id),
                    )
                }
                FetchResult.NoStepsFound -> _workoutLoadState.value = WorkoutLoadState.Empty
                is FetchResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
            }
        }
    }

    /** Loads a GPX track picked via SAF (see AppNav's "GPX route" source) as a SLO route —
     *  workoutExecutor.loadRoute switches control mode to SLO itself, since a loaded route only
     *  makes sense there. */
    fun loadRouteFromGpx(uri: Uri) {
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            val context = getApplication<Application>()
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (t: Exception) {
                null
            }
            if (bytes == null) {
                _workoutLoadState.value = WorkoutLoadState.Error("Couldn't read that file")
                return@launch
            }
            val name = DocumentFile.fromSingleUri(context, uri)?.name
                ?.removeSuffix(".gpx")?.removeSuffix(".GPX")
                ?: "Route"
            val route = GpxParser.parse(bytes.decodeToString(), name)
            if (route == null) {
                _workoutLoadState.value = WorkoutLoadState.Error("That doesn't look like a valid GPX track")
                return@launch
            }
            workoutExecutor.loadRoute(route)
            _workoutLoadState.value = WorkoutLoadState.Loaded(route.name)
        }
    }

    /** A deliberate starting point for an unstructured ride — a single flat 100W/1h00m00s step
     *  the rider then reshapes with the existing intensity (+/-) and +5min extend controls,
     *  rather than SLO's true no-plan-loaded freeform riding (which already falls back to the
     *  same "Free ride" label in history — see SessionRow — whenever a saved session has no
     *  workoutName). No [WorkoutSource]: this plan isn't backed by anything to re-import from. */
    fun loadFreeRide() {
        val steps = listOf(WorkoutStep(durationSec = 3600, startWatts = 100, endWatts = 100, label = "Free ride"))
        workoutExecutor.load(steps)
        _workoutLoadState.value = WorkoutLoadState.Loaded("Free ride")
    }

    /** Refreshes the two-week calendar list (current week + next week) shown in [CalendarScreen].
     *  On success, also persists it via [calendarCacheRepository] for a future offline/outage
     *  fallback. On failure, a cached list already on screen is left as-is (just flagged stale,
     *  with a snackbar noting the refresh itself failed) instead of being replaced by a hard
     *  error — the cached names/dates/durations are still better than nothing during a transient
     *  Intervals.icu outage (e.g. a 503). Only shows the full error screen when there's no cached
     *  list to fall back to. [silent] drops that snackbar — used by the launch-time auto-refresh,
     *  which shouldn't greet the rider with a network complaint before they've even asked to see
     *  the calendar. */
    fun fetchCalendarWorkouts(silent: Boolean = false) {
        val s = settings.value
        if (!s.intervalsConfigured) {
            _calendarState.value = CalendarUiState.Error("Set your Intervals.icu API key and athlete ID first")
            return
        }
        // Keeps a cached/previous list on screen while this refreshes, instead of flashing to a
        // blank spinner every time — only shown when there's nothing to show yet.
        if (_calendarState.value !is CalendarUiState.Loaded) {
            _calendarState.value = CalendarUiState.Loading
        }
        viewModelScope.launch {
            when (val result = intervalsRepository.fetchCalendarWorkouts(s.intervalsApiKey, s.intervalsAthleteId)) {
                is CalendarFetchResult.Success -> {
                    _calendarState.value = CalendarUiState.Loaded(result.workouts, isStale = false)
                    calendarCacheRepository.save(result.workouts)
                    prefetchCalendarStructures(result.workouts, s)
                }
                is CalendarFetchResult.Error -> {
                    val current = _calendarState.value
                    if (current is CalendarUiState.Loaded && current.workouts.isNotEmpty()) {
                        _calendarState.value = current.copy(isStale = true)
                        if (!silent) _snackbarMessages.emit("Couldn't refresh calendar: ${result.message}")
                    } else {
                        _calendarState.value = CalendarUiState.Error(result.message)
                    }
                }
            }
        }
    }

    /** Proactively downloads every listed event's structure after a Calendar list refresh —
     *  not just whichever rows happen to scroll into view for their own preview fetch, or
     *  whichever one is eventually tapped to load — so riding one later with no signal (or
     *  during an Intervals.icu outage) actually works, not just shows its name. Skips events
     *  already cached (a specific event's structure is effectively static once set) and prunes
     *  cached entries for events that dropped out of the list, so the cache tracks the current
     *  ~2-week window instead of growing forever. Runs fully in the background — no loading
     *  state of its own, since nothing on screen is waiting on it. */
    private fun prefetchCalendarStructures(workouts: List<CalendarWorkout>, s: AppSettings) {
        viewModelScope.launch {
            calendarCacheRepository.pruneStructures(workouts.map { it.eventId }.toSet())
            val cached = calendarCacheRepository.loadStructures()
            val missing = workouts.filter { it.eventId !in cached.keys }
            missing.map { workout ->
                async {
                    val result = intervalsRepository.fetchWorkoutByEventId(
                        s.intervalsApiKey, s.intervalsAthleteId, workout.eventId, workout.name, s.ftpWatts,
                    )
                    if (result is FetchResult.Success) {
                        calendarCacheRepository.saveStructure(workout.eventId, result.workout.steps)
                    }
                }
            }.awaitAll()
        }
    }

    /** Loads a specific planned event picked from the calendar list. Falls back to its cached
     *  structure (see [prefetchCalendarStructures]) if the live fetch fails — e.g. no signal or
     *  an Intervals.icu outage — rather than leaving the rider stuck on an error for a workout
     *  that's already sitting on the device. */
    fun loadCalendarWorkout(workout: CalendarWorkout) {
        val s = settings.value
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            when (
                val result = intervalsRepository.fetchWorkoutByEventId(
                    s.intervalsApiKey, s.intervalsAthleteId, workout.eventId, workout.name, s.ftpWatts,
                )
            ) {
                is FetchResult.Success -> {
                    workoutExecutor.load(result.workout.steps)
                    _workoutLoadState.value = WorkoutLoadState.Loaded(
                        result.workout.name,
                        source = WorkoutSource.IntervalsCalendarEvent(workout.eventId),
                    )
                }
                FetchResult.NoStepsFound -> _workoutLoadState.value = WorkoutLoadState.Empty
                is FetchResult.Error -> {
                    val cachedSteps = calendarCacheRepository.loadStructures()[workout.eventId]
                    if (cachedSteps != null) {
                        workoutExecutor.load(cachedSteps)
                        _workoutLoadState.value = WorkoutLoadState.Loaded(
                            workout.name,
                            source = WorkoutSource.IntervalsCalendarEvent(workout.eventId),
                        )
                        _snackbarMessages.emit("Loaded from cache — couldn't reach Intervals.icu (${result.message})")
                    } else {
                        _workoutLoadState.value = WorkoutLoadState.Error(result.message)
                    }
                }
            }
        }
    }

    /** Refreshes the saved-workout list shown in [IntervalsLibraryScreen]. */
    fun fetchIntervalsLibrary() {
        val s = settings.value
        if (!s.intervalsConfigured) {
            _intervalsLibraryState.value = IntervalsLibraryUiState.Error("Set your Intervals.icu API key and athlete ID first")
            return
        }
        _intervalsLibraryState.value = IntervalsLibraryUiState.Loading
        viewModelScope.launch {
            _intervalsLibraryState.value = when (val result = intervalsRepository.fetchLibrary(s.intervalsApiKey, s.intervalsAthleteId)) {
                is LibraryFetchResult.Success -> IntervalsLibraryUiState.Loaded(result.folders)
                is LibraryFetchResult.Error -> IntervalsLibraryUiState.Error(result.message)
            }
        }
    }

    /** Loads a specific saved workout picked from the Intervals.icu library list. Fetches the
     *  workout's canonical record and round-trips it through Intervals.icu's own ZWO converter —
     *  see [IntervalsRepository.loadLibraryWorkout] for why that replaced parsing workout_doc
     *  locally. */
    fun loadIntervalsLibraryWorkout(workout: LibraryWorkout) {
        val s = settings.value
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            when (
                val result = intervalsRepository.loadLibraryWorkout(
                    s.intervalsApiKey, s.intervalsAthleteId, workout, s.ftpWatts,
                )
            ) {
                is FetchResult.Success -> {
                    workoutExecutor.load(result.workout.steps)
                    _workoutLoadState.value = WorkoutLoadState.Loaded(
                        result.workout.name,
                        source = WorkoutSource.IntervalsLibrary(workout.workoutId),
                    )
                }
                FetchResult.NoStepsFound -> _workoutLoadState.value = WorkoutLoadState.Empty
                is FetchResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
            }
        }
    }

    /** Best-effort structure for a Calendar row's mini chart/duration — see
     *  [IntervalsRepository.fetchCalendarWorkoutPreview]; empty on any failure, never an error. */
    suspend fun fetchCalendarWorkoutPreview(eventId: Long): List<WorkoutStep> {
        val s = settings.value
        if (!s.intervalsConfigured) return emptyList()
        val live = intervalsRepository.fetchCalendarWorkoutPreview(s.intervalsApiKey, s.intervalsAthleteId, eventId, s.ftpWatts)
        if (live.isNotEmpty()) return live
        // Live fetch came back empty (no signal, an outage) — fall back to whatever
        // prefetchCalendarStructures already downloaded for this event, if anything.
        return calendarCacheRepository.loadStructures()[eventId].orEmpty()
    }

    /** Same as [fetchCalendarWorkoutPreview], for an Intervals.icu Library row. */
    suspend fun fetchLibraryWorkoutPreview(workout: LibraryWorkout): List<WorkoutStep> {
        val s = settings.value
        if (!s.intervalsConfigured) return emptyList()
        return intervalsRepository.fetchLibraryWorkoutPreview(s.intervalsApiKey, s.intervalsAthleteId, workout, s.ftpWatts)
    }

    /** Same as [fetchCalendarWorkoutPreview], for a local (SAF) library file — no network call. */
    suspend fun previewLocalWorkout(file: LibraryWorkoutFile): List<WorkoutStep> =
        libraryRepository.previewWorkout(file.uri, file.name, settings.value.ftpWatts)

    fun startWorkout() = workoutExecutor.start()
    fun pauseWorkout() = workoutExecutor.pause()
    fun skipStep() = workoutExecutor.skipToNextStep()

    /** Stop: saves a summary of the just-finished ride to the local history (if it was actually
     *  started) before clearing the loaded workout. */
    fun exitWorkout() {
        val s = workoutState.value
        if (s.hasStarted) {
            val samples = sampleHistory.value
            val coreByTSec = coreTempHistory.value.associateBy { it.tSec }
            val watts = samples.map { it.watts }
            val hrs = samples.mapNotNull { it.hrBpm }
            val cadences = samples.mapNotNull { it.cadenceRpm }
            val sessionSamples = samples.map {
                val core = coreByTSec[it.tSec]
                SessionSample(
                    it.tSec, it.watts, it.hrBpm, it.cadenceRpm, it.speedKmh,
                    coreTempC = core?.coreTempC, skinTempC = core?.skinTempC, heatStrainIndex = core?.heatStrainIndex,
                )
            }
            val session = WorkoutSession(
                id = UUID.randomUUID().toString(),
                startEpochMillis = System.currentTimeMillis() - s.totalElapsedSec * 1000L,
                durationSec = s.totalElapsedSec,
                workoutName = (workoutLoadState.value as? WorkoutLoadState.Loaded)?.name,
                avgWatts = if (watts.isNotEmpty()) watts.average().roundToInt() else 0,
                maxWatts = watts.maxOrNull() ?: 0,
                avgHrBpm = if (hrs.isNotEmpty()) hrs.average().roundToInt() else null,
                avgCadenceRpm = if (cadences.isNotEmpty()) cadences.average().roundToInt() else null,
                samples = sessionSamples,
                heatTrainingLoad = HeatTrainingLoad.compute(sessionSamples),
                ftpWatts = settings.value.ftpWatts,
                source = (workoutLoadState.value as? WorkoutLoadState.Loaded)?.source,
            )
            viewModelScope.launch {
                historyRepository.saveSession(session)
                refreshHistory()
                _snackbarMessages.emit("Session saved")
                val minutes = s.totalElapsedSec / 60
                val seconds = s.totalElapsedSec % 60
                AppNotifications.notifyWorkoutSaved(
                    getApplication(),
                    "%d:%02d · avg %d W".format(minutes, seconds, session.avgWatts),
                )
                // Silent on success — a dialog after every single ride would be noise once this
                // is working. Speaks up (an in-app dialog, not just a notification easy to miss
                // in the shade) whenever this ride didn't actually get backed up, whether because
                // no folder is set yet or because writing to it failed — a silent miss here is
                // exactly what led to losing a ride and prompting this feature in the first
                // place, so it must never go unnoticed again.
                val backupFolder = settings.value.backupFolderUri
                val backedUp = backupFolder != null && backupRepository.writeSessionToFolder(Uri.parse(backupFolder), session)
                if (!backedUp) {
                    _autoBackupNeeded.emit(
                        if (backupFolder == null) AutoBackupNeededReason.NOT_CONFIGURED else AutoBackupNeededReason.WRITE_FAILED,
                    )
                }
            }
        }
        workoutExecutor.exit()
    }

    /** Stop without saving: skips exitWorkout()'s history/backup entirely — used by the Stop
     *  dialog's Discard path, after its own "are you sure" confirmation. */
    fun discardWorkout() {
        workoutExecutor.exit()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _sessionHistory.value = historyRepository.listSessions()
        }
    }

    /** Pulls in any session backed up to the auto-backup folder that isn't already in local
     *  history — e.g. after a reinstall, or one written from a different device sharing the same
     *  folder. Called when History opens, and by its manual refresh action. No-op (and silent)
     *  if no folder is configured or nothing new is found; only speaks up when it actually adds
     *  something, since that's the interesting case. */
    fun syncHistoryFromBackupFolder() {
        val folderUri = settings.value.backupFolderUri ?: return
        viewModelScope.launch {
            val added = backupRepository.syncSessionsFromFolder(Uri.parse(folderUri))
            if (added > 0) {
                refreshHistory()
                _snackbarMessages.emit("Synced $added session${if (added == 1) "" else "s"} from backup folder")
            }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            historyRepository.deleteSession(id)
            refreshHistory()
        }
    }

    /** Writes [session] out as a standalone TCX file and hands it to [onReady] for the UI to
     *  share via FileProvider — same pattern as [exportBackup], but for one ride instead of
     *  everything, and in a format (TCX) other platforms can actually import as an activity. */
    fun exportSessionTcx(session: WorkoutSession, onReady: (File) -> Unit) {
        viewModelScope.launch {
            onReady(historyRepository.exportTcx(session))
        }
    }

    /** Writes a fresh settings-only backup file, confirms it with a snackbar, and hands the file
     *  to [onReady] for the UI to share via FileProvider — a suspend write, so this goes through
     *  viewModelScope rather than returning the file directly. Workout history has its own,
     *  automatic backup path — see [writeSessionToBackupFolder] and [syncHistoryFromBackupFolder]. */
    fun exportBackup(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = backupRepository.exportSettingsFile()
            _snackbarMessages.emit("Settings backup saved")
            AppNotifications.notifyBackupExported(getApplication(), file.name)
            onReady(file)
        }
    }

    /** Restores settings from a previously exported backup (picked via SAF) — workout history
     *  isn't part of this file; see [syncHistoryFromBackupFolder] for that. Reads raw bytes (not
     *  text) since a backup file may be gzip-compressed — see [BackupRepository.importFromBytes]. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val bytes = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (t: Exception) {
                null
            }
            if (bytes == null) {
                _snackbarMessages.emit("Couldn't read that file")
                return@launch
            }
            backupRepository.importFromBytes(bytes)
                .onSuccess {
                    _snackbarMessages.emit("Settings restored")
                    AppNotifications.notifySettingsImported(getApplication())
                }
                .onFailure {
                    _snackbarMessages.emit("That file isn't a valid backup")
                }
        }
    }

    fun extendCurrentInterval() = workoutExecutor.extendCurrentStep()
    fun increaseIntensity() = workoutExecutor.increaseIntensity()
    fun decreaseIntensity() = workoutExecutor.decreaseIntensity()
    fun increaseGrade() = workoutExecutor.increaseGrade()
    fun decreaseGrade() = workoutExecutor.decreaseGrade()
    fun toggleControlMode() = workoutExecutor.toggleControlMode()

    /** Called after the user picks a folder via ACTION_OPEN_DOCUMENT_TREE. */
    fun onLibraryFolderPicked(uri: Uri, displayName: String) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        viewModelScope.launch {
            settingsRepository.setLibraryFolder(uri.toString(), displayName)
            refreshLibrary()
        }
    }

    /** Same as [onLibraryFolderPicked], but also takes the write permission — every saved
     *  workout writes a fresh backup into this folder from then on, silently. */
    fun onBackupFolderPicked(uri: Uri, displayName: String) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        viewModelScope.launch {
            settingsRepository.setBackupFolder(uri.toString(), displayName)
        }
    }

    fun refreshLibrary() {
        val folderUriString = settings.value.libraryFolderUri
        if (folderUriString == null) {
            _libraryState.value = LibraryUiState.NoFolder
            return
        }
        _libraryState.value = LibraryUiState.Loading
        viewModelScope.launch {
            try {
                val folders = libraryRepository.listFolders(Uri.parse(folderUriString))
                _libraryState.value = LibraryUiState.Loaded(folders)
            } catch (t: Exception) {
                _libraryState.value = LibraryUiState.Error(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun importLibraryWorkout(file: LibraryWorkoutFile) {
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            when (val result = libraryRepository.importWorkout(file.uri, file.name, settings.value.ftpWatts)) {
                is LibraryImportResult.Success -> {
                    workoutExecutor.load(result.steps)
                    _workoutLoadState.value = WorkoutLoadState.Loaded(
                        file.name.substringBeforeLast(".", file.name),
                        source = WorkoutSource.LocalFile(file.uri.toString()),
                    )
                }
                is LibraryImportResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
            }
        }
    }

    fun updateBuilderName(name: String) {
        _builderState.value = _builderState.value.copy(name = name)
    }

    fun updateBuilderDescription(description: String) {
        _builderState.value = _builderState.value.copy(description = description)
    }

    fun updateBuilderText(text: String) {
        _builderState.value = _builderState.value.copy(text = text, parseResult = WorkoutDslParser.parse(text))
    }

    /** The draft as it stands right now, with the Name/Description fields (edited separately from
     *  the DSL body) merged in — null while the DSL body itself doesn't parse. */
    fun currentBuilderDraft(): WorkoutDraft? {
        val state = _builderState.value
        val result = state.parseResult as? WorkoutDslParser.ParseResult.Success ?: return null
        return result.draft.copy(name = state.name, description = state.description)
    }

    /** Flattened steps for the builder's live preview card (duration/load/NP/mini chart) — same
     *  [previewStats] extension the Library/Calendar preview tiles already use, just fed from the
     *  draft being typed right now instead of a saved file. Null while it doesn't parse. */
    fun builderPreviewSteps(): List<WorkoutStep>? =
        currentBuilderDraft()?.let { WorkoutDslParser.flatten(it, settings.value.ftpWatts) }

    /** Import for the Workout Builder (distinct from [importLibraryWorkout], which loads a file
     *  straight to the trainer): a .zwo goes through [StructuredZwoReader] so a repeat block comes
     *  back as one editable block instead of flattened; .erg/.mrc has no repeat structure to lose,
     *  so each [WorkoutStep] from the existing [ErgParser] just becomes its own one-step block. */
    fun importBuilderFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            val content = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.reader().readText() }
            } catch (t: Exception) {
                null
            }
            if (content == null) {
                _snackbarMessages.emit("Couldn't read that file")
                return@launch
            }
            val ftpWatts = settings.value.ftpWatts
            val fallbackName = fileName.substringBeforeLast(".", fileName)
            val draft = if (fileName.endsWith(".zwo", ignoreCase = true)) {
                StructuredZwoReader.read(content, ftpWatts, fallbackName)
            } else {
                val steps = ErgParser.parse(content, ftpWatts)
                if (steps.isEmpty()) {
                    _snackbarMessages.emit("No steps found in the file")
                    return@launch
                }
                WorkoutDraft(
                    name = fallbackName,
                    description = "",
                    blocks = steps.map { step ->
                        WorkoutBlock("", 1, listOf(DraftStep(step.label, step.durationSec, PowerTarget.Watts(step.startWatts, step.endWatts))))
                    },
                )
            }
            _builderState.value = BuilderDraftState(
                name = draft.name,
                description = draft.description,
                text = WorkoutDslSerializer.serialize(draft),
                parseResult = WorkoutDslParser.ParseResult.Success(draft),
            )
        }
    }

    /** Save As for the Workout Builder: always writes a new file at [uri] (from
     *  ActivityResultContracts.CreateDocument — see WorkoutBuilderScreen), never overwrites the
     *  Workout Library folder's existing read-only tree permission. */
    fun exportBuilderDraft(uri: Uri) {
        val draft = currentBuilderDraft() ?: return
        viewModelScope.launch {
            try {
                val xml = ZwoWriter.write(draft, settings.value.ftpWatts)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(xml.toByteArray()) }
                    ?: throw IllegalStateException("Couldn't open that location for writing")
                _snackbarMessages.emit("Saved \"${draft.name}.zwo\"")
                refreshLibrary()
            } catch (t: Exception) {
                _snackbarMessages.emit("Save failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    /** History's own "re-import" action, in two tiers. First choice: [source], the exact workout
     *  this session was originally loaded from — recorded on sessions saved from here on (see
     *  WorkoutLoadState.Loaded/exitWorkout), looked up directly by id, so renames don't break it.
     *  Second choice, picked automatically (not asked of the rider) whenever the first either
     *  isn't recorded (a session saved before this existed) or no longer resolves (the workout was
     *  since deleted/moved): the original best-effort exact-name search across whichever libraries
     *  are configured — Intervals.icu first, then the local folder. Either tier loads the FIRST
     *  match exactly as it stands there now (any mid-ride +5min extensions or skips the original
     *  ride took are not reproduced, by design: this reloads the plan, not the ride). [onNotFound]
     *  fires with a human-readable reason when nothing resolves at all, instead of silently doing
     *  nothing; a successful match drives [workoutLoadState] the same way every other import path
     *  already does. */
    fun importOriginalWorkout(workoutName: String, source: WorkoutSource?, onNotFound: (String) -> Unit) {
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            val s = settings.value

            when (source) {
                is WorkoutSource.IntervalsLibrary -> if (s.intervalsConfigured) {
                    val result = intervalsRepository.loadLibraryWorkout(
                        s.intervalsApiKey, s.intervalsAthleteId, LibraryWorkout(source.workoutId, workoutName), s.ftpWatts,
                    )
                    if (result is FetchResult.Success) {
                        workoutExecutor.load(result.workout.steps)
                        _workoutLoadState.value = WorkoutLoadState.Loaded(result.workout.name, source)
                        return@launch
                    }
                }
                is WorkoutSource.IntervalsCalendarEvent -> if (s.intervalsConfigured) {
                    val result = intervalsRepository.fetchWorkoutByEventId(
                        s.intervalsApiKey, s.intervalsAthleteId, source.eventId, workoutName, s.ftpWatts,
                    )
                    if (result is FetchResult.Success) {
                        workoutExecutor.load(result.workout.steps)
                        _workoutLoadState.value = WorkoutLoadState.Loaded(result.workout.name, source)
                        return@launch
                    }
                }
                is WorkoutSource.LocalFile -> {
                    val result = try {
                        libraryRepository.importWorkout(Uri.parse(source.uri), workoutName, s.ftpWatts)
                    } catch (_: Exception) {
                        null
                    }
                    if (result is LibraryImportResult.Success) {
                        workoutExecutor.load(result.steps)
                        _workoutLoadState.value = WorkoutLoadState.Loaded(workoutName, source)
                        return@launch
                    }
                }
                null -> {}
            }

            // Fell through: no recorded source, or it no longer resolves — same best-effort
            // exact-name search this action used before sources were tracked.
            if (s.intervalsConfigured) {
                val result = intervalsRepository.fetchLibrary(s.intervalsApiKey, s.intervalsAthleteId)
                if (result is LibraryFetchResult.Success) {
                    val match = result.folders.flatMap { it.workouts }.firstOrNull { it.name == workoutName }
                    if (match != null) {
                        loadIntervalsLibraryWorkout(match)
                        return@launch
                    }
                }
            }
            val folderUriString = s.libraryFolderUri
            if (folderUriString != null) {
                val match = try {
                    libraryRepository.listFolders(Uri.parse(folderUriString))
                        .flatMap { it.files }
                        .firstOrNull { it.name.substringBeforeLast(".", it.name) == workoutName }
                } catch (_: Exception) {
                    null
                }
                if (match != null) {
                    importLibraryWorkout(match)
                    return@launch
                }
            }
            _workoutLoadState.value = WorkoutLoadState.Idle
            onNotFound(
                if (!s.intervalsConfigured && folderUriString == null) {
                    "No library is configured — set up Intervals.icu or a workout folder in Settings to re-import \"$workoutName\"."
                } else {
                    "\"$workoutName\" wasn't found in your configured librar${if (s.intervalsConfigured && folderUriString != null) "ies" else "y"}."
                },
            )
        }
    }

    /** Same folder-picking pattern as [onLibraryFolderPicked], for a separate GPX routes folder —
     *  kept apart from the workout-plan library folder since the two file types are naturally
     *  organized separately (e.g. two different synced Drive folders). */
    fun onGpxFolderPicked(uri: Uri, displayName: String) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        viewModelScope.launch {
            settingsRepository.setGpxFolder(uri.toString(), displayName)
            refreshGpxLibrary()
        }
    }

    fun refreshGpxLibrary() {
        val folderUriString = settings.value.gpxFolderUri
        if (folderUriString == null) {
            _gpxLibraryState.value = GpxLibraryUiState.NoFolder
            return
        }
        _gpxLibraryState.value = GpxLibraryUiState.Loading
        viewModelScope.launch {
            try {
                val files = gpxLibraryRepository.listRoutes(Uri.parse(folderUriString))
                _gpxLibraryState.value = GpxLibraryUiState.Loaded(files)
            } catch (t: Exception) {
                _gpxLibraryState.value = GpxLibraryUiState.Error(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun importGpxFromLibrary(file: GpxRouteFile) {
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            when (val result = gpxLibraryRepository.importRoute(file.uri, file.name)) {
                is GpxImportResult.Success -> {
                    workoutExecutor.loadRoute(result.route)
                    _workoutLoadState.value = WorkoutLoadState.Loaded(result.route.name)
                }
                is GpxImportResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
            }
        }
    }

    override fun onCleared() {
        stopScan()
        stopHrScan()
        stopCoreScan()
        trainerConnection.disconnect()
        heartRateConnection.disconnect()
        coreTempConnection.disconnect()
        stopTrainerService()
        super.onCleared()
    }
}

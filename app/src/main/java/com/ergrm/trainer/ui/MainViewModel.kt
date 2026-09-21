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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ergrm.trainer.ble.BleScanner
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
import com.ergrm.trainer.history.SessionHistoryRepository
import com.ergrm.trainer.history.SessionSample
import com.ergrm.trainer.history.WorkoutSession
import com.ergrm.trainer.intervals.AthleteSettingsResult
import com.ergrm.trainer.intervals.CalendarFetchResult
import com.ergrm.trainer.intervals.CalendarWorkout
import com.ergrm.trainer.intervals.FetchResult
import com.ergrm.trainer.intervals.IntervalsRepository
import com.ergrm.trainer.intervals.LibraryFetchResult
import com.ergrm.trainer.intervals.LibraryFolderGroup
import com.ergrm.trainer.intervals.LibraryWorkout
import com.ergrm.trainer.library.LibraryImportResult
import com.ergrm.trainer.library.LibraryRepository
import com.ergrm.trainer.library.LibraryWorkoutFile
import com.ergrm.trainer.notify.AppNotifications
import com.ergrm.trainer.service.TrainerForegroundService
import com.ergrm.trainer.workout.WorkoutExecutor
import com.ergrm.trainer.workout.WorkoutStep
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

sealed interface WorkoutLoadState {
    data object Idle : WorkoutLoadState
    data object Loading : WorkoutLoadState
    data class Loaded(val name: String) : WorkoutLoadState
    data object Empty : WorkoutLoadState
    data class Error(val message: String) : WorkoutLoadState
}

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Loaded(val workouts: List<CalendarWorkout>) : CalendarUiState
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
    data class Loaded(val files: List<LibraryWorkoutFile>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val intervalsRepository = IntervalsRepository()
    private val libraryRepository = LibraryRepository(application)
    private val historyRepository = SessionHistoryRepository(application)
    private val backupRepository = BackupRepository(application, settingsRepository, historyRepository)

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(BluetoothManager::class.java))?.adapter

    val trainerConnection = TrainerConnection(application, viewModelScope)
    val heartRateConnection = HeartRateConnection(application)

    val connectionState = trainerConnection.connectionState
    val hrConnectionState = heartRateConnection.connectionState

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

    private val _scanResults = MutableStateFlow<List<DiscoveredTrainer>>(emptyList())
    val scanResults: StateFlow<List<DiscoveredTrainer>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hrScanResults = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val hrScanResults: StateFlow<List<DiscoveredDevice>> = _hrScanResults.asStateFlow()

    private val _isHrScanning = MutableStateFlow(false)
    val isHrScanning: StateFlow<Boolean> = _isHrScanning.asStateFlow()

    private var hrScanJob: Job? = null

    private val _workoutLoadState = MutableStateFlow<WorkoutLoadState>(WorkoutLoadState.Idle)
    val workoutLoadState: StateFlow<WorkoutLoadState> = _workoutLoadState.asStateFlow()

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState.asStateFlow()

    private val _intervalsLibraryState = MutableStateFlow<IntervalsLibraryUiState>(IntervalsLibraryUiState.Loading)
    val intervalsLibraryState: StateFlow<IntervalsLibraryUiState> = _intervalsLibraryState.asStateFlow()

    private val _libraryState = MutableStateFlow<LibraryUiState>(LibraryUiState.NoFolder)
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _sessionHistory = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val sessionHistory: StateFlow<List<WorkoutSession>> = _sessionHistory.asStateFlow()

    // One-shot events (not state) so the UI can show a snackbar exactly once per occurrence,
    // rather than re-showing it on every recomposition the way a StateFlow would.
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

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
        autoReconnect()
        BackupReminderScheduler.scheduleIfNeeded(application)
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

    fun saveIntervalsSettings(apiKey: String, athleteId: String, ftpWatts: Int, lthrBpm: Int) {
        viewModelScope.launch {
            settingsRepository.updateIntervalsCredentials(apiKey, athleteId)
            settingsRepository.updateFtp(ftpWatts)
            settingsRepository.updateLthr(lthrBpm)
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
                    _workoutLoadState.value = WorkoutLoadState.Loaded(result.workout.name)
                }
                FetchResult.NoStepsFound -> _workoutLoadState.value = WorkoutLoadState.Empty
                is FetchResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
            }
        }
    }

    /** Refreshes the two-week calendar list (current week + next week) shown in [CalendarScreen]. */
    fun fetchCalendarWorkouts() {
        val s = settings.value
        if (!s.intervalsConfigured) {
            _calendarState.value = CalendarUiState.Error("Set your Intervals.icu API key and athlete ID first")
            return
        }
        _calendarState.value = CalendarUiState.Loading
        viewModelScope.launch {
            _calendarState.value = when (val result = intervalsRepository.fetchCalendarWorkouts(s.intervalsApiKey, s.intervalsAthleteId)) {
                is CalendarFetchResult.Success -> CalendarUiState.Loaded(result.workouts)
                is CalendarFetchResult.Error -> CalendarUiState.Error(result.message)
            }
        }
    }

    /** Loads a specific planned event picked from the calendar list. */
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
                    _workoutLoadState.value = WorkoutLoadState.Loaded(result.workout.name)
                }
                FetchResult.NoStepsFound -> _workoutLoadState.value = WorkoutLoadState.Empty
                is FetchResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
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
                    _workoutLoadState.value = WorkoutLoadState.Loaded(result.workout.name)
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
        return intervalsRepository.fetchCalendarWorkoutPreview(s.intervalsApiKey, s.intervalsAthleteId, eventId, s.ftpWatts)
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
            val watts = samples.map { it.watts }
            val hrs = samples.mapNotNull { it.hrBpm }
            val cadences = samples.mapNotNull { it.cadenceRpm }
            val session = WorkoutSession(
                id = UUID.randomUUID().toString(),
                startEpochMillis = System.currentTimeMillis() - s.totalElapsedSec * 1000L,
                durationSec = s.totalElapsedSec,
                workoutName = (workoutLoadState.value as? WorkoutLoadState.Loaded)?.name,
                avgWatts = if (watts.isNotEmpty()) watts.average().roundToInt() else 0,
                maxWatts = watts.maxOrNull() ?: 0,
                avgHrBpm = if (hrs.isNotEmpty()) hrs.average().roundToInt() else null,
                avgCadenceRpm = if (cadences.isNotEmpty()) cadences.average().roundToInt() else null,
                samples = samples.map { SessionSample(it.tSec, it.watts, it.hrBpm, it.cadenceRpm, it.speedKmh) },
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
            }
        }
        workoutExecutor.exit()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _sessionHistory.value = historyRepository.listSessions()
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            historyRepository.deleteSession(id)
            refreshHistory()
        }
    }

    /** Writes a fresh backup file (settings + history), confirms it with a snackbar exactly like
     *  a saved session, and hands the file to [onReady] for the UI to share via FileProvider — a
     *  suspend write, so this goes through viewModelScope rather than returning the file directly. */
    fun exportBackup(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = backupRepository.exportFile()
            _snackbarMessages.emit("Backup saved")
            AppNotifications.notifyBackupExported(getApplication(), file.name)
            onReady(file)
        }
    }

    /** Restores settings and merges history from a previously exported backup (picked via SAF).
     *  Safe to import the same file more than once — sessions already present (by id) are
     *  skipped rather than duplicated, and settings are simply overwritten with the backup's.
     *  Reads raw bytes (not text) since a backup file may be gzip-compressed — see
     *  [BackupRepository.importFromBytes]. */
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
                .onSuccess { result ->
                    refreshHistory()
                    val count = result.sessionsAdded
                    _snackbarMessages.emit(
                        "Backup applied — added $count new session${if (count == 1) "" else "s"}",
                    )
                    val skipped = result.backup.history.size - count
                    AppNotifications.notifyBackupImported(getApplication(), count, skipped)
                }
                .onFailure {
                    _snackbarMessages.emit("That file isn't a valid backup")
                }
        }
    }

    fun extendCurrentInterval() = workoutExecutor.extendCurrentStep()
    fun increaseIntensity() = workoutExecutor.increaseIntensity()
    fun decreaseIntensity() = workoutExecutor.decreaseIntensity()
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

    fun refreshLibrary() {
        val folderUriString = settings.value.libraryFolderUri
        if (folderUriString == null) {
            _libraryState.value = LibraryUiState.NoFolder
            return
        }
        _libraryState.value = LibraryUiState.Loading
        viewModelScope.launch {
            try {
                val files = libraryRepository.listWorkouts(Uri.parse(folderUriString))
                _libraryState.value = LibraryUiState.Loaded(files)
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
                    )
                }
                is LibraryImportResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
            }
        }
    }

    override fun onCleared() {
        stopScan()
        stopHrScan()
        trainerConnection.disconnect()
        heartRateConnection.disconnect()
        stopTrainerService()
        super.onCleared()
    }
}

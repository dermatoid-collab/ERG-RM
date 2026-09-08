package com.ergrm.trainer.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ergrm.trainer.ble.BleScanner
import com.ergrm.trainer.ble.DiscoveredTrainer
import com.ergrm.trainer.ble.TrainerConnection
import com.ergrm.trainer.ble.TrainerConnectionState
import com.ergrm.trainer.data.AppSettings
import com.ergrm.trainer.data.SettingsRepository
import com.ergrm.trainer.intervals.FetchResult
import com.ergrm.trainer.intervals.IntervalsRepository
import com.ergrm.trainer.library.LibraryImportResult
import com.ergrm.trainer.library.LibraryRepository
import com.ergrm.trainer.library.LibraryWorkoutFile
import com.ergrm.trainer.service.TrainerForegroundService
import com.ergrm.trainer.workout.WorkoutExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

sealed interface WorkoutLoadState {
    data object Idle : WorkoutLoadState
    data object Loading : WorkoutLoadState
    data class Loaded(val name: String) : WorkoutLoadState
    data object Empty : WorkoutLoadState
    data class Error(val message: String) : WorkoutLoadState
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

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(BluetoothManager::class.java))?.adapter

    val trainerConnection = TrainerConnection(application, viewModelScope)
    val workoutExecutor = WorkoutExecutor(trainerConnection, viewModelScope)

    val connectionState = trainerConnection.connectionState
    val liveData = trainerConnection.liveData
    val workoutState = workoutExecutor.state
    val sampleHistory = workoutExecutor.sampleHistory

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _scanResults = MutableStateFlow<List<DiscoveredTrainer>>(emptyList())
    val scanResults: StateFlow<List<DiscoveredTrainer>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _workoutLoadState = MutableStateFlow<WorkoutLoadState>(WorkoutLoadState.Idle)
    val workoutLoadState: StateFlow<WorkoutLoadState> = _workoutLoadState.asStateFlow()

    private val _libraryState = MutableStateFlow<LibraryUiState>(LibraryUiState.NoFolder)
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

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
                        is TrainerConnectionState.Ready -> "Connesso al trainer"
                        is TrainerConnectionState.Failed -> "Errore di connessione"
                        is TrainerConnectionState.Disconnected -> "Trainer disconnesso"
                        else -> "Connessione in corso…"
                    }
                    val target = workout.currentTargetWatts.takeIf { workout.isRunning }
                    trainerService?.updateStatus(title, target)
                }
        }
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
            BleScanner(adapter).scan()
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
        trainerConnection.disconnect()
        stopTrainerService()
    }

    fun saveIntervalsSettings(apiKey: String, athleteId: String, ftpWatts: Int) {
        viewModelScope.launch {
            settingsRepository.updateIntervalsCredentials(apiKey, athleteId)
            settingsRepository.updateFtp(ftpWatts)
        }
    }

    fun fetchTodayWorkout() {
        val s = settings.value
        if (!s.intervalsConfigured) {
            _workoutLoadState.value = WorkoutLoadState.Error("Configura API key e athlete ID di Intervals.icu")
            return
        }
        _workoutLoadState.value = WorkoutLoadState.Loading
        viewModelScope.launch {
            when (val result = intervalsRepository.fetchTodayWorkout(s.intervalsApiKey, s.intervalsAthleteId, s.ftpWatts)) {
                is FetchResult.Success -> {
                    workoutExecutor.load(result.workout.steps)
                    _workoutLoadState.value = WorkoutLoadState.Loaded(result.workout.name)
                }
                FetchResult.NoWorkoutToday -> _workoutLoadState.value = WorkoutLoadState.Empty
                is FetchResult.Error -> _workoutLoadState.value = WorkoutLoadState.Error(result.message)
            }
        }
    }

    fun startWorkout() = workoutExecutor.start()
    fun pauseWorkout() = workoutExecutor.pause()
    fun skipStep() = workoutExecutor.skipToNextStep()
    fun exitWorkout() = workoutExecutor.exit()
    fun extendCurrentInterval() = workoutExecutor.extendCurrentStep()
    fun increaseIntensity() = workoutExecutor.increaseIntensity()
    fun decreaseIntensity() = workoutExecutor.decreaseIntensity()

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
        trainerConnection.disconnect()
        stopTrainerService()
        super.onCleared()
    }
}

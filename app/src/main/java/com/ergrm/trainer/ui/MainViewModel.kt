package com.ergrm.trainer.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ergrm.trainer.ble.BleScanner
import com.ergrm.trainer.ble.DiscoveredTrainer
import com.ergrm.trainer.ble.TrainerConnection
import com.ergrm.trainer.data.AppSettings
import com.ergrm.trainer.data.SettingsRepository
import com.ergrm.trainer.intervals.FetchResult
import com.ergrm.trainer.intervals.IntervalsRepository
import com.ergrm.trainer.workout.WorkoutExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val intervalsRepository = IntervalsRepository()

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(BluetoothManager::class.java))?.adapter

    val trainerConnection = TrainerConnection(application, viewModelScope)
    val workoutExecutor = WorkoutExecutor(trainerConnection, viewModelScope)

    val connectionState = trainerConnection.connectionState
    val liveData = trainerConnection.liveData
    val workoutState = workoutExecutor.state

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _scanResults = MutableStateFlow<List<DiscoveredTrainer>>(emptyList())
    val scanResults: StateFlow<List<DiscoveredTrainer>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _workoutLoadState = MutableStateFlow<WorkoutLoadState>(WorkoutLoadState.Idle)
    val workoutLoadState: StateFlow<WorkoutLoadState> = _workoutLoadState.asStateFlow()

    private var scanJob: Job? = null

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
        trainerConnection.connect(discovered.device)
        viewModelScope.launch {
            settingsRepository.rememberDevice(discovered.device.address, discovered.name)
        }
    }

    fun disconnect() {
        workoutExecutor.stop()
        trainerConnection.disconnect()
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

    override fun onCleared() {
        stopScan()
        trainerConnection.disconnect()
        super.onCleared()
    }
}

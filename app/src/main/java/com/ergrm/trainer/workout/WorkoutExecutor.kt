package com.ergrm.trainer.workout

import com.ergrm.trainer.ble.TrainerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WorkoutRunState(
    val steps: List<WorkoutStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val elapsedInStepSec: Int = 0,
    val totalElapsedSec: Int = 0,
    val totalDurationSec: Int = 0,
    val currentTargetWatts: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
) {
    val currentStep: WorkoutStep? get() = steps.getOrNull(currentStepIndex)
    val remainingInStepSec: Int get() = (currentStep?.durationSec ?: 0) - elapsedInStepSec
    val totalRemainingSec: Int get() = (totalDurationSec - totalElapsedSec).coerceAtLeast(0)
}

/**
 * Drives a structured workout in ERG mode: ticks once per second, computes the target
 * power for the current point in the workout (interpolating across ramps) and pushes it
 * to the connected trainer whenever it changes.
 */
class WorkoutExecutor(
    private val trainer: TrainerConnection,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WorkoutRunState())
    val state: StateFlow<WorkoutRunState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var lastSentWatts: Int? = null

    fun load(steps: List<WorkoutStep>) {
        stop()
        _state.value = WorkoutRunState(
            steps = steps,
            totalDurationSec = steps.sumOf { it.durationSec },
        )
    }

    fun start() {
        if (_state.value.steps.isEmpty() || _state.value.isRunning) return
        _state.value = _state.value.copy(isRunning = true, isFinished = false)
        pushTargetForCurrentStep()
        tickerJob?.cancel()
        tickerJob = scope.launch {
            scope.launch { trainer.startOrResume() }
            while (isActive && _state.value.isRunning && !_state.value.isFinished) {
                tick()
                delay(1000)
            }
        }
    }

    fun pause() {
        _state.value = _state.value.copy(isRunning = false)
        scope.launch { trainer.stop() }
    }

    fun skipToNextStep() {
        val s = _state.value
        val nextIndex = s.currentStepIndex + 1
        if (nextIndex >= s.steps.size) {
            finish()
            return
        }
        val elapsedBeforeStep = s.steps.take(nextIndex).sumOf { it.durationSec }
        _state.value = s.copy(
            currentStepIndex = nextIndex,
            elapsedInStepSec = 0,
            totalElapsedSec = elapsedBeforeStep,
        )
        pushTargetForCurrentStep()
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        lastSentWatts = null
        _state.value = _state.value.copy(isRunning = false)
    }

    private fun tick() {
        val s = _state.value
        val step = s.currentStep ?: return finish()

        val newElapsedInStep = s.elapsedInStepSec + 1
        if (newElapsedInStep >= step.durationSec) {
            val nextIndex = s.currentStepIndex + 1
            if (nextIndex >= s.steps.size) {
                _state.value = s.copy(
                    totalElapsedSec = s.totalElapsedSec + 1,
                    elapsedInStepSec = step.durationSec,
                )
                finish()
                return
            }
            _state.value = s.copy(
                currentStepIndex = nextIndex,
                elapsedInStepSec = 0,
                totalElapsedSec = s.totalElapsedSec + 1,
            )
        } else {
            _state.value = s.copy(
                elapsedInStepSec = newElapsedInStep,
                totalElapsedSec = s.totalElapsedSec + 1,
            )
        }
        pushTargetForCurrentStep()
    }

    private fun pushTargetForCurrentStep() {
        val s = _state.value
        val step = s.currentStep ?: return
        val target = step.targetWattsAt(s.elapsedInStepSec)
        _state.value = _state.value.copy(currentTargetWatts = target)
        if (target != lastSentWatts) {
            lastSentWatts = target
            scope.launch { trainer.setTargetPowerWatts(target) }
        }
    }

    private fun finish() {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false, isFinished = true)
        scope.launch { trainer.stop() }
    }
}

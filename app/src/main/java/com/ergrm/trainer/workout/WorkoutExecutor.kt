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
    val hasStarted: Boolean = false,
) {
    val currentStep: WorkoutStep? get() = steps.getOrNull(currentStepIndex)
    val nextStep: WorkoutStep? get() = steps.getOrNull(currentStepIndex + 1)
    val remainingInStepSec: Int get() = (currentStep?.durationSec ?: 0) - elapsedInStepSec
    val totalRemainingSec: Int get() = (totalDurationSec - totalElapsedSec).coerceAtLeast(0)
}

private const val AUTO_EXTEND_SEC = 300
private const val AUTO_EXTEND_LABEL = "Prolungamento"

/**
 * Drives a structured workout in ERG mode: ticks once per second, computes the target
 * power for the current point in the workout (interpolating across ramps) and pushes it
 * to the connected trainer whenever it changes.
 *
 * Once the last planned step ends, the workout does not stop on its own: it keeps holding
 * the last target power in further 5-minute blocks, indefinitely, until the rider pauses
 * and taps Stop (see [exit]) or [pause]s and never resumes.
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
        _state.value = _state.value.copy(isRunning = true, hasStarted = true)
        pushTargetForCurrentStep()
        tickerJob?.cancel()
        tickerJob = scope.launch {
            scope.launch { trainer.startOrResume() }
            while (isActive && _state.value.isRunning) {
                tick()
                delay(1000)
            }
        }
    }

    fun pause() {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false)
        scope.launch { trainer.stop() }
    }

    /** Fully ends the current workout session: clears the loaded plan, leaves the trainer connected. */
    fun exit() {
        tickerJob?.cancel()
        tickerJob = null
        lastSentWatts = null
        _state.value = WorkoutRunState()
        scope.launch { trainer.stop() }
    }

    /** Adds [extraSec] to the currently running interval, without disturbing its progress so far. */
    fun extendCurrentStep(extraSec: Int = AUTO_EXTEND_SEC) {
        val s = _state.value
        val index = s.currentStepIndex
        val step = s.steps.getOrNull(index) ?: return
        val extended = step.copy(durationSec = step.durationSec + extraSec)
        val newSteps = s.steps.toMutableList().also { it[index] = extended }
        _state.value = s.copy(steps = newSteps, totalDurationSec = s.totalDurationSec + extraSec)
    }

    fun skipToNextStep() {
        val s = _state.value
        val current = s.currentStep ?: return
        val nextIndex = s.currentStepIndex + 1
        if (nextIndex >= s.steps.size) {
            appendExtensionAndAdvance(s, current.endWatts, s.totalDurationSec)
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
        val step = s.currentStep ?: return

        val newElapsedInStep = s.elapsedInStepSec + 1
        if (newElapsedInStep >= step.durationSec) {
            val nextIndex = s.currentStepIndex + 1
            if (nextIndex >= s.steps.size) {
                appendExtensionAndAdvance(s, step.endWatts, s.totalElapsedSec + 1)
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

    /** Appends another [AUTO_EXTEND_SEC] block at [holdWatts] and moves into it — repeats forever. */
    private fun appendExtensionAndAdvance(s: WorkoutRunState, holdWatts: Int, totalElapsedSec: Int) {
        val extension = WorkoutStep(AUTO_EXTEND_SEC, holdWatts, holdWatts, AUTO_EXTEND_LABEL)
        _state.value = s.copy(
            steps = s.steps + extension,
            currentStepIndex = s.steps.size,
            elapsedInStepSec = 0,
            totalElapsedSec = totalElapsedSec,
            totalDurationSec = s.totalDurationSec + AUTO_EXTEND_SEC,
        )
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
}

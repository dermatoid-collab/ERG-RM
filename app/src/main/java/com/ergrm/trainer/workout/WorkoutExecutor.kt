package com.ergrm.trainer.workout

import com.ergrm.trainer.ble.TrainerConnection
import com.ergrm.trainer.ble.TrainerSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class WorkoutRunState(
    val steps: List<WorkoutStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val elapsedInStepSec: Int = 0,
    val totalElapsedSec: Int = 0,
    val totalDurationSec: Int = 0,
    val currentTargetWatts: Int = 0,
    val isRunning: Boolean = false,
    val hasStarted: Boolean = false,
    val intensityPercent: Int = 100,
    /** True when the last pause was triggered automatically (rider stopped pedaling), as
     *  opposed to a manual tap — only an auto-pause resumes on its own when pedaling resumes. */
    val autoPaused: Boolean = false,
) {
    val currentStep: WorkoutStep? get() = steps.getOrNull(currentStepIndex)
    val nextStep: WorkoutStep? get() = steps.getOrNull(currentStepIndex + 1)
    val remainingInStepSec: Int get() = (currentStep?.durationSec ?: 0) - elapsedInStepSec
    val totalRemainingSec: Int get() = (totalDurationSec - totalElapsedSec).coerceAtLeast(0)
}

/** One second of recorded live data during a running workout, used to trace power/HR/cadence on the chart. */
data class SamplePoint(
    val tSec: Int,
    val watts: Int,
    val hrBpm: Int?,
    val cadenceRpm: Int?,
)

private const val AUTO_EXTEND_SEC = 300
private const val AUTO_EXTEND_LABEL = "Extension"
private const val MIN_INTENSITY_PERCENT = 10
private const val INTENSITY_STEP_PERCENT = 5
private const val MAX_SAMPLE_HISTORY = 6 * 3600

// Auto start/stop: begin the workout as soon as the rider starts pedaling (power above this
// small dead zone), and auto-pause as soon as they stop. Debounced in each direction so a
// single noisy zero-power sample or a brief coast doesn't flip state — starting reacts almost
// immediately, stopping waits a few seconds to ride out a coast or a gear shift.
private const val AUTO_START_THRESHOLD_WATTS = 20
private const val AUTO_START_DEBOUNCE_MS = 500L
private const val AUTO_STOP_DEBOUNCE_MS = 3000L

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
    // Merged live data (trainer + standalone HR sensor override, see MainViewModel.liveData) —
    // reading trainer.liveData directly here meant recorded samples never carried a real heart
    // rate (FTMS trainers essentially never report it themselves), so the workout chart's HR
    // trace had no data to draw despite the drawing code already being in place.
    private val liveData: StateFlow<TrainerSample>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WorkoutRunState())
    val state: StateFlow<WorkoutRunState> = _state.asStateFlow()

    private val _sampleHistory = MutableStateFlow<List<SamplePoint>>(emptyList())
    val sampleHistory: StateFlow<List<SamplePoint>> = _sampleHistory.asStateFlow()

    private var tickerJob: Job? = null
    private var lastSentWatts: Int? = null

    init {
        scope.launch {
            liveData
                .map { (it.powerWatts ?: 0) > AUTO_START_THRESHOLD_WATTS }
                .distinctUntilChanged()
                .collectLatest { pedaling ->
                    delay(if (pedaling) AUTO_START_DEBOUNCE_MS else AUTO_STOP_DEBOUNCE_MS)
                    val s = _state.value
                    if (pedaling) {
                        // Auto-start from idle, or auto-resume from an auto-pause — but never
                        // resume a workout the rider explicitly paused by hand.
                        if (s.steps.isNotEmpty() && !s.isRunning && (!s.hasStarted || s.autoPaused)) {
                            start()
                        }
                    } else if (s.isRunning) {
                        autoPause()
                    }
                }
        }
    }

    fun load(steps: List<WorkoutStep>) {
        stop()
        _sampleHistory.value = emptyList()
        _state.value = WorkoutRunState(
            steps = steps,
            totalDurationSec = steps.sumOf { it.durationSec },
        )
    }

    /** Scales every target power (current step and beyond) by this %FTP-style multiplier. Unbounded above. */
    fun setIntensity(percent: Int) {
        _state.value = _state.value.copy(
            intensityPercent = percent.coerceAtLeast(MIN_INTENSITY_PERCENT),
        )
        pushTargetForCurrentStep()
    }

    fun increaseIntensity() = setIntensity(_state.value.intensityPercent + INTENSITY_STEP_PERCENT)
    fun decreaseIntensity() = setIntensity(_state.value.intensityPercent - INTENSITY_STEP_PERCENT)

    fun start() {
        if (_state.value.steps.isEmpty() || _state.value.isRunning) return
        _state.value = _state.value.copy(isRunning = true, hasStarted = true, autoPaused = false)
        tickerJob?.cancel()
        tickerJob = scope.launch {
            // Start/Resume must reach the trainer and be acknowledged before the first target
            // power write — some trainers (Elite Direto included, per user reports) ignore or
            // silently drop a Set Target Power command sent while not yet in the started state,
            // which reads as "ERG mode not responding". A nested, unawaited launch here used to
            // race the two writes with no guaranteed order.
            trainer.startOrResume()
            pushTargetForCurrentStep()
            while (isActive && _state.value.isRunning) {
                tick()
                delay(1000)
            }
        }
    }

    fun pause() {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false, autoPaused = false)
        scope.launch { trainer.stop() }
    }

    /** Like [pause], but triggered by the rider stopping pedaling rather than an explicit tap —
     *  pedaling again auto-resumes it, unlike a manual pause which only offers Stop. */
    private fun autoPause() {
        if (!_state.value.isRunning) return
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false, autoPaused = true)
        scope.launch { trainer.stop() }
    }

    /** Fully ends the current workout session: clears the loaded plan, leaves the trainer connected. */
    fun exit() {
        tickerJob?.cancel()
        tickerJob = null
        lastSentWatts = null
        _state.value = WorkoutRunState()
        _sampleHistory.value = emptyList()
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
        _state.value = _state.value.copy(isRunning = false, autoPaused = false)
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
        val raw = step.targetWattsAt(s.elapsedInStepSec)
        val target = (raw * s.intensityPercent / 100f).roundToInt()
        _state.value = _state.value.copy(currentTargetWatts = target)
        if (target != lastSentWatts) {
            lastSentWatts = target
            scope.launch { trainer.setTargetPowerWatts(target) }
        }
        recordSample()
    }

    private fun recordSample() {
        if (!_state.value.isRunning) return
        val live = liveData.value
        val sample = SamplePoint(
            tSec = _state.value.totalElapsedSec,
            watts = live.powerWatts ?: 0,
            hrBpm = live.heartRateBpm,
            cadenceRpm = live.cadenceRpm?.toInt(),
        )
        _sampleHistory.value = (_sampleHistory.value + sample).takeLast(MAX_SAMPLE_HISTORY)
    }
}

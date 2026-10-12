package com.ergrm.trainer.workout

import com.ergrm.trainer.ble.TrainerConnection
import com.ergrm.trainer.ble.TrainerSample
import com.ergrm.trainer.data.AppSettings
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

/** ERG sends the file's own %FTP-derived watts to the trainer, as always. HR+ reinterprets the
 *  same per-interval percentage against LTHR instead of FTP, and slowly nudges the power actually
 *  sent so the rider's real heart rate tracks that target — see [WorkoutExecutor.tick]. SLO
 *  ignores the loaded workout's power/HR targets entirely and instead sends a manually-set grade
 *  percentage via FTMS simulation mode — see [WorkoutExecutor.setGrade]. */
enum class ControlMode {
    ERG, HR_PLUS, SLO;

    fun next(): ControlMode = when (this) {
        ERG -> HR_PLUS
        HR_PLUS -> SLO
        SLO -> ERG
    }
}

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
    val controlMode: ControlMode = ControlMode.ERG,
    /** Live target heart rate in HR+ mode (null in ERG, or before FTP/LTHR are configured) —
     *  the power actually sent to the trainer is [currentTargetWatts] either way. */
    val currentTargetBpm: Int? = null,
    /** SLO mode only: the grade (±%) currently being sent to the trainer via FTMS simulation
     *  mode. Resets to 0 (flat) whenever a workout is freshly loaded or exited — manually set
     *  from there via [WorkoutExecutor.setGrade]/increaseGrade/decreaseGrade, unless
     *  [routeProfile] is loaded, in which case the route itself drives it (see
     *  [WorkoutExecutor.advanceRoute]) and manual grade taps are ignored. */
    val currentGradePercent: Float = 0f,
    /** A GPX route loaded via [WorkoutExecutor.loadRoute] — mutually exclusive with [steps] (one
     *  replaces the other). Non-null here means grade is driven by position along this route,
     *  not manual taps. */
    val routeProfile: RouteProfile? = null,
    /** How far along [routeProfile] the ride has virtually traveled, estimated each tick from
     *  the trainer's own reported speed (see [WorkoutExecutor.advanceRoute]) — there's no real
     *  wheel-on-road distance to measure directly. */
    val routeDistanceM: Float = 0f,
) {
    val currentStep: WorkoutStep? get() = steps.getOrNull(currentStepIndex)
    val nextStep: WorkoutStep? get() = steps.getOrNull(currentStepIndex + 1)
    val remainingInStepSec: Int get() = (currentStep?.durationSec ?: 0) - elapsedInStepSec
    val totalRemainingSec: Int get() = (totalDurationSec - totalElapsedSec).coerceAtLeast(0)
}

/** One second of recorded live data during a running workout, used to trace power/HR/cadence on
 *  the chart, and (once saved) to compute a session's average speed/distance in the history
 *  detail view. */
data class SamplePoint(
    val tSec: Int,
    val watts: Int,
    val hrBpm: Int?,
    val cadenceRpm: Int?,
    val speedKmh: Float?,
    // SLO mode only: the grade (±%) being sent to the trainer at this instant — null in ERG/HR+,
    // so the chart can tell "flat/not in SLO" apart from "genuinely 0% grade".
    val gradePercent: Float? = null,
    // GPX-route rides only: how far along the route this sample was recorded (mirrors
    // WorkoutRunState.routeDistanceM at that instant) — lets the chart plot recorded
    // power/HR/cadence against distance instead of elapsed time, alongside the full route
    // profile shown from the start. Null outside a route ride.
    val distanceM: Float? = null,
)

private const val AUTO_EXTEND_SEC = 300
private const val AUTO_EXTEND_LABEL = "Extension"
private const val MIN_INTENSITY_PERCENT = 10
private const val INTENSITY_STEP_PERCENT = 2
private const val MAX_SAMPLE_HISTORY = 6 * 3600

// Auto start/stop: begin the workout as soon as the rider starts pedaling (power above this
// small dead zone), and auto-pause as soon as they stop. Debounced in each direction so a
// single noisy zero-power sample or a brief coast doesn't flip state — starting reacts almost
// immediately, stopping waits a few seconds to ride out a coast or a gear shift.
private const val AUTO_START_THRESHOLD_WATTS = 20
private const val AUTO_START_DEBOUNCE_MS = 500L
private const val AUTO_STOP_DEBOUNCE_MS = 3000L

// HR+ correction loop: every 20s, compare actual HR to the interval's LTHR-derived target and
// nudge the power actually sent by a small fixed step — still deliberately coarse, since power
// changes take tens of seconds to show up in heart rate and a much faster or finer loop would
// just chase noise, but tighter than the original 30s/5W after it felt sluggish in practice.
private const val HR_CORRECTION_INTERVAL_SEC = 20
private const val HR_CORRECTION_STEP_WATTS = 8
private const val HR_DEADBAND_BPM = 3

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
    // Read for FTP/LTHR whenever HR+ needs to reinterpret an interval's %FTP as %LTHR — not
    // copied in at load time, so a mid-ride FTP/LTHR edit in Settings takes effect immediately.
    private val settings: StateFlow<AppSettings>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WorkoutRunState())
    val state: StateFlow<WorkoutRunState> = _state.asStateFlow()

    private val _sampleHistory = MutableStateFlow<List<SamplePoint>>(emptyList())
    val sampleHistory: StateFlow<List<SamplePoint>> = _sampleHistory.asStateFlow()

    private var tickerJob: Job? = null
    private var lastSentWatts: Int? = null
    private var lastSentGrade: Float? = null
    // The plan exactly as loaded, before any auto-extend blocks get appended during the ride —
    // so exit() can restore this clean, restartable definition instead of whatever the finished
    // ride's steps list grew into.
    private var originalSteps: List<WorkoutStep> = emptyList()
    // Mutually exclusive with originalSteps — whichever was loaded most recently replaces the
    // other (see load/loadRoute) — restored the same way on exit() so re-starting the same ride
    // doesn't require re-picking the file.
    private var originalRoute: RouteProfile? = null

    // HR+ only: the power actually being sent, nudged slowly by the correction loop instead of
    // recomputed fresh every tick like the ERG target is — null means "not established yet",
    // which pushTargetForCurrentStep() seeds from the plain ERG-equivalent watts.
    private var hrPlusWatts: Int? = null
    private var secondsSinceHrCorrection = 0

    private fun resetHrPlusBaseline() {
        hrPlusWatts = null
        secondsSinceHrCorrection = 0
    }

    init {
        scope.launch {
            liveData
                .map { (it.powerWatts ?: 0) > AUTO_START_THRESHOLD_WATTS }
                .distinctUntilChanged()
                .collectLatest { pedaling ->
                    delay(if (pedaling) AUTO_START_DEBOUNCE_MS else AUTO_STOP_DEBOUNCE_MS)
                    val s = _state.value
                    if (pedaling) {
                        // Auto-start from idle, or resume from any pause (auto or manual) —
                        // pedaling again always means "keep going".
                        if ((s.steps.isNotEmpty() || s.routeProfile != null) && !s.isRunning) {
                            start()
                        }
                    } else if (s.isRunning) {
                        pause()
                    }
                }
        }
    }

    fun load(steps: List<WorkoutStep>) {
        stop()
        _sampleHistory.value = emptyList()
        originalSteps = steps
        originalRoute = null
        // Loading a new workout keeps the rider's ERG/HR+ choice from the previous one, rather
        // than always reverting to ERG.
        val mode = _state.value.controlMode
        _state.value = WorkoutRunState(
            steps = steps,
            totalDurationSec = steps.sumOf { it.durationSec },
            controlMode = mode,
        )
        // The auto-start collector above only reacts to a pedaling *transition* (not-pedaling ->
        // pedaling), so it never fires here if the rider was already pedaling before this load —
        // without this check the workout would sit loaded but idle until they either tap Start
        // or briefly coast and resume to manufacture a fresh transition.
        if ((liveData.value.powerWatts ?: 0) > AUTO_START_THRESHOLD_WATTS) {
            start()
        }
    }

    /** Loads a GPX route (see [RouteProfile]) and switches straight to SLO — a loaded route
     *  only makes sense there, so there's no reason to make the rider toggle modes themselves.
     *  Replaces any previously loaded time-based workout ([originalSteps]), same as [load]
     *  replaces any previously loaded route. */
    fun loadRoute(route: RouteProfile) {
        stop()
        _sampleHistory.value = emptyList()
        originalSteps = emptyList()
        originalRoute = route
        _state.value = WorkoutRunState(
            controlMode = ControlMode.SLO,
            routeProfile = route,
        )
        if ((liveData.value.powerWatts ?: 0) > AUTO_START_THRESHOLD_WATTS) {
            start()
        }
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

    /** Switches between ERG (send the file's %FTP-derived watts as-is), HR+ (reinterpret the
     *  same per-interval percentage against LTHR, then slowly correct actual watts toward it),
     *  and SLO (send a manually-set grade instead) — resets the HR+ correction loop so a new
     *  mode starts from a clean baseline. Rider+accessories weight is NOT sent automatically
     *  here: it risks silently overwriting whatever the rider has configured in the trainer's
     *  own companion app, on the same shared Bluetooth characteristic — see
     *  MainViewModel.sendWeightToTrainer, triggered only by an explicit Settings button. */
    fun setControlMode(mode: ControlMode) {
        if (_state.value.controlMode == mode) return
        _state.value = _state.value.copy(controlMode = mode)
        resetHrPlusBaseline()
        pushTargetForCurrentStep()
    }

    fun toggleControlMode() = setControlMode(_state.value.controlMode.next())

    /** SLO only: sets the grade sent to the trainer, clamped to ±[AppSettings.maxGradePercent]
     *  since that's a hardware limit of whichever trainer is connected, not a preference. A
     *  no-op while a route is loaded — the route itself drives grade every tick (see
     *  [advanceRoute]), so a manual tap here would just be overwritten on the next tick anyway. */
    fun setGrade(percent: Float) {
        if (_state.value.routeProfile != null) return
        val maxGrade = settings.value.maxGradePercent
        _state.value = _state.value.copy(currentGradePercent = percent.coerceIn(-maxGrade, maxGrade))
        pushTargetForCurrentStep()
    }

    fun increaseGrade() = setGrade(_state.value.currentGradePercent + settings.value.slopeStepPercent)
    fun decreaseGrade() = setGrade(_state.value.currentGradePercent - settings.value.slopeStepPercent)

    fun start() {
        val s = _state.value
        if ((s.steps.isEmpty() && s.routeProfile == null) || s.isRunning) return
        _state.value = _state.value.copy(isRunning = true, hasStarted = true)
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

    /** Pauses the ride, whether triggered by an explicit tap or by the rider coasting to a stop —
     *  either way, pedaling again resumes it (see the collector in [init]); only [exit] actually
     *  ends the session. */
    fun pause() {
        if (!_state.value.isRunning) return
        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRunning = false)
        scope.launch { trainer.stop() }
    }

    /** Ends the current ride: leaves the same workout loaded, reset to a clean, restartable state
     *  (any auto-extend blocks appended during the finished ride are discarded, progress and
     *  timers go back to zero) rather than clearing the plan entirely — so the rider can hit
     *  Start again for the same workout without re-picking it. The trainer stays connected. */
    fun exit() {
        tickerJob?.cancel()
        tickerJob = null
        lastSentWatts = null
        lastSentGrade = null
        resetHrPlusBaseline()
        val mode = _state.value.controlMode
        _state.value = WorkoutRunState(
            steps = originalSteps,
            totalDurationSec = originalSteps.sumOf { it.durationSec },
            controlMode = mode,
            routeProfile = originalRoute,
        )
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
        resetHrPlusBaseline()
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
        lastSentGrade = null
        resetHrPlusBaseline()
        _state.value = _state.value.copy(isRunning = false)
    }

    private fun tick() {
        val s = _state.value
        if (s.steps.isEmpty()) {
            // A GPX route with no separate time-based workout loaded — nothing to advance by
            // step index, just the clock (still needed for the chart's x axis and sample
            // history) and the route's own position, below. totalDurationSec has no real
            // meaning here (a route's duration depends on how fast the rider goes), so it's
            // just kept equal to totalElapsedSec — makes the chart's own "full workout" zoom
            // level naturally mean "zero to now" instead of needing a route-specific case.
            val newElapsed = s.totalElapsedSec + 1
            _state.value = s.copy(totalElapsedSec = newElapsed, totalDurationSec = newElapsed)
        } else {
            val step = s.currentStep ?: return
            val newElapsedInStep = s.elapsedInStepSec + 1
            if (newElapsedInStep >= step.durationSec) {
                val nextIndex = s.currentStepIndex + 1
                resetHrPlusBaseline()
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
        }
        advanceRoute()
        pushTargetForCurrentStep()
        maybeApplyHrCorrection()
    }

    /** No-op unless a route is loaded. Estimates how far the ride has virtually traveled this
     *  tick from the trainer's own reported speed (there's no real wheel-on-road distance to
     *  measure directly), then looks up and sets the grade for that position — the rider's
     *  manual ↓/↑ taps are ignored while a route drives grade (see [setGrade]). */
    private fun advanceRoute() {
        val s = _state.value
        val route = s.routeProfile ?: return
        val speedKmh = liveData.value.speedKmh ?: return
        val deltaM = speedKmh * 1000f / 3600f // 1 tick == 1 second
        val newDistance = (s.routeDistanceM + deltaM).coerceAtMost(route.totalDistanceM)
        val maxGrade = settings.value.maxGradePercent
        val grade = route.gradeAt(newDistance).coerceIn(-maxGrade, maxGrade)
        _state.value = _state.value.copy(routeDistanceM = newDistance, currentGradePercent = grade)
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
        // SLO ignores the loaded workout's power targets entirely — it only ever sends the
        // manually-set grade (see setGrade/increaseGrade/decreaseGrade), independent of which
        // step the ticker is nominally on.
        if (s.controlMode == ControlMode.SLO) {
            if (s.currentGradePercent != lastSentGrade) {
                lastSentGrade = s.currentGradePercent
                scope.launch { trainer.setSimulationGrade(s.currentGradePercent) }
            }
            recordSample()
            return
        }
        val step = s.currentStep ?: return
        val raw = step.targetWattsAt(s.elapsedInStepSec)
        val ergTarget = (raw * s.intensityPercent / 100f).roundToInt()

        val target: Int
        val targetBpm: Int?
        if (s.controlMode == ControlMode.HR_PLUS) {
            val athlete = settings.value
            // Same %-of-interval the file encodes as %FTP, reinterpreted against LTHR instead —
            // no change needed to the parsed WorkoutStep, since its %FTP fraction is just
            // raw / ftpWatts.
            targetBpm = if (athlete.ftpWatts > 0) {
                (athlete.lthrBpm * raw / athlete.ftpWatts.toFloat() * s.intensityPercent / 100f).roundToInt()
            } else {
                null
            }
            // Seed the correction loop from the plain ERG-equivalent watts the first time this
            // interval runs in HR+, then leave it alone until maybeApplyHrCorrection() nudges it.
            if (hrPlusWatts == null) hrPlusWatts = ergTarget
            target = hrPlusWatts!!
        } else {
            targetBpm = null
            target = ergTarget
        }

        _state.value = _state.value.copy(currentTargetWatts = target, currentTargetBpm = targetBpm)
        if (target != lastSentWatts) {
            lastSentWatts = target
            scope.launch { trainer.setTargetPowerWatts(target) }
        }
        recordSample()
    }

    /** Every [HR_CORRECTION_INTERVAL_SEC], nudges [hrPlusWatts] toward the live HR reading
     *  matching [WorkoutRunState.currentTargetBpm] — a small fixed step per tick rather than a
     *  proportional correction, since HR lags a power change by tens of seconds and a sharper
     *  loop would overshoot chasing that lag. No-op outside HR+, or before HR/target are known. */
    private fun maybeApplyHrCorrection() {
        val s = _state.value
        if (s.controlMode != ControlMode.HR_PLUS) return
        secondsSinceHrCorrection++
        if (secondsSinceHrCorrection < HR_CORRECTION_INTERVAL_SEC) return
        secondsSinceHrCorrection = 0

        val targetBpm = s.currentTargetBpm ?: return
        val actualBpm = liveData.value.heartRateBpm ?: return
        val current = hrPlusWatts ?: return
        val diff = actualBpm - targetBpm
        hrPlusWatts = when {
            diff > HR_DEADBAND_BPM -> (current - HR_CORRECTION_STEP_WATTS).coerceAtLeast(0)
            diff < -HR_DEADBAND_BPM -> current + HR_CORRECTION_STEP_WATTS
            else -> current
        }
        pushTargetForCurrentStep()
    }

    private fun recordSample() {
        if (!_state.value.isRunning) return
        val live = liveData.value
        val s = _state.value
        val sample = SamplePoint(
            tSec = s.totalElapsedSec,
            watts = live.powerWatts ?: 0,
            hrBpm = live.heartRateBpm,
            cadenceRpm = live.cadenceRpm?.toInt(),
            speedKmh = live.speedKmh,
            gradePercent = if (s.controlMode == ControlMode.SLO) s.currentGradePercent else null,
            distanceM = s.routeProfile?.let { s.routeDistanceM },
        )
        _sampleHistory.value = (_sampleHistory.value + sample).takeLast(MAX_SAMPLE_HISTORY)
    }
}

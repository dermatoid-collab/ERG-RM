package com.ergrm.trainer.intervals

import com.ergrm.trainer.workout.WorkoutStep
import com.ergrm.trainer.workout.ZwoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class LoadedWorkout(
    val id: Long,
    val name: String,
    val steps: List<WorkoutStep>,
)

sealed interface FetchResult {
    data class Success(val workout: LoadedWorkout) : FetchResult
    data object NoStepsFound : FetchResult
    data class Error(val message: String) : FetchResult
}

/** One planned workout on the Intervals.icu calendar, as listed before it's loaded. */
data class CalendarWorkout(
    val eventId: Long,
    val date: LocalDate,
    val name: String,
    val movingTimeSec: Int?,
)

sealed interface CalendarFetchResult {
    data class Success(val workouts: List<CalendarWorkout>) : CalendarFetchResult
    data class Error(val message: String) : CalendarFetchResult
}

/** One reusable (undated) workout from the athlete's Intervals.icu library, flattened out of
 *  whatever folder tree it was nested in — [folderPath] is that folder's display name.
 *  [workoutDoc] is its full structure exactly as /folders returned it (there's no separate
 *  per-workout download endpoint), parsed on demand by [IntervalsRepository.loadLibraryWorkout]. */
data class LibraryWorkout(
    val workoutId: Long,
    val folderPath: String,
    val name: String,
    val workoutDoc: JsonElement,
)

sealed interface LibraryFetchResult {
    data class Success(val workouts: List<LibraryWorkout>) : LibraryFetchResult
    data class Error(val message: String) : LibraryFetchResult
}

private const val BASE_URL = "https://intervals.icu/"
private val BIKE_TYPES = setOf("Ride", "VirtualRide", "GravelRide", "MountainBikeRide")

class IntervalsRepository {

    private fun buildApi(apiKey: String): IntervalsApi {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic("API_KEY", apiKey))
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()

        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(IntervalsApi::class.java)
    }

    /** Today's first planned bike workout — the "Intervals WOD" quick-load shortcut. If more
     *  than one is scheduled today, the earliest-listed one wins; use [fetchCalendarWorkouts]
     *  to see and pick between all of them. */
    suspend fun fetchTodayWorkout(apiKey: String, athleteId: String, ftpWatts: Int): FetchResult =
        withContext(Dispatchers.IO) {
            try {
                val api = buildApi(apiKey)
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val events = api.getEvents(athleteId, oldest = today, newest = today)
                val candidate = events.firstOrNull { it.category == "WORKOUT" && (it.type == null || it.type in BIKE_TYPES) }
                    ?: events.firstOrNull { it.category == "WORKOUT" }
                    ?: return@withContext FetchResult.NoStepsFound
                loadWorkout(candidate.name ?: "Workout", ftpWatts, "event ${candidate.id}") {
                    api.getWorkoutZwo(athleteId, candidate.id)
                }
            } catch (t: Exception) {
                FetchResult.Error(describeError(t))
            }
        }

    /** Planned bike workouts from the Monday of this week through the Sunday of next week. */
    suspend fun fetchCalendarWorkouts(apiKey: String, athleteId: String): CalendarFetchResult =
        withContext(Dispatchers.IO) {
            try {
                val api = buildApi(apiKey)
                val today = LocalDate.now()
                val mondayThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sundayNextWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(1)
                val events = api.getEvents(
                    athleteId,
                    oldest = mondayThisWeek.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    newest = sundayNextWeek.format(DateTimeFormatter.ISO_LOCAL_DATE),
                )
                val workouts = events
                    .filter { it.category == "WORKOUT" && (it.type == null || it.type in BIKE_TYPES) }
                    .mapNotNull { event ->
                        val dateStr = event.startDateLocal ?: return@mapNotNull null
                        val date = try {
                            LocalDate.parse(dateStr.take(10))
                        } catch (t: Exception) {
                            return@mapNotNull null
                        }
                        CalendarWorkout(
                            eventId = event.id,
                            date = date,
                            name = event.name ?: "Workout",
                            movingTimeSec = event.movingTimeSec,
                        )
                    }
                    .sortedBy { it.date }
                CalendarFetchResult.Success(workouts)
            } catch (t: Exception) {
                CalendarFetchResult.Error(describeError(t))
            }
        }

    /** Downloads and parses a specific planned event, picked from [fetchCalendarWorkouts]. */
    suspend fun fetchWorkoutByEventId(
        apiKey: String,
        athleteId: String,
        eventId: Long,
        name: String,
        ftpWatts: Int,
    ): FetchResult = withContext(Dispatchers.IO) {
        try {
            val api = buildApi(apiKey)
            loadWorkout(name, ftpWatts, "event $eventId") { api.getWorkoutZwo(athleteId, eventId) }
        } catch (t: Exception) {
            FetchResult.Error(describeError(t))
        }
    }

    /** The athlete's saved workout library (folders of reusable, undated workouts), flattened to
     *  one entry per workout. The exact response shape hasn't been verified against a live
     *  account from this environment, so a mismatch is surfaced as [LibraryFetchResult.Error]
     *  with the raw response instead of crashing — check that message against the real payload
     *  if this comes back empty or wrong on a real device. */
    suspend fun fetchLibrary(apiKey: String, athleteId: String): LibraryFetchResult =
        withContext(Dispatchers.IO) {
            val raw = try {
                buildApi(apiKey).getFoldersRaw(athleteId).string()
            } catch (t: Exception) {
                return@withContext LibraryFetchResult.Error(describeError(t))
            }
            val nodes = try {
                Json { ignoreUnknownKeys = true }.decodeFromString<List<IcuFolderDto>>(raw)
            } catch (t: Exception) {
                return@withContext LibraryFetchResult.Error(
                    "Unexpected response from Intervals.icu /folders — the reader may need updating for this " +
                        "account's data: ${raw.take(400)}",
                )
            }
            val workouts = nodes.flatMap { flattenLibraryNode(it, it.name ?: "Library") }
            if (workouts.isEmpty() && nodes.isNotEmpty()) {
                // The JSON decoded fine (so the top-level shape matches), but nothing survived
                // flattenLibraryNode's type/children guess — e.g. a folder nested under a
                // "Training Plan"-type entry rather than a plain "FOLDER". Surface the raw
                // response instead of a silent (and here, misleading) "nothing found".
                return@withContext LibraryFetchResult.Error(
                    "No workouts matched after parsing ${nodes.size} top-level folder(s) — please share this " +
                        "so the folder/workout type names can be corrected: ${raw.take(600)}",
                )
            }
            LibraryFetchResult.Success(workouts)
        }

    /** Walks the folder tree collecting workout leaves, labeling each with the name of the
     *  nearest enclosing folder (not a full nested path — simplest thing that reads well in a
     *  flat list). Confirmed against a real account: a folder node always has `type: "FOLDER"`
     *  (checked first, even when empty — an empty folder isn't a workout); a real workout leaf
     *  carries a non-null [IcuFolderDto.workoutDoc], which an empty placeholder folder lacks. */
    private fun flattenLibraryNode(node: IcuFolderDto, folderName: String): List<LibraryWorkout> {
        if (node.type == "FOLDER") {
            val label = node.name ?: folderName
            return node.children.orEmpty().flatMap { flattenLibraryNode(it, label) }
        }
        val doc = node.workoutDoc
        if (doc != null) {
            return listOf(LibraryWorkout(workoutId = node.id, folderPath = folderName, name = node.name ?: "Workout", workoutDoc = doc))
        }
        return node.children.orEmpty().flatMap { flattenLibraryNode(it, folderName) }
    }

    /** Parses a library workout's steps straight out of the `workout_doc` JSON captured by
     *  [fetchLibrary] — no separate network call (a guessed per-workout download endpoint 404s;
     *  Intervals.icu doesn't expose one, the full structure just comes inline in /folders). */
    fun loadLibraryWorkout(workout: LibraryWorkout, ftpWatts: Int): FetchResult {
        val steps = try {
            parseWorkoutDocSteps(workout.workoutDoc, ftpWatts)
        } catch (t: Exception) {
            return FetchResult.Error(
                "Couldn't read '${workout.name}'s structure — please share this so the reader can be fixed: " +
                    "${t.message}: ${workout.workoutDoc}".take(600),
            )
        }
        if (steps.isEmpty()) return FetchResult.NoStepsFound
        return FetchResult.Success(LoadedWorkout(id = workout.workoutId, name = workout.name, steps = steps))
    }

    /** workout_doc looks like `{"steps": [...]}`, where each entry is either a leaf step (a
     *  "duration" in seconds and a "power" — `{"units": "%ftp", "value": 39}` for a steady 39%
     *  FTP target, confirmed against a real workout, or presumably `start`/`end` for a ramp) or a
     *  group with its own nested "steps" and a "reps" count for a repeated block. */
    private fun parseWorkoutDocSteps(doc: JsonElement, ftpWatts: Int): List<WorkoutStep> {
        val topSteps = doc.jsonObject["steps"]?.jsonArray ?: error("no 'steps' array in workout_doc")
        return topSteps.flatMap { parseDocStep(it.jsonObject, ftpWatts) }
    }

    private fun parseDocStep(obj: JsonObject, ftpWatts: Int): List<WorkoutStep> {
        val nestedSteps = obj["steps"]?.jsonArray
        if (nestedSteps != null) {
            val reps = (obj["reps"] ?: obj["repeat"])?.jsonPrimitive?.intOrNull ?: 1
            val group = nestedSteps.flatMap { parseDocStep(it.jsonObject, ftpWatts) }
            return (1..reps).flatMap { group }
        }
        val durationSec = listOf("duration", "durationSec", "seconds", "time")
            .firstNotNullOfOrNull { key -> obj[key]?.jsonPrimitive?.intOrNull }
            ?: error("no recognized duration field")
        val powerObj = obj["power"]?.jsonObject
        val (startFraction, endFraction) = when {
            powerObj == null -> 0.5f to 0.5f
            powerObj["value"]?.jsonPrimitive?.floatOrNull != null -> {
                val fraction = powerObj["value"]!!.jsonPrimitive.float / 100f
                fraction to fraction
            }
            powerObj["start"]?.jsonPrimitive?.floatOrNull != null && powerObj["end"]?.jsonPrimitive?.floatOrNull != null -> {
                (powerObj["start"]!!.jsonPrimitive.float / 100f) to (powerObj["end"]!!.jsonPrimitive.float / 100f)
            }
            else -> error("unrecognized 'power' shape")
        }
        return listOf(
            WorkoutStep(
                durationSec = durationSec,
                startWatts = (startFraction * ftpWatts).roundToInt(),
                endWatts = (endFraction * ftpWatts).roundToInt(),
                label = "Step",
            ),
        )
    }

    private suspend fun loadWorkout(name: String, ftpWatts: Int, sourceLabel: String, download: suspend () -> ResponseBody): FetchResult {
        val zwoBody = try {
            download().string()
        } catch (t: Exception) {
            return FetchResult.Error("Couldn't download the workout: ${describeError(t)}")
        }
        if (!zwoBody.contains("<workout", ignoreCase = true)) {
            // Not actual ZWO content — most likely an error page or unexpected response body,
            // not a workout that's genuinely empty. Surface it instead of silently reporting
            // "no workout today".
            return FetchResult.Error("Unexpected response from Intervals.icu for $sourceLabel: ${zwoBody.take(120)}")
        }
        val steps = ZwoParser.parse(zwoBody, ftpWatts)
        if (steps.isEmpty()) return FetchResult.NoStepsFound
        return FetchResult.Success(LoadedWorkout(id = 0, name = name, steps = steps))
    }

    private fun describeError(t: Throwable): String = when (t) {
        is retrofit2.HttpException -> {
            val body = t.response()?.errorBody()?.string()?.take(200)
            "HTTP ${t.code()}" + if (!body.isNullOrBlank()) " — $body" else ""
        }
        else -> t.message ?: t.javaClass.simpleName
    }
}

package com.ergrm.trainer.intervals

import com.ergrm.trainer.workout.WorkoutStep
import com.ergrm.trainer.workout.ZwoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
 *  whatever folder tree it was nested in — [folderPath] is that folder's display name. */
data class LibraryWorkout(
    val workoutId: Long,
    val folderPath: String,
    val name: String,
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

    /** Walks the folder tree collecting WORKOUT leaves, labeling each with the name of the
     *  nearest enclosing folder (not a full nested path — simplest thing that reads well in a
     *  flat list). A node with no [IcuFolderDto.type] and no children is treated as a workout,
     *  since the exact type strings this account's folders use aren't independently confirmed. */
    private fun flattenLibraryNode(node: IcuFolderDto, folderName: String): List<LibraryWorkout> {
        val children = node.children
        if (children.isNullOrEmpty()) {
            return if (node.type == null || node.type == "WORKOUT") {
                listOf(LibraryWorkout(workoutId = node.id, folderPath = folderName, name = node.name ?: "Workout"))
            } else {
                emptyList()
            }
        }
        val labelForChildren = if (node.type == "FOLDER" || node.type == null) node.name ?: folderName else folderName
        return children.flatMap { flattenLibraryNode(it, labelForChildren) }
    }

    /** Downloads and parses a specific library workout, picked from [fetchLibrary]. */
    suspend fun fetchLibraryWorkoutById(
        apiKey: String,
        athleteId: String,
        workoutId: Long,
        name: String,
        ftpWatts: Int,
    ): FetchResult = withContext(Dispatchers.IO) {
        try {
            val api = buildApi(apiKey)
            loadWorkout(name, ftpWatts, "library workout $workoutId") { api.getLibraryWorkoutZwo(athleteId, workoutId) }
        } catch (t: Exception) {
            FetchResult.Error(describeError(t))
        }
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

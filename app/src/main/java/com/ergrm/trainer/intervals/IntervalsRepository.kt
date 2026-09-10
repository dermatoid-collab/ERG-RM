package com.ergrm.trainer.intervals

import com.ergrm.trainer.workout.WorkoutStep
import com.ergrm.trainer.workout.ZwoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class LoadedWorkout(
    val eventId: Long,
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
            loadWorkout(buildApi(apiKey), athleteId, eventId, name, ftpWatts)
        } catch (t: Exception) {
            FetchResult.Error(describeError(t))
        }
    }

    private suspend fun loadWorkout(api: IntervalsApi, athleteId: String, eventId: Long, name: String, ftpWatts: Int): FetchResult {
        val zwoBody = try {
            api.getWorkoutZwo(athleteId, eventId).string()
        } catch (t: Exception) {
            return FetchResult.Error("Couldn't download the workout: ${describeError(t)}")
        }
        if (!zwoBody.contains("<workout", ignoreCase = true)) {
            // Not actual ZWO content — most likely an error page or unexpected response body,
            // not a workout that's genuinely empty. Surface it instead of silently reporting
            // "no workout today".
            return FetchResult.Error("Unexpected response from Intervals.icu for event $eventId: ${zwoBody.take(120)}")
        }
        val steps = ZwoParser.parse(zwoBody, ftpWatts)
        if (steps.isEmpty()) return FetchResult.NoStepsFound
        return FetchResult.Success(LoadedWorkout(eventId = eventId, name = name, steps = steps))
    }

    private fun describeError(t: Throwable): String = when (t) {
        is retrofit2.HttpException -> {
            val body = t.response()?.errorBody()?.string()?.take(200)
            "HTTP ${t.code()}" + if (!body.isNullOrBlank()) " — $body" else ""
        }
        else -> t.message ?: t.javaClass.simpleName
    }
}

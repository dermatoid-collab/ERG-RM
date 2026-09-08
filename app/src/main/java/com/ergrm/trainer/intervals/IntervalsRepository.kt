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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TodayWorkout(
    val eventId: Long,
    val name: String,
    val steps: List<WorkoutStep>,
)

sealed interface FetchResult {
    data class Success(val workout: TodayWorkout) : FetchResult
    data object NoWorkoutToday : FetchResult
    data class Error(val message: String) : FetchResult
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

    suspend fun fetchTodayWorkout(apiKey: String, athleteId: String, ftpWatts: Int): FetchResult =
        withContext(Dispatchers.IO) {
            try {
                val api = buildApi(apiKey)
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val events = try {
                    api.getEvents(athleteId, oldest = today, newest = today)
                } catch (t: Exception) {
                    return@withContext FetchResult.Error("Impossibile leggere il calendario: ${describeError(t)}")
                }

                val candidate = events.firstOrNull { it.category == "WORKOUT" && (it.type == null || it.type in BIKE_TYPES) }
                    ?: events.firstOrNull { it.category == "WORKOUT" }
                    ?: return@withContext FetchResult.NoWorkoutToday

                val zwoBody = try {
                    api.getWorkoutZwo(athleteId, candidate.id).string()
                } catch (t: Exception) {
                    return@withContext FetchResult.Error("Impossibile scaricare l'allenamento: ${describeError(t)}")
                }
                if (!zwoBody.contains("<workout", ignoreCase = true)) {
                    // Not actual ZWO content — most likely an error page or unexpected response
                    // body, not a workout that's genuinely empty. Surface it instead of silently
                    // reporting "no workout today".
                    return@withContext FetchResult.Error(
                        "Risposta inattesa da Intervals.icu per l'evento ${candidate.id}: ${zwoBody.take(120)}",
                    )
                }
                val steps = ZwoParser.parse(zwoBody, ftpWatts)
                if (steps.isEmpty()) return@withContext FetchResult.NoWorkoutToday

                FetchResult.Success(
                    TodayWorkout(eventId = candidate.id, name = candidate.name ?: "Workout", steps = steps)
                )
            } catch (t: Exception) {
                FetchResult.Error(describeError(t))
            }
        }

    private fun describeError(t: Throwable): String = when (t) {
        is retrofit2.HttpException -> {
            val body = t.response()?.errorBody()?.string()?.take(200)
            "HTTP ${t.code()}" + if (!body.isNullOrBlank()) " — $body" else ""
        }
        else -> t.message ?: t.javaClass.simpleName
    }
}

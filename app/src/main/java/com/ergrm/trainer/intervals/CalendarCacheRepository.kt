package com.ergrm.trainer.intervals

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true }

/** [CalendarWorkout] isn't itself @Serializable (its LocalDate has no built-in kotlinx.serialization
 *  support) — this mirrors its fields with the date as an epoch day instead, just for the JSON file. */
@Serializable
private data class CachedCalendarWorkout(
    val eventId: Long,
    val dateEpochDay: Long,
    val name: String,
    val movingTimeSec: Int?,
)

/**
 * Last-fetched Calendar (Intervals.icu) workout list, persisted as a single JSON file in the
 * app's private storage — same trade-off as [com.ergrm.trainer.history.SessionHistoryRepository].
 * Lets the rider still see what's scheduled (names, dates, durations) when Intervals.icu is
 * briefly unreachable (a 503, no signal, etc.), instead of an empty error screen. Loading a
 * cached entry still needs a live fetch for its actual steps — this only covers the list.
 */
class CalendarCacheRepository(private val context: Context) {

    private val file: File get() = File(context.filesDir, "calendar_cache.json")

    suspend fun load(): List<CalendarWorkout> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<CachedCalendarWorkout>>(file.readText()).map {
                CalendarWorkout(
                    eventId = it.eventId,
                    date = LocalDate.ofEpochDay(it.dateEpochDay),
                    name = it.name,
                    movingTimeSec = it.movingTimeSec,
                )
            }
        } catch (t: Exception) {
            emptyList()
        }
    }

    suspend fun save(workouts: List<CalendarWorkout>) = withContext(Dispatchers.IO) {
        val cached = workouts.map {
            CachedCalendarWorkout(
                eventId = it.eventId,
                dateEpochDay = it.date.toEpochDay(),
                name = it.name,
                movingTimeSec = it.movingTimeSec,
            )
        }
        file.writeText(json.encodeToString(cached))
    }
}

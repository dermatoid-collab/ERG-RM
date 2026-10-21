package com.ergrm.trainer.intervals

import android.content.Context
import com.ergrm.trainer.workout.WorkoutStep
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

/** One event's downloaded structure — a flat list (not a Map<Long, ...>) so this file doesn't
 *  depend on kotlinx.serialization's handling of non-String map keys. */
@Serializable
private data class CachedEventStructure(val eventId: Long, val steps: List<WorkoutStep>)

/**
 * Last-fetched Calendar (Intervals.icu) workout list, and each event's actual structure, both
 * persisted as JSON files in the app's private storage — same trade-off as
 * [com.ergrm.trainer.history.SessionHistoryRepository]. Together these let the rider still see
 * *and ride* what's scheduled when Intervals.icu is unreachable (a 503, no signal, etc.) instead
 * of an empty error screen or a load that only works with a live connection.
 *
 * The list and the structures are cached separately because they refresh on different schedules:
 * the list changes often (the rider reschedules, Intervals.icu generates next week's plan) and is
 * cheap to refetch whole, while a given event's structure is effectively immutable once set and
 * worth fetching only once — see [pruneStructures] for how the two stay in sync regardless.
 */
class CalendarCacheRepository(private val context: Context) {

    private val listFile: File get() = File(context.filesDir, "calendar_cache.json")
    private val structuresFile: File get() = File(context.filesDir, "calendar_structures_cache.json")

    suspend fun load(): List<CalendarWorkout> = withContext(Dispatchers.IO) {
        if (!listFile.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<CachedCalendarWorkout>>(listFile.readText()).map {
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
        listFile.writeText(json.encodeToString(cached))
    }

    suspend fun loadStructures(): Map<Long, List<WorkoutStep>> = withContext(Dispatchers.IO) { readStructures() }

    /** Adds or replaces one event's structure, keeping every other cached one — called once per
     *  event right after it's downloaded (see MainViewModel's Calendar prefetch), not as a bulk
     *  rewrite, so prefetching event N's structure can never clobber event N-1's just fetched a
     *  moment earlier. */
    suspend fun saveStructure(eventId: Long, steps: List<WorkoutStep>) = withContext(Dispatchers.IO) {
        val current = readStructures().toMutableMap()
        current[eventId] = steps
        writeStructures(current)
    }

    /** Drops any cached structure for an event no longer in the current Calendar list — called
     *  after each successful list refresh so the structure cache stays bounded to the current
     *  ~2-week rolling window instead of growing forever as old events scroll out of it. */
    suspend fun pruneStructures(keepEventIds: Set<Long>) = withContext(Dispatchers.IO) {
        val current = readStructures()
        val pruned = current.filterKeys { it in keepEventIds }
        if (pruned.size != current.size) writeStructures(pruned)
    }

    private fun readStructures(): Map<Long, List<WorkoutStep>> {
        if (!structuresFile.exists()) return emptyMap()
        return try {
            json.decodeFromString<List<CachedEventStructure>>(structuresFile.readText())
                .associate { it.eventId to it.steps }
        } catch (t: Exception) {
            emptyMap()
        }
    }

    private fun writeStructures(structures: Map<Long, List<WorkoutStep>>) {
        val cached = structures.map { (eventId, steps) -> CachedEventStructure(eventId, steps) }
        structuresFile.writeText(json.encodeToString(cached))
    }
}

package com.ergrm.trainer.intervals

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A single calendar event as returned by GET /athlete/{id}/events.
 * We only model the fields this app needs; intervals.icu returns many more.
 */
@Serializable
data class IcuEventDto(
    val id: Long,
    @SerialName("start_date_local") val startDateLocal: String? = null,
    val category: String? = null,
    val type: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("moving_time") val movingTimeSec: Int? = null,
)

/**
 * One node of the athlete's workout library, as returned by GET /athlete/{id}/folders — a tree
 * of folders and reusable (undated) workouts. The exact field names/shape are not independently
 * verified against live Intervals.icu (this environment can't reach intervals.icu directly), so
 * IntervalsRepository decodes this defensively and surfaces the raw JSON if it doesn't match.
 */
@Serializable
data class IcuFolderDto(
    val id: Long,
    val name: String? = null,
    val type: String? = null,
    val children: List<IcuFolderDto>? = null,
)

interface IntervalsApi {

    /** Events (including planned workouts) between [oldest] and [newest], both yyyy-MM-dd. */
    @GET("api/v1/athlete/{athleteId}/events")
    suspend fun getEvents(
        @Path("athleteId") athleteId: String,
        @Query("oldest") oldest: String,
        @Query("newest") newest: String,
    ): List<IcuEventDto>

    /** Structured workout for a planned event, in .zwo XML format. */
    @GET("api/v1/athlete/{athleteId}/events/{eventId}/download.zwo")
    suspend fun getWorkoutZwo(
        @Path("athleteId") athleteId: String,
        @Path("eventId") eventId: Long,
    ): ResponseBody

    /** The athlete's workout library (folders + reusable workouts), as raw JSON — decoded
     *  manually by the repository so an unexpected shape can be surfaced instead of crashing. */
    @GET("api/v1/athlete/{athleteId}/folders")
    suspend fun getFoldersRaw(@Path("athleteId") athleteId: String): ResponseBody

    /** A specific library workout's structure, in .zwo XML format. */
    @GET("api/v1/athlete/{athleteId}/workouts/{workoutId}/download.zwo")
    suspend fun getLibraryWorkoutZwo(
        @Path("athleteId") athleteId: String,
        @Path("workoutId") workoutId: Long,
    ): ResponseBody
}

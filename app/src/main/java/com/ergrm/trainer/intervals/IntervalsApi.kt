package com.ergrm.trainer.intervals

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
 * of folders and reusable (undated) workouts. Confirmed against a real account: a folder has
 * type:"FOLDER"; a workout leaf's own type is its sport (e.g. "Ride"). [workoutDoc] is only used
 * as a leaf/folder signal here — its content isn't parsed locally (see [getWorkoutRaw] /
 * [downloadWorkoutFromJson] for why).
 */
@Serializable
data class IcuFolderDto(
    val id: Long,
    val name: String? = null,
    val type: String? = null,
    val children: List<IcuFolderDto>? = null,
    @SerialName("workout_doc") val workoutDoc: JsonElement? = null,
)

/** Per-sport athlete settings, as returned by GET /athlete/{id}/sport-settings/{type} — only the
 *  fields this app pulls in for the FTP/LTHR sync button. [indoorFtp] is preferred over [ftp] when
 *  present, since that's the number the rider actually trains ERG against on a smart trainer. */
@Serializable
data class IcuSportSettingsDto(
    val ftp: Int? = null,
    @SerialName("indoor_ftp") val indoorFtp: Int? = null,
    val lthr: Int? = null,
)

interface IntervalsApi {

    /** [type] is a sport key, e.g. "Ride" — matches what this app already loads bike workouts as. */
    @GET("api/v1/athlete/{athleteId}/sport-settings/{type}")
    suspend fun getSportSettings(
        @Path("athleteId") athleteId: String,
        @Path("type") type: String,
    ): IcuSportSettingsDto

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

    /** One saved library workout's full record (per Intervals.icu's OpenAPI spec — confirmed via
     *  https://github.com/eddmann/intervals-icu-mcp), as raw JSON. Passed straight through to
     *  [downloadWorkoutFromJson] rather than parsed locally: earlier attempts at interpreting
     *  workout_doc's step schema ourselves (duration field name, repeat structure, percent vs.
     *  fraction power values) got it wrong on a real account even after several rounds of fixes. */
    @GET("api/v1/athlete/{athleteId}/workouts/{workoutId}")
    suspend fun getWorkoutRaw(
        @Path("athleteId") athleteId: String,
        @Path("workoutId") workoutId: Long,
    ): ResponseBody

    /** Converts a Workout JSON object (as returned by [getWorkoutRaw]) into .zwo XML, per
     *  Intervals.icu's own OpenAPI-documented `POST /download-workout{ext}` — round-tripping
     *  through their own converter instead of guessing workout_doc's schema, so the result can be
     *  parsed with the same ZwoParser already verified against the calendar path. */
    @POST("api/v1/athlete/{athleteId}/download-workout.zwo")
    suspend fun downloadWorkoutFromJson(
        @Path("athleteId") athleteId: String,
        @Body workout: RequestBody,
    ): ResponseBody
}

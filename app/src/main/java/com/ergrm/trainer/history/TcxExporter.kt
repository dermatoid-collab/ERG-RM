package com.ergrm.trainer.history

import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Builds a standard Garmin TCX (Training Center XML) document from a completed [WorkoutSession]
 * — the format Strava, TrainingPeaks and Intervals.icu all accept for a completed-activity
 * upload, unlike this app's own JSON backup. There's no GPS track (an indoor trainer ride has
 * none), so [DistanceMeters] is estimated by integrating the recorded speed sample-by-sample
 * rather than read from a route.
 */
object TcxExporter {

    fun build(session: WorkoutSession): String {
        val startInstant = Instant.ofEpochMilli(session.startEpochMillis)
        val samples = session.samples

        var distanceM = 0.0
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" " +
                "xmlns:ns3=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n",
        )
        sb.append("  <Activities>\n")
        sb.append("    <Activity Sport=\"Biking\">\n")
        sb.append("      <Id>${isoInstant(startInstant)}</Id>\n")
        sb.append("      <Lap StartTime=\"${isoInstant(startInstant)}\">\n")
        sb.append("        <TotalTimeSeconds>${session.durationSec}</TotalTimeSeconds>\n")

        val hrs = samples.mapNotNull { it.hrBpm }
        val maxSpeedKmh = samples.mapNotNull { it.speedKmh }.maxOrNull()
        // DistanceMeters here is a placeholder (0) until the track below finishes integrating
        // speed — TCX doesn't require the Lap's summary distance to precede the Track, but
        // writing it accurately means knowing the running total first.
        val trackSb = StringBuilder("        <Track>\n")
        for (sample in samples) {
            val t = startInstant.plusSeconds(sample.tSec.toLong())
            sample.speedKmh?.let { distanceM += it / 3.6 } // km/h -> m/s, one sample = one second
            trackSb.append("          <Trackpoint>\n")
            trackSb.append("            <Time>${isoInstant(t)}</Time>\n")
            trackSb.append("            <DistanceMeters>${"%.1f".format(distanceM)}</DistanceMeters>\n")
            sample.hrBpm?.let { trackSb.append("            <HeartRateBpm><Value>$it</Value></HeartRateBpm>\n") }
            sample.cadenceRpm?.let { trackSb.append("            <Cadence>$it</Cadence>\n") }
            trackSb.append("            <Extensions><ns3:TPX><ns3:Watts>${sample.watts}</ns3:Watts>")
            sample.speedKmh?.let { trackSb.append("<ns3:Speed>${"%.2f".format(it / 3.6)}</ns3:Speed>") }
            trackSb.append("</ns3:TPX></Extensions>\n")
            trackSb.append("          </Trackpoint>\n")
        }
        trackSb.append("        </Track>\n")

        sb.append("        <DistanceMeters>${"%.1f".format(distanceM)}</DistanceMeters>\n")
        sb.append("        <Calories>0</Calories>\n")
        if (hrs.isNotEmpty()) {
            sb.append("        <AverageHeartRateBpm><Value>${hrs.average().roundToInt()}</Value></AverageHeartRateBpm>\n")
            sb.append("        <MaximumHeartRateBpm><Value>${hrs.max()}</Value></MaximumHeartRateBpm>\n")
        }
        sb.append("        <Intensity>Active</Intensity>\n")
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        sb.append(trackSb)
        sb.append("        <Extensions><ns3:LX>")
        sb.append("<ns3:AvgWatts>${session.avgWatts}</ns3:AvgWatts>")
        sb.append("<ns3:MaxWatts>${session.maxWatts}</ns3:MaxWatts>")
        maxSpeedKmh?.let { sb.append("<ns3:MaxSpeed>${"%.2f".format(it / 3.6)}</ns3:MaxSpeed>") }
        sb.append("</ns3:LX></Extensions>\n")
        sb.append("      </Lap>\n")
        session.workoutName?.let { sb.append("      <Notes>${escapeXml(it)}</Notes>\n") }
        sb.append("      <Creator xsi:type=\"Device_t\">\n")
        sb.append("        <Name>ERG-RM</Name>\n")
        sb.append("      </Creator>\n")
        sb.append("    </Activity>\n")
        sb.append("  </Activities>\n")
        sb.append("</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    private fun isoInstant(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

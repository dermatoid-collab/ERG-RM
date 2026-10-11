package com.ergrm.trainer.workout

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A GPX route's elevation profile, resampled into fixed-length [segmentLengthM] segments —
 * 50m by default — so GPS noise is smoothed out by construction (each segment's grade is the
 * real rise over a real 50m run, not two adjacent raw points that might be a meter apart) and
 * SLO gets a steady, predictable spatial resolution to update grade at, instead of jittering
 * between every raw trackpoint.
 */
data class RouteProfile(
    val name: String,
    val segmentLengthM: Float,
    // One grade value (%) per segment — gradePercents[i] is the grade from
    // i*segmentLengthM to (i+1)*segmentLengthM.
    val gradePercents: List<Float>,
) {
    val totalDistanceM: Float get() = gradePercents.size * segmentLengthM

    /** Grade at [distanceM] along the route — holds the last segment's grade past the end,
     *  same "hold the last value" convention as a time-based workout's auto-extend. */
    fun gradeAt(distanceM: Float): Float {
        if (gradePercents.isEmpty()) return 0f
        val index = (distanceM / segmentLengthM).toInt().coerceIn(0, gradePercents.size - 1)
        return gradePercents[index]
    }
}

/** Parses a .gpx track into a [RouteProfile]. Reads every &lt;trkpt&gt; (lat/lon/ele) in file
 *  order across all &lt;trkseg&gt;s — multiple segments are treated as one continuous route. */
object GpxParser {
    private const val EARTH_RADIUS_M = 6_371_000.0
    const val DEFAULT_SEGMENT_LENGTH_M = 50f

    fun parse(xml: String, name: String, segmentLengthM: Float = DEFAULT_SEGMENT_LENGTH_M): RouteProfile? {
        val points = parseTrackPoints(xml)
        if (points.size < 2) return null
        val resampled = resample(points, segmentLengthM)
        if (resampled.size < 2) return null
        val grades = (0 until resampled.size - 1).map { i ->
            val riseM = resampled[i + 1].second - resampled[i].second
            riseM / segmentLengthM * 100f
        }
        return RouteProfile(name, segmentLengthM, grades)
    }

    private data class RawPoint(val lat: Double, val lon: Double, val ele: Float)

    private fun parseTrackPoints(xml: String): List<RawPoint> {
        val points = mutableListOf<RawPoint>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var lat = 0.0
        var lon = 0.0
        var insideTrkpt = false
        var insideEle = false
        val eleText = StringBuilder()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "trkpt" -> {
                        insideTrkpt = true
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    }
                    "ele" -> if (insideTrkpt) {
                        insideEle = true
                        eleText.clear()
                    }
                }
                XmlPullParser.TEXT -> if (insideEle) eleText.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "ele" -> insideEle = false
                    "trkpt" -> {
                        points += RawPoint(lat, lon, eleText.toString().trim().toFloatOrNull() ?: 0f)
                        insideTrkpt = false
                    }
                }
            }
            eventType = parser.next()
        }
        return points
    }

    /** Haversine great-circle distance in meters — accurate enough at the scale of a ride route;
     *  no need for anything more precise than that over a few km. */
    private fun distanceM(a: RawPoint, b: RawPoint): Double {
        val lat1 = a.lat * PI / 180.0
        val lat2 = b.lat * PI / 180.0
        val dLat = (b.lat - a.lat) * PI / 180.0
        val dLon = (b.lon - a.lon) * PI / 180.0
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Walks the raw points' cumulative distance and linearly interpolates elevation at every
     *  multiple of [segmentLengthM] — this resampling IS the noise smoothing (each resampled
     *  point already averages over a real segment, no separate moving-average pass needed).
     *  Returns (cumulativeDistanceM, elevationM) pairs at 0, L, 2L, ... up to the route's last
     *  full segment (a final partial segment shorter than L is dropped). */
    private fun resample(points: List<RawPoint>, segmentLengthM: Float): List<Pair<Float, Float>> {
        val result = mutableListOf<Pair<Float, Float>>()
        result += 0f to points[0].ele
        var cumDist = 0.0
        var nextMark = segmentLengthM.toDouble()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val segDist = distanceM(a, b)
            if (segDist <= 0.0) continue
            while (nextMark <= cumDist + segDist) {
                val frac = ((nextMark - cumDist) / segDist).coerceIn(0.0, 1.0)
                val ele = a.ele + (b.ele - a.ele) * frac.toFloat()
                result += nextMark.toFloat() to ele
                nextMark += segmentLengthM
            }
            cumDist += segDist
        }
        return result
    }
}

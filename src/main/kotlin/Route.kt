import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import kotlin.math.*
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("strava.Route")

@Serializable
data class NominatimResult(
    val lat: String,
    val lon: String,
    val display_name: String
)

fun mapActivityTypeToTravelMode(activityType: String): String {
    return when (activityType.lowercase()) {
        "run", "walk", "hike", "trail_run" -> "walking"
        "ride", "road_ride", "mountain_bike" -> "bicycling"
        else -> "walking"
    }
}

suspend fun geocodeAddress(address: String): Pair<Double, Double>? {
    val encoded = withContext(Dispatchers.IO) {
        URLEncoder.encode(address, "UTF-8")
    }
    val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
    logger.debug("Nominatim geocode: {}", url)

    return try {
        val response = httpClient.get(url) {
            header("User-Agent", "strava-mcp-server")
            accept(ContentType.Application.Json)
        }
        if (response.status != HttpStatusCode.OK) {
            logger.warn("Nominatim request failed: {}", response.status)
            return null
        }
        val body = response.bodyAsText()
        val results = jsonConfig.decodeFromString<List<NominatimResult>>(body)
        val first = results.firstOrNull() ?: run {
            logger.warn("Nominatim returned no results for: {}", address)
            return null
        }
        val lat = first.lat.toDoubleOrNull()
        val lon = first.lon.toDoubleOrNull()
        if (lat == null || lon == null) {
            logger.warn("Nominatim returned invalid coordinates: lat={}, lon={}", first.lat, first.lon)
            return null
        }
        logger.debug("Geocoded '{}' -> ({}, {})", address, lat, lon)
        Pair(lat, lon)
    } catch (e: Exception) {
        logger.error("Nominatim geocoding error: {}", e.message)
        null
    }
}

/**
 * Generates round-trip waypoints in a loop around the start point.
 */
fun generateWaypoints(
    lat: Double,
    lng: Double,
    distanceKm: Double,
    numPoints: Int = 6,
    seed: Int? = null
): List<Pair<Double, Double>> {
    val points = numPoints.coerceAtLeast(1)
    val roadWindingFactor = 1.3
    val radiusMeters = distanceKm * 1000 / (2 * PI * roadWindingFactor)

    val random = if (seed != null) Random(seed) else Random
    val angularOffset = random.nextDouble(0.0, 2 * PI)

    // Earth radius in meters
    val earthRadius = 6_371_000.0

    return (0 until points).map { i ->
        val angle = angularOffset + (2 * PI * i / points)
        val dLat = radiusMeters * cos(angle) / earthRadius
        val dLng = radiusMeters * sin(angle) / (earthRadius * cos(Math.toRadians(lat)))
        Pair(
            lat + Math.toDegrees(dLat),
            lng + Math.toDegrees(dLng)
        )
    }
}

/**
 * Builds a Google Maps Directions URL for a round-trip route.
 */
fun buildGoogleMapsUrl(
    start: Pair<Double, Double>,
    waypoints: List<Pair<Double, Double>>,
    travelMode: String
): String {
    val origin = "${start.first},${start.second}"
    val waypointStr = waypoints.joinToString("|") { "${it.first},${it.second}" }
    val encoded = URLEncoder.encode(waypointStr, "UTF-8")
    return "https://www.google.com/maps/dir/?api=1" +
            "&origin=$origin" +
            "&destination=$origin" +
            "&waypoints=$encoded" +
            "&travelmode=$travelMode"
}

@Serializable
data class SegmentExploreResponse(val segments: List<ExplorerSegment>)

@Serializable
data class ExplorerSegment(
    val id: Long,
    val name: String,
    val climb_category: Int,
    val avg_grade: Double,
    val elev_difference: Double,
    val distance: Double,
    val points: String,
    val start_latlng: List<Double>,
    val end_latlng: List<Double>,
)

fun boundsFromCenter(lat: Double, lng: Double, radiusKm: Double): String {
    val earthRadius = 6371.0
    val dLat = Math.toDegrees(radiusKm / earthRadius)
    val dLng = Math.toDegrees(radiusKm / (earthRadius * cos(Math.toRadians(lat))))
    val swLat = lat - dLat
    val swLng = lng - dLng
    val neLat = lat + dLat
    val neLng = lng + dLng
    return "$swLat,$swLng,$neLat,$neLng"
}

fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val result = mutableListOf<Pair<Double, Double>>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var shift = 0
        var b: Int
        var value = 0
        do {
            b = encoded[index++].code - 63
            value = value or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (value and 1 != 0) (value shr 1).inv() else value shr 1
        shift = 0
        value = 0
        do {
            b = encoded[index++].code - 63
            value = value or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (value and 1 != 0) (value shr 1).inv() else value shr 1
        result.add(Pair(lat / 1e5, lng / 1e5))
    }
    return result
}

fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun orderSegmentsIntoLoop(
    segments: List<ExplorerSegment>,
    startLat: Double,
    startLng: Double,
): List<Pair<Double, Double>> {
    if (segments.isEmpty()) return emptyList()
    val remaining = segments.toMutableList()
    val waypoints = mutableListOf<Pair<Double, Double>>()
    var curLat = startLat
    var curLng = startLng
    while (remaining.isNotEmpty()) {
        var bestIdx = 0
        var bestDist = Double.MAX_VALUE
        var bestReversed = false
        for ((i, seg) in remaining.withIndex()) {
            val distToStart = haversineMeters(curLat, curLng, seg.start_latlng[0], seg.start_latlng[1])
            val distToEnd = haversineMeters(curLat, curLng, seg.end_latlng[0], seg.end_latlng[1])
            if (distToStart < bestDist) {
                bestDist = distToStart
                bestIdx = i
                bestReversed = false
            }
            if (distToEnd < bestDist) {
                bestDist = distToEnd
                bestIdx = i
                bestReversed = true
            }
        }
        val seg = remaining.removeAt(bestIdx)
        if (bestReversed) {
            waypoints.add(Pair(seg.end_latlng[0], seg.end_latlng[1]))
            waypoints.add(Pair(seg.start_latlng[0], seg.start_latlng[1]))
            curLat = seg.start_latlng[0]
            curLng = seg.start_latlng[1]
        } else {
            waypoints.add(Pair(seg.start_latlng[0], seg.start_latlng[1]))
            waypoints.add(Pair(seg.end_latlng[0], seg.end_latlng[1]))
            curLat = seg.end_latlng[0]
            curLng = seg.end_latlng[1]
        }
    }
    return waypoints
}

data class ExploreResult(
    val segments: List<ExplorerSegment> = emptyList(),
    val error: String? = null,
)

suspend fun exploreSegments(
    lat: Double,
    lng: Double,
    radiusKm: Double,
    activityType: String,
): ExploreResult {
    val bounds = boundsFromCenter(lat, lng, radiusKm)
    val stravaType =
        when (activityType.lowercase()) {
            "ride", "road_ride", "mountain_bike" -> "riding"
            else -> "running"
        }
    val url = "https://www.strava.com/api/v3/segments/explore?bounds=$bounds&activity_type=$stravaType"
    logger.debug("Segments explore: {}", url)

    return try {
        val response = httpClient.get(url) {
            header("Authorization", "Bearer ${Auth.TOKEN}")
            accept(ContentType.Application.Json)
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            logger.warn("Segments explore failed: {} - {}", response.status, body)
            return ExploreResult(error = "Strava API error ${response.status}: $body")
        }
        val segments = jsonConfig.decodeFromString<SegmentExploreResponse>(body).segments
        ExploreResult(segments = segments)
    } catch (e: Exception) {
        logger.error("Segments explore exception: {}", e.message)
        ExploreResult(error = "Request failed: ${e.message}")
    }
}

fun climbCategoryLabel(cat: Int): String =
    when (cat) {
        0 -> "NC"
        1 -> "Cat 4"
        2 -> "Cat 3"
        3 -> "Cat 2"
        4 -> "Cat 1"
        5 -> "HC"
        else -> "NC"
    }

import kotlinx.serialization.json.Json
import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertContains

private val json = Json { ignoreUnknownKeys = true }

class RouteTest {
    @Test
    fun `mapActivityTypeToTravelMode maps run to walking`() {
        assertEquals("walking", mapActivityTypeToTravelMode("run"))
    }

    @Test
    fun `mapActivityTypeToTravelMode maps walk to walking`() {
        assertEquals("walking", mapActivityTypeToTravelMode("walk"))
    }

    @Test
    fun `mapActivityTypeToTravelMode maps hike to walking`() {
        assertEquals("walking", mapActivityTypeToTravelMode("hike"))
    }

    @Test
    fun `mapActivityTypeToTravelMode maps trail_run to walking`() {
        assertEquals("walking", mapActivityTypeToTravelMode("trail_run"))
    }

    @Test
    fun `mapActivityTypeToTravelMode maps ride to bicycling`() {
        assertEquals("bicycling", mapActivityTypeToTravelMode("ride"))
    }

    @Test
    fun `mapActivityTypeToTravelMode maps road_ride to bicycling`() {
        assertEquals("bicycling", mapActivityTypeToTravelMode("road_ride"))
    }

    @Test
    fun `mapActivityTypeToTravelMode maps mountain_bike to bicycling`() {
        assertEquals("bicycling", mapActivityTypeToTravelMode("mountain_bike"))
    }

    @Test
    fun `mapActivityTypeToTravelMode defaults to walking for unknown type`() {
        assertEquals("walking", mapActivityTypeToTravelMode("unknown"))
    }

    @Test
    fun `mapActivityTypeToTravelMode is case-insensitive`() {
        assertEquals("walking", mapActivityTypeToTravelMode("RUN"))
        assertEquals("walking", mapActivityTypeToTravelMode("Hike"))
        assertEquals("bicycling", mapActivityTypeToTravelMode("ROAD_RIDE"))
    }

    @Test
    fun `generateWaypoints returns correct number of points`() {
        val waypoints = generateWaypoints(48.8566, 2.3522, 5.0, numPoints = 4, seed = 42)
        assertEquals(4, waypoints.size)
    }

    @Test
    fun `generateWaypoints returns 1 point when numPoints is 1`() {
        val waypoints = generateWaypoints(48.8566, 2.3522, 5.0, numPoints = 1, seed = 42)
        assertEquals(1, waypoints.size)
    }

    @Test
    fun `generateWaypoints coerces numPoints to at least 1`() {
        val waypoints = generateWaypoints(48.8566, 2.3522, 5.0, numPoints = 0, seed = 42)
        assertEquals(1, waypoints.size)
    }

    @Test
    fun `generateWaypoints produces points at roughly correct radius`() {
        val lat = 48.8566
        val lng = 2.3522
        val distanceKm = 5.0
        val waypoints = generateWaypoints(lat, lng, distanceKm, numPoints = 4, seed = 42)

        val roadWindingFactor = 1.3
        val expectedRadiusMeters = distanceKm * 1000 / (2 * PI * roadWindingFactor)

        for ((wpLat, wpLng) in waypoints) {
            val dLat = Math.toRadians(wpLat - lat)
            val dLng = Math.toRadians(wpLng - lng)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(wpLat)) * sin(dLng / 2).pow(2)
            val distMeters = 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
            // Allow 5% tolerance for the simplified projection
            assertTrue(
                abs(distMeters - expectedRadiusMeters) / expectedRadiusMeters < 0.05,
                "Waypoint distance $distMeters should be close to expected radius $expectedRadiusMeters"
            )
        }
    }

    @Test
    fun `generateWaypoints with different seeds produce different routes`() {
        val wp1 = generateWaypoints(48.8566, 2.3522, 5.0, numPoints = 4, seed = 1)
        val wp2 = generateWaypoints(48.8566, 2.3522, 5.0, numPoints = 4, seed = 2)
        assertNotEquals(wp1, wp2)
    }

    @Test
    fun `generateWaypoints with same seed produce identical routes`() {
        val wp1 = generateWaypoints(48.8566, 2.3522, 5.0, numPoints = 4, seed = 42)
        val wp2 = generateWaypoints(48.8566, 2.3522, 5.0, numPoints = 4, seed = 42)
        assertEquals(wp1, wp2)
    }

    @Test
    fun `generateWaypoints handles very short distance`() {
        val waypoints = generateWaypoints(48.8566, 2.3522, 0.5, numPoints = 3, seed = 42)
        assertEquals(3, waypoints.size)
        for ((wpLat, wpLng) in waypoints) {
            assertTrue(abs(wpLat - 48.8566) < 0.01, "Waypoint should be close to start for short distance")
            assertTrue(abs(wpLng - 2.3522) < 0.01, "Waypoint should be close to start for short distance")
        }
    }

    @Test
    fun `generateWaypoints handles long distance`() {
        val waypoints = generateWaypoints(48.8566, 2.3522, 42.0, numPoints = 6, seed = 42)
        assertEquals(6, waypoints.size)
        // Waypoints should be farther from start
        val maxDist = waypoints.maxOf { (wpLat, _) -> abs(wpLat - 48.8566) }
        assertTrue(maxDist > 0.01, "Waypoints should be spread out for long distance")
    }

    // --- buildGoogleMapsUrl ---

    @Test
    fun `buildGoogleMapsUrl has correct origin and destination`() {
        val start = Pair(48.8566, 2.3522)
        val waypoints = listOf(Pair(48.86, 2.35), Pair(48.85, 2.36))
        val url = buildGoogleMapsUrl(start, waypoints, "walking")

        assertContains(url, "origin=48.8566,2.3522")
        assertContains(url, "destination=48.8566,2.3522")
    }

    @Test
    fun `buildGoogleMapsUrl origin equals destination for round trip`() {
        val start = Pair(40.7829, -73.9654)
        val url = buildGoogleMapsUrl(start, listOf(Pair(40.79, -73.96)), "walking")

        val originMatch = Regex("origin=([^&]+)").find(url)?.groupValues?.get(1)
        val destMatch = Regex("destination=([^&]+)").find(url)?.groupValues?.get(1)
        assertEquals(originMatch, destMatch)
    }

    @Test
    fun `buildGoogleMapsUrl includes travel mode`() {
        val url = buildGoogleMapsUrl(Pair(48.8566, 2.3522), listOf(Pair(48.86, 2.35)), "bicycling")
        assertContains(url, "travelmode=bicycling")
    }

    @Test
    fun `buildGoogleMapsUrl includes waypoints`() {
        val waypoints = listOf(Pair(48.86, 2.35), Pair(48.85, 2.36))
        val url = buildGoogleMapsUrl(Pair(48.8566, 2.3522), waypoints, "walking")
        assertContains(url, "waypoints=")
        assertContains(url, "48.86")
        assertContains(url, "48.85")
    }

    @Test
    fun `buildGoogleMapsUrl starts with correct base URL`() {
        val url = buildGoogleMapsUrl(Pair(48.8566, 2.3522), listOf(Pair(48.86, 2.35)), "walking")
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
    }

    @Test
    fun `NominatimResult deserializes correctly`() {
        val jsonStr = """[{"lat": "48.8566", "lon": "2.3522", "display_name": "Paris, France"}]"""
        val results = json.decodeFromString<List<NominatimResult>>(jsonStr)
        assertEquals(1, results.size)
        assertEquals("48.8566", results[0].lat)
        assertEquals("2.3522", results[0].lon)
        assertEquals("Paris, France", results[0].display_name)
    }

    @Test
    fun `NominatimResult handles empty array`() {
        val jsonStr = "[]"
        val results = json.decodeFromString<List<NominatimResult>>(jsonStr)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `NominatimResult ignores extra fields`() {
        val jsonStr = """[{"lat": "40.7128", "lon": "-74.0060", "display_name": "New York", "importance": 0.9, "type": "city"}]"""
        val results = json.decodeFromString<List<NominatimResult>>(jsonStr)
        assertEquals(1, results.size)
        assertEquals("40.7128", results[0].lat)
    }

    @Test
    fun `decodePolyline decodes canonical Google example`() {
        // Google's example: "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        // Expected: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].first, 0.001)
        assertEquals(-120.2, points[0].second, 0.001)
        assertEquals(40.7, points[1].first, 0.001)
        assertEquals(-120.95, points[1].second, 0.001)
        assertEquals(43.252, points[2].first, 0.001)
        assertEquals(-126.453, points[2].second, 0.001)
    }

    @Test
    fun `decodePolyline returns empty for empty string`() {
        assertEquals(emptyList(), decodePolyline(""))
    }

    @Test
    fun `decodePolyline single point`() {
        // Encode (0.0, 0.0) -> "??"
        val points = decodePolyline("??")
        assertEquals(1, points.size)
        assertEquals(0.0, points[0].first, 0.001)
        assertEquals(0.0, points[0].second, 0.001)
    }

    // --- boundsFromCenter ---

    @Test
    fun `boundsFromCenter produces correct format`() {
        val bounds = boundsFromCenter(48.8566, 2.3522, 5.0)
        val parts = bounds.split(",")
        assertEquals(4, parts.size)
        // All parts should be valid doubles
        parts.forEach { assertNotNull(it.toDoubleOrNull(), "Part '$it' should be a valid double") }
    }

    @Test
    fun `boundsFromCenter sw is less than ne`() {
        val bounds = boundsFromCenter(48.8566, 2.3522, 5.0)
        val (swLat, swLng, neLat, neLng) = bounds.split(",").map { it.toDouble() }
        assertTrue(swLat < neLat, "SW lat should be less than NE lat")
        assertTrue(swLng < neLng, "SW lng should be less than NE lng")
    }

    @Test
    fun `boundsFromCenter is symmetric around center`() {
        val lat = 48.8566
        val lng = 2.3522
        val bounds = boundsFromCenter(lat, lng, 5.0)
        val (swLat, swLng, neLat, neLng) = bounds.split(",").map { it.toDouble() }
        assertEquals(lat, (swLat + neLat) / 2, 0.0001)
        assertEquals(lng, (swLng + neLng) / 2, 0.0001)
    }

    @Test
    fun `boundsFromCenter span scales with radius`() {
        val bounds5 = boundsFromCenter(48.8566, 2.3522, 5.0).split(",").map { it.toDouble() }
        val bounds10 = boundsFromCenter(48.8566, 2.3522, 10.0).split(",").map { it.toDouble() }
        val span5 = bounds5[2] - bounds5[0]
        val span10 = bounds10[2] - bounds10[0]
        assertEquals(span10, span5 * 2, 0.0001)
    }

    @Test
    fun `haversineMeters same point is zero`() {
        assertEquals(0.0, haversineMeters(48.8566, 2.3522, 48.8566, 2.3522), 0.001)
    }

    @Test
    fun `haversineMeters Paris to London approximately 344km`() {
        val dist = haversineMeters(48.8566, 2.3522, 51.5074, -0.1278)
        assertEquals(344_000.0, dist, 2_000.0) // ~344km ±2km
    }

    @Test
    fun `haversineMeters is symmetric`() {
        val d1 = haversineMeters(48.8566, 2.3522, 51.5074, -0.1278)
        val d2 = haversineMeters(51.5074, -0.1278, 48.8566, 2.3522)
        assertEquals(d1, d2, 0.001)
    }

    @Test
    fun `orderSegmentsIntoLoop empty returns empty`() {
        assertEquals(emptyList(), orderSegmentsIntoLoop(emptyList(), 48.0, 2.0))
    }

    @Test
    fun `orderSegmentsIntoLoop single segment returns two waypoints`() {
        val seg = ExplorerSegment(
            id = 1, name = "Test", climb_category = 0, avg_grade = 1.0,
            elev_difference = 10.0, distance = 500.0, points = "",
            start_latlng = listOf(48.86, 2.35), end_latlng = listOf(48.87, 2.36)
        )
        val waypoints = orderSegmentsIntoLoop(listOf(seg), 48.85, 2.34)
        assertEquals(2, waypoints.size)
    }

    @Test
    fun `orderSegmentsIntoLoop picks nearest first`() {
        val near = ExplorerSegment(
            id = 1, name = "Near", climb_category = 0, avg_grade = 0.0,
            elev_difference = 0.0, distance = 100.0, points = "",
            start_latlng = listOf(48.001, 2.001), end_latlng = listOf(48.002, 2.002)
        )
        val far = ExplorerSegment(
            id = 2, name = "Far", climb_category = 0, avg_grade = 0.0,
            elev_difference = 0.0, distance = 100.0, points = "",
            start_latlng = listOf(49.0, 3.0), end_latlng = listOf(49.01, 3.01)
        )
        val waypoints = orderSegmentsIntoLoop(listOf(far, near), 48.0, 2.0)
        assertEquals(4, waypoints.size)
        // First waypoint should be from the "near" segment
        assertEquals(48.001, waypoints[0].first, 0.0001)
    }

    @Test
    fun `orderSegmentsIntoLoop correct waypoint count`() {
        val segments = (1..3).map { i ->
            ExplorerSegment(
                id = i.toLong(), name = "Seg$i", climb_category = 0, avg_grade = 0.0,
                elev_difference = 0.0, distance = 100.0, points = "",
                start_latlng = listOf(48.0 + i * 0.01, 2.0), end_latlng = listOf(48.0 + i * 0.01 + 0.005, 2.005)
            )
        }
        val waypoints = orderSegmentsIntoLoop(segments, 48.0, 2.0)
        assertEquals(6, waypoints.size) // 3 segments × 2 waypoints each
    }

    @Test
    fun `SegmentExploreResponse parses single segment`() {
        val jsonStr = """{"segments":[{"id":123,"name":"Hill Climb","climb_category":2,"avg_grade":5.5,"elev_difference":100.0,"distance":2000.0,"points":"abc","start_latlng":[48.85,2.35],"end_latlng":[48.86,2.36]}]}"""
        val response = json.decodeFromString<SegmentExploreResponse>(jsonStr)
        assertEquals(1, response.segments.size)
        assertEquals("Hill Climb", response.segments[0].name)
        assertEquals(123L, response.segments[0].id)
        assertEquals(2, response.segments[0].climb_category)
        assertEquals(5.5, response.segments[0].avg_grade, 0.01)
    }

    @Test
    fun `SegmentExploreResponse parses multiple segments`() {
        val jsonStr = """{"segments":[
            {"id":1,"name":"Seg1","climb_category":0,"avg_grade":1.0,"elev_difference":10.0,"distance":500.0,"points":"a","start_latlng":[48.0,2.0],"end_latlng":[48.01,2.01]},
            {"id":2,"name":"Seg2","climb_category":3,"avg_grade":7.0,"elev_difference":200.0,"distance":3000.0,"points":"b","start_latlng":[48.1,2.1],"end_latlng":[48.11,2.11]}
        ]}"""
        val response = json.decodeFromString<SegmentExploreResponse>(jsonStr)
        assertEquals(2, response.segments.size)
        assertEquals("Seg1", response.segments[0].name)
        assertEquals("Seg2", response.segments[1].name)
    }

    @Test
    fun `SegmentExploreResponse parses empty segments`() {
        val jsonStr = """{"segments":[]}"""
        val response = json.decodeFromString<SegmentExploreResponse>(jsonStr)
        assertTrue(response.segments.isEmpty())
    }

    @Test
    fun `climbCategoryLabel maps correctly`() {
        assertEquals("NC", climbCategoryLabel(0))
        assertEquals("Cat 4", climbCategoryLabel(1))
        assertEquals("Cat 3", climbCategoryLabel(2))
        assertEquals("Cat 2", climbCategoryLabel(3))
        assertEquals("Cat 1", climbCategoryLabel(4))
        assertEquals("HC", climbCategoryLabel(5))
        assertEquals("NC", climbCategoryLabel(-1))
    }
}

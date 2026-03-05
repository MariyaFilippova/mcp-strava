import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

class ActivityTest {

    private fun createTestActivity(
        name: String = "Morning Run",
        distance: Double = 5000.0,
        movingTime: Int = 1800,
        elapsedTime: Int = 2000,
        elevationGain: Double = 50.0,
        type: String = "Run",
        sportType: String = "Run",
        id: Long = 12345L,
        averageHeartrate: Double? = 150.0,
        maxHeartrate: Double? = 175.0
    ) = Activity(
        name = name,
        distance = distance,
        moving_time = movingTime,
        elapsed_time = elapsedTime,
        total_elevation_gain = elevationGain,
        type = type,
        sport_type = sportType,
        id = id,
        start_date = "2024-01-15T08:00:00Z",
        start_date_local = "2024-01-15T10:00:00Z",
        location_country = "United States",
        achievement_count = 3,
        kudos_count = 10,
        average_speed = 2.78,
        max_speed = 4.0,
        has_heartrate = true,
        average_heartrate = averageHeartrate,
        max_heartrate = maxHeartrate,
        elev_high = 100.0,
        elev_low = 50.0
    )

    @Test
    fun `Activity parsing from JSON works correctly`() {
        val json = """
            {
                "name": "Evening Ride",
                "distance": 25000.5,
                "moving_time": 3600,
                "elapsed_time": 4000,
                "total_elevation_gain": 200.0,
                "type": "Ride",
                "sport_type": "Ride",
                "id": 99999,
                "start_date": "2024-02-01T18:00:00Z",
                "start_date_local": "2024-02-01T20:00:00Z",
                "location_country": "Germany",
                "achievement_count": 5,
                "kudos_count": 20,
                "average_speed": 6.94,
                "max_speed": 12.0,
                "has_heartrate": true,
                "average_heartrate": 140.0,
                "max_heartrate": 165.0,
                "elev_high": 300.0,
                "elev_low": 100.0
            }
        """.trimIndent()

        val activity = jsonConfig.decodeFromString<Activity>(json)

        assertEquals("Evening Ride", activity.name)
        assertEquals(25000.5, activity.distance)
        assertEquals(3600, activity.moving_time)
        assertEquals("Ride", activity.sport_type)
        assertEquals(99999L, activity.id)
        assertEquals(140.0, activity.average_heartrate)
    }

    @Test
    fun `Activity parsing handles missing optional fields`() {
        val json = """
            {
                "name": "Quick Walk",
                "distance": 2000.0,
                "moving_time": 1200,
                "elapsed_time": 1500,
                "total_elevation_gain": 10.0,
                "type": "Walk",
                "sport_type": "Walk",
                "id": 11111,
                "start_date": null,
                "start_date_local": null,
                "location_country": null,
                "achievement_count": 0,
                "kudos_count": 0,
                "average_speed": 1.67,
                "max_speed": 2.0,
                "has_heartrate": false,
                "elev_high": 50.0,
                "elev_low": 45.0
            }
        """.trimIndent()

        val activity = jsonConfig.decodeFromString<Activity>(json)

        assertEquals("Quick Walk", activity.name)
        assertEquals(null, activity.average_heartrate)
        assertEquals(null, activity.max_heartrate)
        assertEquals(null, activity.start_date)
    }

    @Test
    fun `getAllInfo formats activity details correctly`() {
        val activity = createTestActivity()
        val info = activity.getAllInfo()

        assertContains(info, "Morning Run")
        assertContains(info, "Run")
        assertContains(info, "5000") // distance value (locale-independent)
        assertContains(info, "meters")
        assertContains(info, "30 minutes")
        assertContains(info, "150")
        assertContains(info, "175")
    }

    @Test
    fun `getAllInfo handles missing heartrate`() {
        val activity = createTestActivity(averageHeartrate = null, maxHeartrate = null)
        val info = activity.getAllInfo()

        assertContains(info, "Average Heartrate: N/A")
        assertContains(info, "Max Heartrate: N/A")
    }

    @Test
    fun `calculateSummary computes totals correctly`() {
        val activities = listOf(
            createTestActivity(distance = 5000.0, movingTime = 1800, elevationGain = 50.0, sportType = "Run"),
            createTestActivity(distance = 10000.0, movingTime = 3600, elevationGain = 100.0, sportType = "Run"),
            createTestActivity(distance = 25000.0, movingTime = 3600, elevationGain = 200.0, sportType = "Ride")
        )

        val summary = calculateSummary(activities)

        assertEquals(3, summary.activityCount)
        assertEquals(40000.0, summary.totalDistance)
        assertEquals(9000, summary.totalMovingTime)
        assertEquals(350.0, summary.totalElevation)
    }

    @Test
    fun `calculateSummary groups by sport type`() {
        val activities = listOf(
            createTestActivity(distance = 5000.0, movingTime = 1800, sportType = "Run"),
            createTestActivity(distance = 6000.0, movingTime = 2000, sportType = "Run"),
            createTestActivity(distance = 25000.0, movingTime = 3600, sportType = "Ride")
        )

        val summary = calculateSummary(activities)

        assertEquals(2, summary.byType.size)
        assertEquals(2, summary.byType["Run"]?.count)
        assertEquals(11000.0, summary.byType["Run"]?.distance)
        assertEquals(1, summary.byType["Ride"]?.count)
        assertEquals(25000.0, summary.byType["Ride"]?.distance)
    }

    @Test
    fun `calculateSummary handles empty list`() {
        val summary = calculateSummary(emptyList())

        assertEquals(0, summary.activityCount)
        assertEquals(0.0, summary.totalDistance)
        assertEquals(0, summary.totalMovingTime)
        assertEquals(0.0, summary.totalElevation)
        assertTrue(summary.byType.isEmpty())
    }

    @Test
    fun `ActivitySummary format produces readable output`() {
        val activities = listOf(
            createTestActivity(distance = 10000.0, movingTime = 3600, elevationGain = 100.0, sportType = "Run")
        )
        val summary = calculateSummary(activities)
        val formatted = summary.format()

        assertContains(formatted, "Activity Summary")
        assertContains(formatted, "Total Activities: 1")
        assertContains(formatted, "10") // distance in km (locale-independent)
        assertContains(formatted, "km")
        assertContains(formatted, "1h 0m")
        assertContains(formatted, "100 m")
        assertContains(formatted, "Run: 1 activities")
    }

    @Test
    fun `ActivitySummary format handles multiple hours correctly`() {
        val activities = listOf(
            createTestActivity(movingTime = 7380, sportType = "Run") // 2h 3m
        )
        val summary = calculateSummary(activities)
        val formatted = summary.format()

        assertContains(formatted, "2h 3m")
    }

    @Test
    fun `Lap parsing from JSON works correctly`() {
        val json = """
            {
                "name": "Lap 1",
                "elapsed_time": 360,
                "moving_time": 350,
                "distance": 1000.0,
                "average_speed": 2.86,
                "max_speed": 3.5,
                "average_heartrate": 155.0,
                "max_heartrate": 170.0,
                "lap_index": 1,
                "total_elevation_gain": 15.0
            }
        """.trimIndent()

        val lap = jsonConfig.decodeFromString<Lap>(json)

        assertEquals("Lap 1", lap.name)
        assertEquals(350, lap.moving_time)
        assertEquals(1000.0, lap.distance)
        assertEquals(1, lap.lap_index)
        assertEquals(155.0, lap.average_heartrate)
        assertEquals(15.0, lap.total_elevation_gain)
    }

    @Test
    fun `Lap parsing handles missing heartrate`() {
        val json = """
            {
                "name": "Lap 2",
                "elapsed_time": 400,
                "moving_time": 390,
                "distance": 1200.0,
                "average_speed": 3.08,
                "max_speed": 4.0,
                "lap_index": 2,
                "total_elevation_gain": 20.0
            }
        """.trimIndent()

        val lap = jsonConfig.decodeFromString<Lap>(json)

        assertEquals(null, lap.average_heartrate)
        assertEquals(null, lap.max_heartrate)
    }

    @Test
    fun `Lap format produces readable output`() {
        val lap = Lap(
            name = "Lap 1",
            elapsed_time = 360,
            moving_time = 305,
            distance = 1000.0,
            average_speed = 3.28,
            max_speed = 4.0,
            average_heartrate = 155.0,
            max_heartrate = 170.0,
            lap_index = 1,
            total_elevation_gain = 15.0
        )
        val formatted = lap.format()

        assertContains(formatted, "Lap 1")
        assertContains(formatted, "km")
        assertContains(formatted, "5m 5s")
        assertContains(formatted, "Avg HR: 155 bpm")
        assertContains(formatted, "Max HR: 170 bpm")
        assertContains(formatted, "Elevation Gain:")
    }

    @Test
    fun `Lap format omits heartrate when missing`() {
        val lap = Lap(
            name = "Lap 2",
            elapsed_time = 400,
            moving_time = 390,
            distance = 1200.0,
            average_speed = 3.08,
            max_speed = 4.0,
            average_heartrate = null,
            max_heartrate = null,
            lap_index = 2,
            total_elevation_gain = 20.0
        )
        val formatted = lap.format()

        assertTrue(!formatted.contains("Avg HR"))
        assertTrue(!formatted.contains("Max HR"))
    }

    @Test
    fun `Activity parsing by ID returns correct fields`() {
        val json = """
            {
                "name": "Afternoon Run",
                "distance": 8500.0,
                "moving_time": 2700,
                "elapsed_time": 3000,
                "total_elevation_gain": 75.0,
                "type": "Run",
                "sport_type": "Run",
                "id": 123456789,
                "start_date": "2024-06-15T14:00:00Z",
                "start_date_local": "2024-06-15T16:00:00Z",
                "location_country": "France",
                "achievement_count": 2,
                "kudos_count": 5,
                "average_speed": 3.15,
                "max_speed": 4.5,
                "has_heartrate": true,
                "average_heartrate": 160.0,
                "max_heartrate": 180.0,
                "elev_high": 250.0,
                "elev_low": 175.0
            }
        """.trimIndent()

        val activity = jsonConfig.decodeFromString<Activity>(json)

        assertEquals(123456789L, activity.id)
        assertEquals("Afternoon Run", activity.name)
        assertEquals(8500.0, activity.distance)
        assertEquals("France", activity.location_country)
        assertEquals(160.0, activity.average_heartrate)
    }

    @Test
    fun `Lap list parsing from JSON array works`() {
        val json = """
            [
                {
                    "name": "Lap 1",
                    "elapsed_time": 300,
                    "moving_time": 290,
                    "distance": 1000.0,
                    "average_speed": 3.45,
                    "max_speed": 4.0,
                    "average_heartrate": 150.0,
                    "max_heartrate": 165.0,
                    "lap_index": 1,
                    "total_elevation_gain": 10.0
                },
                {
                    "name": "Lap 2",
                    "elapsed_time": 310,
                    "moving_time": 300,
                    "distance": 1000.0,
                    "average_speed": 3.33,
                    "max_speed": 3.8,
                    "average_heartrate": 160.0,
                    "max_heartrate": 175.0,
                    "lap_index": 2,
                    "total_elevation_gain": 12.0
                }
            ]
        """.trimIndent()

        val laps = jsonConfig.decodeFromString<List<Lap>>(json)

        assertEquals(2, laps.size)
        assertEquals("Lap 1", laps[0].name)
        assertEquals("Lap 2", laps[1].name)
        assertEquals(150.0, laps[0].average_heartrate)
        assertEquals(160.0, laps[1].average_heartrate)
    }

    @Test
    fun `month timestamp calculation is correct for January`() {
        val startOfJan2025 = java.time.LocalDate.of(2025, 1, 1)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toEpochSecond()

        // January 1, 2025 00:00:00 UTC = 1735689600
        assertEquals(1735689600L, startOfJan2025)
    }

    @Test
    fun `month timestamp calculation is correct for February leap year`() {
        val daysInFeb2024 = java.time.YearMonth.of(2024, 2).lengthOfMonth()
        assertEquals(29, daysInFeb2024) // 2024 is a leap year

        val daysInFeb2025 = java.time.YearMonth.of(2025, 2).lengthOfMonth()
        assertEquals(28, daysInFeb2025) // 2025 is not a leap year
    }
}
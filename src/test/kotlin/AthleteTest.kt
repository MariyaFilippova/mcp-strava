import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AthleteTest {

    @Test
    fun `Athlete parsing from JSON works correctly`() {
        val json = """
            {
                "id": 12345,
                "username": "runner123",
                "resourceState": 2,
                "firstname": "John",
                "lastname": "Doe",
                "city": "San Francisco",
                "state": "California",
                "country": "United States",
                "sex": "M",
                "premium": true,
                "followerCount": 150,
                "weight": 75.5
            }
        """.trimIndent()

        val athlete = jsonConfig.decodeFromString<Athlete>(json)

        assertEquals(12345L, athlete.id)
        assertEquals("runner123", athlete.username)
        assertEquals("John", athlete.firstname)
        assertEquals("Doe", athlete.lastname)
        assertEquals("San Francisco", athlete.city)
        assertEquals(true, athlete.premium)
        assertEquals(150, athlete.followerCount)
        assertEquals(75.5, athlete.weight)
    }

    @Test
    fun `Athlete parsing handles null optional fields`() {
        val json = """
            {
                "id": 99999,
                "resourceState": 1,
                "firstname": "Jane",
                "lastname": "Smith",
                "premium": false,
                "followerCount": 50
            }
        """.trimIndent()

        val athlete = jsonConfig.decodeFromString<Athlete>(json)

        assertEquals(99999L, athlete.id)
        assertNull(athlete.username)
        assertNull(athlete.city)
        assertNull(athlete.state)
        assertNull(athlete.country)
        assertNull(athlete.weight)
    }

    @Test
    fun `getAllInfo formats athlete details correctly`() {
        val athlete = Athlete(
            id = 12345,
            username = "runner123",
            resourceState = 2,
            firstname = "John",
            lastname = "Doe",
            city = "San Francisco",
            state = "California",
            country = "United States",
            sex = "M",
            premium = true,
            followerCount = 150,
            weight = 75.5
        )

        val info = athlete.getAllInfo()

        assertContains(info, "runner123")
        assertContains(info, "John Doe")
        assertContains(info, "San Francisco")
        assertContains(info, "California")
        assertContains(info, "United States")
        assertContains(info, "Premium User: true")
        assertContains(info, "Followers: 150")
        assertContains(info, "75") // weight value (locale-independent)
        assertContains(info, "kg")
    }

    @Test
    fun `getAllInfo handles missing values with NA`() {
        val athlete = Athlete(
            id = 12345,
            resourceState = 1,
            firstname = "Jane",
            lastname = "Smith",
            premium = false,
            followerCount = 0
        )

        val info = athlete.getAllInfo()

        assertContains(info, "Username: N/A")
        assertContains(info, "Weight: N/A")
    }

    @Test
    fun `AthleteStats parsing from JSON works correctly`() {
        val json = """
            {
                "biggest_ride_distance": 150000.0,
                "biggest_climb_elevation_gain": 2500.0,
                "recent_ride_totals": {
                    "count": 5,
                    "distance": 200000.0,
                    "moving_time": 28800,
                    "elapsed_time": 32000,
                    "elevation_gain": 1500.0
                },
                "recent_run_totals": {
                    "count": 10,
                    "distance": 80000.0,
                    "moving_time": 28800,
                    "elapsed_time": 30000,
                    "elevation_gain": 500.0
                },
                "recent_swim_totals": {
                    "count": 0,
                    "distance": 0.0,
                    "moving_time": 0,
                    "elapsed_time": 0,
                    "elevation_gain": 0.0
                },
                "ytd_ride_totals": {
                    "count": 50,
                    "distance": 2000000.0,
                    "moving_time": 288000,
                    "elapsed_time": 300000,
                    "elevation_gain": 15000.0
                },
                "ytd_run_totals": {
                    "count": 100,
                    "distance": 800000.0,
                    "moving_time": 288000,
                    "elapsed_time": 300000,
                    "elevation_gain": 5000.0
                },
                "ytd_swim_totals": {
                    "count": 0,
                    "distance": 0.0,
                    "moving_time": 0,
                    "elapsed_time": 0,
                    "elevation_gain": 0.0
                },
                "all_ride_totals": {
                    "count": 500,
                    "distance": 20000000.0,
                    "moving_time": 2880000,
                    "elapsed_time": 3000000,
                    "elevation_gain": 150000.0
                },
                "all_run_totals": {
                    "count": 1000,
                    "distance": 8000000.0,
                    "moving_time": 2880000,
                    "elapsed_time": 3000000,
                    "elevation_gain": 50000.0
                },
                "all_swim_totals": {
                    "count": 20,
                    "distance": 40000.0,
                    "moving_time": 36000,
                    "elapsed_time": 40000,
                    "elevation_gain": 0.0
                }
            }
        """.trimIndent()

        val stats = jsonConfig.decodeFromString<AthleteStats>(json)

        assertEquals(150000.0, stats.biggest_ride_distance)
        assertEquals(2500.0, stats.biggest_climb_elevation_gain)
        assertEquals(5, stats.recent_ride_totals.count)
        assertEquals(200000.0, stats.recent_ride_totals.distance)
        assertEquals(10, stats.recent_run_totals.count)
        assertEquals(500, stats.all_ride_totals.count)
        assertEquals(1000, stats.all_run_totals.count)
    }

    @Test
    fun `AthleteStats format produces readable output`() {
        val stats = AthleteStats(
            biggest_ride_distance = 100000.0,
            biggest_climb_elevation_gain = 1500.0,
            recent_ride_totals = ActivityTotal(count = 5, distance = 150000.0, moving_time = 18000, elapsed_time = 20000, elevation_gain = 1000.0),
            recent_run_totals = ActivityTotal(count = 8, distance = 60000.0, moving_time = 21600, elapsed_time = 22000, elevation_gain = 300.0),
            recent_swim_totals = ActivityTotal(),
            ytd_ride_totals = ActivityTotal(count = 50, distance = 1500000.0, moving_time = 180000, elapsed_time = 200000, elevation_gain = 10000.0),
            ytd_run_totals = ActivityTotal(count = 80, distance = 600000.0, moving_time = 216000, elapsed_time = 220000, elevation_gain = 3000.0),
            ytd_swim_totals = ActivityTotal(),
            all_ride_totals = ActivityTotal(count = 200, distance = 6000000.0, moving_time = 720000, elapsed_time = 800000, elevation_gain = 40000.0),
            all_run_totals = ActivityTotal(count = 400, distance = 3000000.0, moving_time = 1080000, elapsed_time = 1100000, elevation_gain = 15000.0),
            all_swim_totals = ActivityTotal()
        )

        val formatted = stats.format()

        assertContains(formatted, "Athlete Statistics")
        assertContains(formatted, "Biggest Ride: 100") // locale-independent
        assertContains(formatted, "Biggest Climb: 1500 m")
        assertContains(formatted, "Recent (4 weeks):")
        assertContains(formatted, "Year to Date:")
        assertContains(formatted, "All Time:")
        assertContains(formatted, "Rides")
        assertContains(formatted, "Runs")
        assertContains(formatted, "Swims")
    }

    @Test
    fun `AthleteStats format handles zero values`() {
        val stats = AthleteStats(
            recent_ride_totals = ActivityTotal(),
            recent_run_totals = ActivityTotal(),
            recent_swim_totals = ActivityTotal(),
            ytd_ride_totals = ActivityTotal(),
            ytd_run_totals = ActivityTotal(),
            ytd_swim_totals = ActivityTotal(),
            all_ride_totals = ActivityTotal(),
            all_run_totals = ActivityTotal(),
            all_swim_totals = ActivityTotal()
        )

        val formatted = stats.format()

        assertContains(formatted, "0 activities")
        assertContains(formatted, "0h 0m")
        // Check km is present (locale-independent check for 0,00 or 0.00)
        assertTrue(formatted.contains("0,00 km") || formatted.contains("0.00 km"))
    }

    @Test
    fun `ActivityTotal default values are zero`() {
        val total = ActivityTotal()

        assertEquals(0, total.count)
        assertEquals(0.0, total.distance)
        assertEquals(0, total.moving_time)
        assertEquals(0, total.elapsed_time)
        assertEquals(0.0, total.elevation_gain)
    }
}
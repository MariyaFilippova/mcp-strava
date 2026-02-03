import kotlinx.serialization.Serializable

@Serializable
data class Athlete(
    val id: Long,
    val username: String? = null,
    val resourceState: Int,
    val firstname: String,
    val lastname: String,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val sex: String? = null,
    val premium: Boolean,
    val follower: Int? = null,
    val followerCount: Int,
    val athleteType: Int? = null,
    val ftp: Int? = null,
    val weight: Double? = null,
)

/**
 * Fetch and return the authenticated athlete from the Strava API.
 */
suspend fun getAthlete(): Athlete? {
    val url = "https://www.strava.com/api/v3/athlete"
    val response = performGetRequest(url)

    return if (response != null) {
        parseAthlete(response)
    } else {
        null
    }
}

/**
 * Parse the athlete data from JSON.
 */
private fun parseAthlete(jsonString: String): Athlete? {
    return try {
        jsonConfig.decodeFromString(jsonString)
    } catch (_: Exception) {
        null
    }
}

/**
 * Generate a string summary of the athlete's information.
 */
fun Athlete.getAllInfo(): String {
    return """
        Athlete Details:
        ----------------
        Username: ${username ?: "N/A"}
        Name: $firstname $lastname
        Resource State: $resourceState
        Location: ${city ?: "N/A"}, ${state ?: "N/A"}, ${country ?: "N/A"}
        Sex: ${sex ?: "N/A"}
        Premium User: $premium
        Followers: $followerCount
        Weight: ${weight?.let { "%.1f".format(it) } ?: "N/A"} kg
    """.trimIndent()
}

@Serializable
data class ActivityTotal(
    val count: Int = 0,
    val distance: Double = 0.0,
    val moving_time: Int = 0,
    val elapsed_time: Int = 0,
    val elevation_gain: Double = 0.0
)

@Serializable
data class AthleteStats(
    val biggest_ride_distance: Double? = null,
    val biggest_climb_elevation_gain: Double? = null,
    val recent_ride_totals: ActivityTotal = ActivityTotal(),
    val recent_run_totals: ActivityTotal = ActivityTotal(),
    val recent_swim_totals: ActivityTotal = ActivityTotal(),
    val ytd_ride_totals: ActivityTotal = ActivityTotal(),
    val ytd_run_totals: ActivityTotal = ActivityTotal(),
    val ytd_swim_totals: ActivityTotal = ActivityTotal(),
    val all_ride_totals: ActivityTotal = ActivityTotal(),
    val all_run_totals: ActivityTotal = ActivityTotal(),
    val all_swim_totals: ActivityTotal = ActivityTotal()
)

/**
 * Fetch athlete statistics from the Strava API.
 */
suspend fun getAthleteStats(athleteId: Long): AthleteStats? {
    val url = "https://www.strava.com/api/v3/athletes/$athleteId/stats"
    val response = performGetRequest(url) ?: return null
    return try {
        jsonConfig.decodeFromString<AthleteStats>(response)
    } catch (_: Exception) {
        null
    }
}

fun AthleteStats.format(): String {
    fun ActivityTotal.formatLine(label: String): String {
        val hours = moving_time / 3600
        val minutes = (moving_time % 3600) / 60
        return "$label: $count activities, ${"%.2f".format(distance / 1000)} km, ${hours}h ${minutes}m, ${"%.0f".format(elevation_gain)}m elevation"
    }

    return buildString {
        appendLine("Athlete Statistics")
        appendLine("==================")
        appendLine()
        biggest_ride_distance?.let {
            appendLine("Biggest Ride: ${"%.2f".format(it / 1000)} km")
        }
        biggest_climb_elevation_gain?.let {
            appendLine("Biggest Climb: ${"%.0f".format(it)} m")
        }
        appendLine()
        appendLine("Recent (4 weeks):")
        appendLine("-----------------")
        appendLine(recent_ride_totals.formatLine("  Rides"))
        appendLine(recent_run_totals.formatLine("  Runs"))
        appendLine(recent_swim_totals.formatLine("  Swims"))
        appendLine()
        appendLine("Year to Date:")
        appendLine("-------------")
        appendLine(ytd_ride_totals.formatLine("  Rides"))
        appendLine(ytd_run_totals.formatLine("  Runs"))
        appendLine(ytd_swim_totals.formatLine("  Swims"))
        appendLine()
        appendLine("All Time:")
        appendLine("---------")
        appendLine(all_ride_totals.formatLine("  Rides"))
        appendLine(all_run_totals.formatLine("  Runs"))
        appendLine(all_swim_totals.formatLine("  Swims"))
    }
}
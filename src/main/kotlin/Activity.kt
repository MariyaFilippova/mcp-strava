import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val activities = mutableListOf<Activity>()

val jsonConfig = Json {
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
}

val httpClient = HttpClient {
    install(ContentNegotiation)
}

@Serializable
data class Activity(
    val name: String,
    val distance: Double,
    val moving_time: Int,
    val elapsed_time: Int,
    val total_elevation_gain: Double,
    val type: String,
    val sport_type: String,
    val id: Long,
    val start_date: String,
    val start_date_local: String,
    val location_country: String,
    val achievement_count: Int,
    val kudos_count: Int,
    val average_speed: Double,
    val max_speed: Double,
    val has_heartrate: Boolean,
    val average_heartrate: Double? = null,
    val max_heartrate: Double? = null,
    val elev_high: Double,
    val elev_low: Double
)

/**
 * Fetch the last activity. If not preloaded, load all activities first.
 */
suspend fun getLastActivity(): Activity? {
    if (activities.isEmpty()) {
        fillAllActivities()
    }
    return activities.firstOrNull()
}

/**
 * Populate the activities list by fetching data from the API.
 */
private suspend fun fillAllActivities() {
    val url = "https://www.strava.com/api/v3/activities?per_page=5"
    val response = performGetRequest(url)

    if (response != null) {
        val fetchedActivities = jsonConfig.decodeFromString<List<Activity>>(response)
        activities.clear()
        activities.addAll(fetchedActivities)
    }
}

suspend fun getActivityStreams(id: Long): String? {
    val url = "https://www.strava.com/api/v3/activities/$id/streams?keys=heartrate&key_by_type=true"
    return performGetRequest(url)
}

suspend fun performGetRequest(url: String): String? {
    val response = httpClient.get(url) {
        header("Authorization", "Bearer ${Auth.TOKEN}")
        accept(ContentType.Application.Json)
    }
    if (response.status == HttpStatusCode.OK) {
        return response.bodyAsText()
    }
    return null
}

/**
 * Generate a detailed string representation of an activity instance.
 */
fun Activity.getAllInfo(): String {
    return """
        Activity Details:
        -----------------
        Name: $name
        Type: $type
        Sport Type: $sport_type
        Distance: ${"%.2f".format(distance)} meters
        Moving Time: ${moving_time / 60} minutes (${moving_time} seconds)
        Elapsed Time: ${elapsed_time / 60} minutes (${elapsed_time} seconds)
        Total Elevation Gain: $total_elevation_gain meters
        Start Date (UTC): $start_date
        Location: $location_country
        Achievements: $achievement_count
        Average Speed: ${"%.2f".format(average_speed)} m/s
        Max Speed: ${"%.2f".format(max_speed)} m/s
        Average Heartrate: ${average_heartrate ?: "N/A"} bpm
        Max Heartrate: ${max_heartrate ?: "N/A"} bpm
        Elevation (High): ${"%.2f".format(elev_high)} meters
        Elevation (Low): ${"%.2f".format(elev_low)} meters
    """.trimIndent()
}
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("strava.Activity")

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
    val start_date: String?,
    val start_date_local: String?,
    val location_country: String?,
    val achievement_count: Int,
    val kudos_count: Int,
    val average_speed: Double,
    val max_speed: Double,
    val has_heartrate: Boolean,
    val average_heartrate: Double? = null,
    val max_heartrate: Double? = null,
    val elev_high: Double,
    val elev_low: Double,
    val gear_id: String? = null
)

/**
 * Fetch the last activity. If not preloaded, load all activities first.
 */
suspend fun getLastActivity(): Activity? {
    Auth.auth()
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

/**
 * Fetch a single activity by its ID.
 */
suspend fun getActivityById(id: Long): Activity? {
    Auth.auth()
    val url = "https://www.strava.com/api/v3/activities/$id"
    val response = performGetRequest(url) ?: return null
    return try {
        jsonConfig.decodeFromString<Activity>(response)
    } catch (e: Exception) {
        logger.error("Failed to parse activity {}: {}", id, e.message)
        null
    }
}

/**
 * Search activities with optional date range and pagination.
 * @param before Unix timestamp — return activities before this time
 * @param after Unix timestamp — return activities after this time
 * @param page Page number (1-based)
 * @param perPage Number of results per page (max 200)
 */
suspend fun searchActivities(before: Long? = null, after: Long? = null, page: Int = 1, perPage: Int = 30): List<Activity> {
    Auth.auth()
    val params = mutableListOf<String>()
    params.add("page=$page")
    params.add("per_page=$perPage")
    if (before != null) params.add("before=$before")
    if (after != null) params.add("after=$after")
    val url = "https://www.strava.com/api/v3/activities?${params.joinToString("&")}"
    val response = performGetRequest(url) ?: return emptyList()
    return try {
        val activities = jsonConfig.decodeFromString<List<Activity>>(response)
        logger.debug("Search returned {} activities (page {})", activities.size, page)
        activities
    } catch (e: Exception) {
        logger.error("Failed to parse search results: {}", e.message)
        emptyList()
    }
}

/**
 * Fetch activity streams for any activity by ID with configurable stream types.
 */
suspend fun getActivityStreamsById(
    id: Long,
    streamTypes: String = "heartrate,distance,altitude,velocity_smooth,cadence,watts,time,latlng,grade_smooth"
): String? {
    Auth.auth()
    val url = "https://www.strava.com/api/v3/activities/$id/streams?keys=$streamTypes&key_by_type=true"
    return performGetRequest(url)
}

@Serializable
data class Lap(
    val name: String,
    val elapsed_time: Int,
    val moving_time: Int,
    val distance: Double,
    val average_speed: Double,
    val max_speed: Double,
    val average_heartrate: Double? = null,
    val max_heartrate: Double? = null,
    val lap_index: Int,
    val total_elevation_gain: Double
) {
    fun format(): String {
        val minutes = moving_time / 60
        val seconds = moving_time % 60
        return buildString {
            append("Lap ${lap_index}: $name")
            appendLine()
            append("  Distance: ${"%.2f".format(distance / 1000)} km")
            appendLine()
            append("  Moving Time: ${minutes}m ${seconds}s")
            appendLine()
            append("  Avg Speed: ${"%.2f".format(average_speed)} m/s")
            appendLine()
            append("  Max Speed: ${"%.2f".format(max_speed)} m/s")
            appendLine()
            append("  Elevation Gain: ${"%.1f".format(total_elevation_gain)} m")
            if (average_heartrate != null) {
                appendLine()
                append("  Avg HR: ${"%.0f".format(average_heartrate)} bpm")
            }
            if (max_heartrate != null) {
                appendLine()
                append("  Max HR: ${"%.0f".format(max_heartrate)} bpm")
            }
        }
    }
}

@Serializable
data class Gear(
    val id: String,
    val name: String,
    val brand_name: String? = null,
    val model_name: String? = null,
    val distance: Double = 0.0,
    val primary: Boolean = false,
    val description: String? = null,
    val weight: Double? = null
) {
    fun format(): String {
        return buildString {
            appendLine("Gear: $name")
            if (brand_name != null || model_name != null) {
                appendLine("Brand/Model: ${brand_name ?: "N/A"} / ${model_name ?: "N/A"}")
            }
            appendLine("Total Distance: ${"%.1f".format(distance / 1000)} km")
            if (weight != null) {
                appendLine("Weight: ${"%.0f".format(weight)} g")
            }
            if (primary) {
                appendLine("Primary: Yes")
            }
            if (!description.isNullOrBlank()) {
                appendLine("Description: $description")
            }
        }.trimEnd()
    }
}

/**
 * Fetch gear details by ID.
 */
suspend fun getGearById(id: String): Gear? {
    Auth.auth()
    val url = "https://www.strava.com/api/v3/gear/$id"
    val response = performGetRequest(url) ?: return null
    return try {
        jsonConfig.decodeFromString<Gear>(response)
    } catch (e: Exception) {
        logger.error("Failed to parse gear {}: {}", id, e.message)
        null
    }
}

/**
 * Fetch lap data for an activity.
 */
suspend fun getActivityLaps(id: Long): List<Lap> {
    Auth.auth()
    val url = "https://www.strava.com/api/v3/activities/$id/laps"
    val response = performGetRequest(url) ?: return emptyList()
    return try {
        val laps = jsonConfig.decodeFromString<List<Lap>>(response)
        logger.debug("Fetched {} laps for activity {}", laps.size, id)
        laps
    } catch (e: Exception) {
        logger.error("Failed to parse laps for activity {}: {}", id, e.message)
        emptyList()
    }
}

/**
 * Fetch recent activities with configurable count.
 */
suspend fun getRecentActivities(count: Int = 10): List<Activity> {
    Auth.auth()
    val url = "https://www.strava.com/api/v3/activities?per_page=$count"
    val response = performGetRequest(url) ?: return emptyList()
    return try {
        val activities = jsonConfig.decodeFromString<List<Activity>>(response)
        logger.debug("Fetched {} activities", activities.size)
        activities
    } catch (e: Exception) {
        logger.error("Failed to parse activities: {}", e.message)
        emptyList()
    }
}

/**
 * Fetch activities filtered by sport type.
 */
suspend fun getActivitiesByType(sportType: String, count: Int = 10): List<Activity> {
    val allActivities = getRecentActivities(count * 3) // fetch more to filter
    return allActivities.filter {
        it.sport_type.equals(sportType, ignoreCase = true) ||
        it.type.equals(sportType, ignoreCase = true)
    }.take(count)
}

/**
 * Get activities for a specific month and year.
 * @param year The year (e.g., 2025)
 * @param month The month (1-12)
 */
suspend fun getActivitiesForMonth(year: Int, month: Int): List<Activity> {
    val startOfMonth = java.time.LocalDate.of(year, month, 1)
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toEpochSecond()
    val endOfMonth = startOfMonth + (startOfMonth.let {
        java.time.YearMonth.of(year, month).lengthOfMonth() * 24 * 60 * 60L
    })
    logger.debug("Fetching activities for {}/{} (timestamps: {} to {})", month, year, startOfMonth, endOfMonth)
    return getActivitiesInRange(startOfMonth, endOfMonth)
}

/**
 * Get activities within a date range (for weekly/monthly summaries).
 * @param afterTimestamp Unix timestamp for start date
 * @param beforeTimestamp Unix timestamp for end date (optional)
 */
suspend fun getActivitiesInRange(afterTimestamp: Long, beforeTimestamp: Long? = null): List<Activity> {
    Auth.auth()
    var url = "https://www.strava.com/api/v3/activities?per_page=100&after=$afterTimestamp"
    if (beforeTimestamp != null) {
        url += "&before=$beforeTimestamp"
    }
    val response = performGetRequest(url) ?: return emptyList()
    return try {
        val activities = jsonConfig.decodeFromString<List<Activity>>(response)
        logger.debug("Fetched {} activities in date range", activities.size)
        activities
    } catch (e: Exception) {
        logger.error("Failed to parse activities in range: {}", e.message)
        emptyList()
    }
}

/**
 * Calculate summary statistics for a list of activities.
 */
fun calculateSummary(activities: List<Activity>): ActivitySummary {
    val totalDistance = activities.sumOf { it.distance }
    val totalMovingTime = activities.sumOf { it.moving_time }
    val totalElevation = activities.sumOf { it.total_elevation_gain }
    val byType = activities.groupBy { it.sport_type }

    return ActivitySummary(
        activityCount = activities.size,
        totalDistance = totalDistance,
        totalMovingTime = totalMovingTime,
        totalElevation = totalElevation,
        byType = byType.mapValues { (_, acts) ->
            TypeSummary(
                count = acts.size,
                distance = acts.sumOf { it.distance },
                movingTime = acts.sumOf { it.moving_time },
                elevation = acts.sumOf { it.total_elevation_gain }
            )
        }
    )
}

data class TypeSummary(
    val count: Int,
    val distance: Double,
    val movingTime: Int,
    val elevation: Double
)

data class ActivitySummary(
    val activityCount: Int,
    val totalDistance: Double,
    val totalMovingTime: Int,
    val totalElevation: Double,
    val byType: Map<String, TypeSummary>
) {
    fun format(): String {
        val hours = totalMovingTime / 3600
        val minutes = (totalMovingTime % 3600) / 60

        val sb = StringBuilder()
        sb.appendLine("Activity Summary")
        sb.appendLine("================")
        sb.appendLine("Total Activities: $activityCount")
        sb.appendLine("Total Distance: ${"%.2f".format(totalDistance / 1000)} km")
        sb.appendLine("Total Moving Time: ${hours}h ${minutes}m")
        sb.appendLine("Total Elevation Gain: ${"%.0f".format(totalElevation)} m")
        sb.appendLine()
        sb.appendLine("By Activity Type:")
        sb.appendLine("-----------------")
        byType.forEach { (type, summary) ->
            val typeHours = summary.movingTime / 3600
            val typeMinutes = (summary.movingTime % 3600) / 60
            sb.appendLine("$type: ${summary.count} activities, ${"%.2f".format(summary.distance / 1000)} km, ${typeHours}h ${typeMinutes}m")
        }
        return sb.toString()
    }
}

suspend fun performGetRequest(url: String): String? {
    logger.debug("GET {}", url)
    val response = httpClient.get(url) {
        header("Authorization", "Bearer ${Auth.TOKEN}")
        accept(ContentType.Application.Json)
    }
    if (response.status == HttpStatusCode.OK) {
        logger.debug("Request successful: {}", response.status)
        return response.bodyAsText()
    }
    logger.warn("Request failed: {} for URL {}", response.status, url)
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
        Gear ID: ${gear_id ?: "N/A"}
    """.trimIndent()
}
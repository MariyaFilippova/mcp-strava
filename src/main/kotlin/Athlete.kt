import kotlinx.serialization.Serializable

@Serializable
data class Athlete(
    val id: Long,
    val username: String? = null,
    val resource_state: Int,
    val firstname: String,
    val lastname: String,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val sex: String? = null,
    val premium: Boolean,
    val follower: Int? = null,
    val follower_count: Int,
    val athlete_type: Int? = null,
    val ftp: Int? = null,
    val weight: Double,
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
        Resource State: $resource_state
        Location: ${city ?: "N/A"}, ${state ?: "N/A"}, ${country ?: "N/A"}
        Sex: ${sex ?: "N/A"}
        Premium User: $premium
        Followers: $follower_count
        Weight: ${"%.1f".format(weight)} kg
    """.trimIndent()
}
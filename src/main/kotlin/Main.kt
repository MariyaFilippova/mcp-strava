import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("strava.Main")

fun main() {
    logger.info("Starting Strava MCP Server v2.0.0")
    val server = configureServer()
    val transport = StdioServerTransport(System.`in`.asSource().buffered(), System.out.asSink().buffered())

    runBlocking {
        server.createSession(transport)
        logger.info("Server session created, waiting for requests")
        val done = Job()
        server.onClose {
            logger.info("Server closing")
            done.complete()
        }
        done.join()
    }
    Auth.close()
    logger.info("Server shutdown complete")
}

fun configureServer(): Server {
    val server = Server(
        Implementation(
            name = "strava mcp server",
            version = "2.0.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        )
    )

    server.addTool(
        name = "auth_strava",
        description = "Authorize with Strava"
    ) { _ ->
        Auth.auth()
        val athlete = getAthlete()
            ?: return@addTool CallToolResult(content = listOf(TextContent("Failed to get athlete information")))
        return@addTool CallToolResult(content = listOf(TextContent(athlete.getAllInfo())))
    }

    server.addTool(
        name = "last_activity",
        description = "Analyze last strava activity"
    ) { _ ->
        try {
            val activity =
                getLastActivity() ?: return@addTool CallToolResult(content = listOf(TextContent("No last activity")))
            return@addTool CallToolResult(content = listOf(TextContent(activity.getAllInfo())))
        } catch (e: Exception) {
            return@addTool CallToolResult(
                content = listOf(TextContent("An error occurred: ${e.message}"))
            )
        }
    }

    server.addTool(
        name = "get_streams",
        description = "Returns dynamics of heart rate/distance for the last activity"
    ) { _ ->
        try {
            val activity =
                getLastActivity() ?: return@addTool CallToolResult(content = listOf(TextContent("No last activity")))
            val streams = getActivityStreams(activity.id)
                ?: return@addTool CallToolResult(content = listOf(TextContent("Failed to get activity streams")))
            return@addTool CallToolResult(content = listOf(TextContent(streams)))
        } catch (e: Exception) {
            return@addTool CallToolResult(
                content = listOf(TextContent("An error occurred: ${e.message}"))
            )
        }
    }

    server.addTool(
        name = "recent_activities",
        description = "Get a list of recent Strava activities. Returns up to 10 most recent activities with their details."
    ) { _ ->
        try {
            val activities = getRecentActivities(10)
            if (activities.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No activities found")))
            }
            val result = activities.mapIndexed { index, activity ->
                "${index + 1}. ${activity.name} (${activity.sport_type}) - ${"%.2f".format(activity.distance / 1000)} km on ${activity.start_date_local}"
            }.joinToString("\n")
            return@addTool CallToolResult(content = listOf(TextContent("Recent Activities:\n$result")))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "athlete_stats",
        description = "Get all-time athlete statistics including total distance, time, and elevation for rides, runs, and swims. Shows recent (4 weeks), year-to-date, and all-time totals."
    ) { _ ->
        try {
            Auth.auth()
            val athlete = getAthlete()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Failed to get athlete")))
            val stats = getAthleteStats(athlete.id)
                ?: return@addTool CallToolResult(content = listOf(TextContent("Failed to get athlete stats")))
            return@addTool CallToolResult(content = listOf(TextContent(stats.format())))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "activities_by_type",
        description = "Get recent activities filtered by sport type. Supports: Run, Ride, Swim, Walk, Hike, TrailRun, VirtualRide, VirtualRun, Workout, WeightTraining, Yoga"
    ) { request ->
        try {
            val sportType = request.arguments?.get("sport_type")?.toString()?.removeSurrounding("\"")
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide a sport_type parameter (e.g., Run, Ride, Swim)")))
            val activities = getActivitiesByType(sportType, 10)
            if (activities.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No $sportType activities found")))
            }
            val result = activities.mapIndexed { index, activity ->
                "${index + 1}. ${activity.name} - ${"%.2f".format(activity.distance / 1000)} km, ${activity.moving_time / 60} min on ${activity.start_date_local}"
            }.joinToString("\n")
            return@addTool CallToolResult(content = listOf(TextContent("Recent $sportType Activities:\n$result")))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "weekly_summary",
        description = "Get a summary of activities from the past week including total distance, time, elevation, and breakdown by activity type."
    ) { _ ->
        try {
            val oneWeekAgo = System.currentTimeMillis() / 1000 - (7 * 24 * 60 * 60)
            val activities = getActivitiesInRange(oneWeekAgo)
            if (activities.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No activities in the past week")))
            }
            val summary = calculateSummary(activities)
            return@addTool CallToolResult(content = listOf(TextContent("Weekly Summary (Last 7 Days)\n${summary.format()}")))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "monthly_summary",
        description = "Get a summary of activities from the past month including total distance, time, elevation, and breakdown by activity type."
    ) { _ ->
        try {
            val oneMonthAgo = System.currentTimeMillis() / 1000 - (30 * 24 * 60 * 60)
            val activities = getActivitiesInRange(oneMonthAgo)
            if (activities.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No activities in the past month")))
            }
            val summary = calculateSummary(activities)
            return@addTool CallToolResult(content = listOf(TextContent("Monthly Summary (Last 30 Days)\n${summary.format()}")))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "month_summary",
        description = "Get activity summary for a specific month. Requires 'year' (e.g., 2025) and 'month' (1-12) parameters."
    ) { request ->
        try {
            val year = request.arguments?.get("year")?.toString()?.removeSurrounding("\"")?.toIntOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide a 'year' parameter (e.g., 2025)")))
            val month = request.arguments?.get("month")?.toString()?.removeSurrounding("\"")?.toIntOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide a 'month' parameter (1-12)")))

            if (month !in 1..12) {
                return@addTool CallToolResult(content = listOf(TextContent("Month must be between 1 and 12")))
            }

            val activities = getActivitiesForMonth(year, month)
            if (activities.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No activities found for $month/$year")))
            }
            val summary = calculateSummary(activities)
            val monthName = java.time.Month.of(month).name.lowercase().replaceFirstChar { it.uppercase() }
            return@addTool CallToolResult(content = listOf(TextContent("$monthName $year Summary\n${summary.format()}")))
        } catch (e: Exception) {
            logger.error("Error getting month summary: {}", e.message)
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "compare_months",
        description = "Compare activities between two months. Requires 'year1', 'month1', 'year2', 'month2' parameters. Example: compare January 2025 with January 2026."
    ) { request ->
        try {
            val year1 = request.arguments?.get("year1")?.toString()?.removeSurrounding("\"")?.toIntOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide 'year1' parameter")))
            val month1 = request.arguments?.get("month1")?.toString()?.removeSurrounding("\"")?.toIntOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide 'month1' parameter (1-12)")))
            val year2 = request.arguments?.get("year2")?.toString()?.removeSurrounding("\"")?.toIntOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide 'year2' parameter")))
            val month2 = request.arguments?.get("month2")?.toString()?.removeSurrounding("\"")?.toIntOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide 'month2' parameter (1-12)")))

            if (month1 !in 1..12 || month2 !in 1..12) {
                return@addTool CallToolResult(content = listOf(TextContent("Months must be between 1 and 12")))
            }

            val activities1 = getActivitiesForMonth(year1, month1)
            val activities2 = getActivitiesForMonth(year2, month2)

            val summary1 = calculateSummary(activities1)
            val summary2 = calculateSummary(activities2)

            val month1Name = java.time.Month.of(month1).name.lowercase().replaceFirstChar { it.uppercase() }
            val month2Name = java.time.Month.of(month2).name.lowercase().replaceFirstChar { it.uppercase() }

            val distanceDiff = summary2.totalDistance - summary1.totalDistance
            val timeDiff = summary2.totalMovingTime - summary1.totalMovingTime
            val elevationDiff = summary2.totalElevation - summary1.totalElevation
            val activityDiff = summary2.activityCount - summary1.activityCount

            fun formatDiff(value: Double, unit: String): String {
                val sign = if (value >= 0) "+" else ""
                return "$sign${"%.1f".format(value)} $unit"
            }

            fun formatTimeDiff(seconds: Int): String {
                val sign = if (seconds >= 0) "+" else "-"
                val absSeconds = kotlin.math.abs(seconds)
                val hours = absSeconds / 3600
                val minutes = (absSeconds % 3600) / 60
                return "$sign${hours}h ${minutes}m"
            }

            val result = buildString {
                appendLine("Comparison: $month1Name $year1 vs $month2Name $year2")
                appendLine("=".repeat(50))
                appendLine()
                appendLine("$month1Name $year1:")
                appendLine("  Activities: ${summary1.activityCount}")
                appendLine("  Distance: ${"%.1f".format(summary1.totalDistance / 1000)} km")
                appendLine("  Time: ${summary1.totalMovingTime / 3600}h ${(summary1.totalMovingTime % 3600) / 60}m")
                appendLine("  Elevation: ${"%.0f".format(summary1.totalElevation)} m")
                appendLine()
                appendLine("$month2Name $year2:")
                appendLine("  Activities: ${summary2.activityCount}")
                appendLine("  Distance: ${"%.1f".format(summary2.totalDistance / 1000)} km")
                appendLine("  Time: ${summary2.totalMovingTime / 3600}h ${(summary2.totalMovingTime % 3600) / 60}m")
                appendLine("  Elevation: ${"%.0f".format(summary2.totalElevation)} m")
                appendLine()
                appendLine("Difference ($month2Name $year2 vs $month1Name $year1):")
                appendLine("  Activities: ${if (activityDiff >= 0) "+" else ""}$activityDiff")
                appendLine("  Distance: ${formatDiff(distanceDiff / 1000, "km")}")
                appendLine("  Time: ${formatTimeDiff(timeDiff)}")
                appendLine("  Elevation: ${formatDiff(elevationDiff, "m")}")
            }

            return@addTool CallToolResult(content = listOf(TextContent(result)))
        } catch (e: Exception) {
            logger.error("Error comparing months: {}", e.message)
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "get_activity",
        description = "Fetch a specific Strava activity by its ID. Returns full activity details including distance, time, elevation, heartrate, etc."
    ) { request ->
        try {
            val id = request.arguments?.get("id")?.toString()?.removeSurrounding("\"")?.toLongOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide an 'id' parameter (activity ID)")))
            val activity = getActivityById(id)
                ?: return@addTool CallToolResult(content = listOf(TextContent("Activity $id not found or failed to fetch")))
            return@addTool CallToolResult(content = listOf(TextContent(activity.getAllInfo())))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "search_activities",
        description = "Search Strava activities with optional date range and pagination. Parameters: 'before' (epoch timestamp, optional), 'after' (epoch timestamp, optional), 'page' (default 1), 'per_page' (default 30, max 200)."
    ) { request ->
        try {
            val before = request.arguments?.get("before")?.toString()?.removeSurrounding("\"")?.toLongOrNull()
            val after = request.arguments?.get("after")?.toString()?.removeSurrounding("\"")?.toLongOrNull()
            val page = request.arguments?.get("page")?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 1
            val perPage = (request.arguments?.get("per_page")?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 30).coerceIn(1, 200)
            val activities = searchActivities(before = before, after = after, page = page, perPage = perPage)
            if (activities.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No activities found")))
            }
            val result = activities.mapIndexed { index, activity ->
                val num = (page - 1) * perPage + index + 1
                "$num. ${activity.name} (${activity.sport_type}) - ${"%.2f".format(activity.distance / 1000)} km on ${activity.start_date_local} [ID: ${activity.id}]"
            }.joinToString("\n")
            return@addTool CallToolResult(content = listOf(TextContent("Activities (page $page):\n$result")))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "get_activity_streams",
        description = "Get detailed data streams for any Strava activity. Returns time-series data like heartrate, distance, altitude, speed, cadence, power, GPS, and grade. Parameters: 'activity_id' (required), 'stream_types' (optional, comma-separated, default: heartrate,distance,altitude,velocity_smooth,cadence,watts,time,latlng,grade_smooth)."
    ) { request ->
        try {
            val activityId = request.arguments?.get("activity_id")?.toString()?.removeSurrounding("\"")?.toLongOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide an 'activity_id' parameter")))
            val streamTypes = request.arguments?.get("stream_types")?.toString()?.removeSurrounding("\"")
            val streams = if (streamTypes != null) {
                getActivityStreamsById(activityId, streamTypes)
            } else {
                getActivityStreamsById(activityId)
            }
            streams ?: return@addTool CallToolResult(content = listOf(TextContent("Failed to get streams for activity $activityId")))
            return@addTool CallToolResult(content = listOf(TextContent(streams)))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "get_laps",
        description = "Get lap data for a Strava activity. Returns lap splits with distance, time, speed, elevation, and heartrate for each lap. Parameter: 'activity_id' (required)."
    ) { request ->
        try {
            val activityId = request.arguments?.get("activity_id")?.toString()?.removeSurrounding("\"")?.toLongOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide an 'activity_id' parameter")))
            val laps = getActivityLaps(activityId)
            if (laps.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No laps found for activity $activityId")))
            }
            val result = laps.joinToString("\n\n") { it.format() }
            return@addTool CallToolResult(content = listOf(TextContent("Laps for activity $activityId:\n\n$result")))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "suggest_route",
        description = """Generate a round-trip route and return a Google Maps link for navigation. Provide a starting point (lat/lng or address) and desired distance. No API keys required.
Parameters:
- start_lat (Double, optional): Starting latitude. Required if start_address is not provided.
- start_lng (Double, optional): Starting longitude. Required if start_address is not provided.
- start_address (String, optional): Address to geocode as starting point. Alternative to lat/lng.
- distance_km (Double, required): Desired route distance in kilometers.
- activity_type (String, optional): One of: run, walk, hike, trail_run, ride, road_ride, mountain_bike. Default: run.
- num_points (Int, optional): Number of waypoints in the loop (1-8). Default: 4.
- seed (Int, optional): Random seed for route variation. Different seeds produce different routes."""
    ) { request ->
        try {
            val startLat = request.arguments?.get("start_lat")?.toString()?.removeSurrounding("\"")?.toDoubleOrNull()
            val startLng = request.arguments?.get("start_lng")?.toString()?.removeSurrounding("\"")?.toDoubleOrNull()
            val startAddress = request.arguments?.get("start_address")?.toString()?.removeSurrounding("\"")
            val distanceKm = request.arguments?.get("distance_km")?.toString()?.removeSurrounding("\"")?.toDoubleOrNull()
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide 'distance_km' parameter (desired route distance in km)")))
            val activityType = request.arguments?.get("activity_type")?.toString()?.removeSurrounding("\"") ?: "run"
            val numPoints = (request.arguments?.get("num_points")?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 4).coerceIn(1, 8)
            val seed = request.arguments?.get("seed")?.toString()?.removeSurrounding("\"")?.toIntOrNull()

            val (lat, lng) = if (startLat != null && startLng != null) {
                Pair(startLat, startLng)
            } else if (!startAddress.isNullOrBlank()) {
                geocodeAddress(startAddress)
                    ?: return@addTool CallToolResult(content = listOf(TextContent("Failed to geocode address: $startAddress")))
            } else {
                return@addTool CallToolResult(content = listOf(TextContent("Please provide either start_lat + start_lng or start_address")))
            }

            val travelMode = mapActivityTypeToTravelMode(activityType)
            val waypoints = generateWaypoints(lat, lng, distanceKm, numPoints, seed)
            val mapsUrl = buildGoogleMapsUrl(Pair(lat, lng), waypoints, travelMode)

            val result = buildString {
                appendLine("Route suggestion (~${"%.1f".format(distanceKm)} km ${activityType})")
                appendLine("=".repeat(40))
                appendLine("Start: ${"%.6f".format(lat)}, ${"%.6f".format(lng)}")
                appendLine("Waypoints: $numPoints")
                appendLine("Mode: $travelMode")
                appendLine()
                appendLine("Open in Google Maps:")
                appendLine(mapsUrl)
                appendLine()
                appendLine("Google Maps will calculate the actual road route with turn-by-turn directions.")
                if (seed != null) {
                    appendLine("Seed: $seed (use a different seed for an alternative route)")
                } else {
                    appendLine("Tip: provide a 'seed' parameter for reproducible routes, or change it to get a different route.")
                }
            }
            return@addTool CallToolResult(content = listOf(TextContent(result)))
        } catch (e: Exception) {
            logger.error("Error generating route: {}", e.message)
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "popular_routes",
        description = """Find popular Strava segments near a location and generate a Google Maps route through them. Uses Strava's Segment Explore API to find well-known segments, orders them into a loop, and returns a navigable Google Maps link.
Parameters:
- start_lat (Double, optional): Starting latitude. Required if start_address is not provided.
- start_lng (Double, optional): Starting longitude. Required if start_address is not provided.
- start_address (String, optional): Address to geocode as starting point. Alternative to lat/lng.
- activity_type (String, optional): "run" or "ride". Default: "run".
- radius_km (Double, optional): Search radius in km (1-50). Default: 5.0.
- max_segments (Int, optional): Maximum segments to include (1-5). Default: 5."""
    ) { request ->
        try {
            Auth.auth()
            val startLat = request.arguments?.get("start_lat")?.toString()?.removeSurrounding("\"")?.toDoubleOrNull()
            val startLng = request.arguments?.get("start_lng")?.toString()?.removeSurrounding("\"")?.toDoubleOrNull()
            val startAddress = request.arguments?.get("start_address")?.toString()?.removeSurrounding("\"")
            val activityType = request.arguments?.get("activity_type")?.toString()?.removeSurrounding("\"") ?: "run"
            val radiusKm = (request.arguments?.get("radius_km")?.toString()?.removeSurrounding("\"")?.toDoubleOrNull() ?: 5.0).coerceIn(1.0, 50.0)
            val maxSegments = (request.arguments?.get("max_segments")?.toString()?.removeSurrounding("\"")?.toIntOrNull() ?: 5).coerceIn(1, 5)

            val (lat, lng) = if (startLat != null && startLng != null) {
                Pair(startLat, startLng)
            } else if (!startAddress.isNullOrBlank()) {
                geocodeAddress(startAddress)
                    ?: return@addTool CallToolResult(content = listOf(TextContent("Failed to geocode address: $startAddress")))
            } else {
                return@addTool CallToolResult(content = listOf(TextContent("Please provide either start_lat + start_lng or start_address")))
            }

            val exploreResult = exploreSegments(lat, lng, radiusKm, activityType)
            if (exploreResult.error != null) {
                return@addTool CallToolResult(content = listOf(TextContent("Segment explore failed: ${exploreResult.error}")))
            }
            if (exploreResult.segments.isEmpty()) {
                return@addTool CallToolResult(content = listOf(TextContent("No segments found near (${"%.4f".format(lat)}, ${"%.4f".format(lng)}). Try increasing radius_km or changing activity_type.")))
            }

            val capped = exploreResult.segments.take(maxSegments)
            val waypoints = orderSegmentsIntoLoop(capped, lat, lng)
            val travelMode = mapActivityTypeToTravelMode(activityType)
            val mapsUrl = buildGoogleMapsUrl(Pair(lat, lng), waypoints, travelMode)

            val result = buildString {
                appendLine("Popular Segments Route (${activityType}, ${radiusKm}km radius)")
                appendLine("=".repeat(50))
                appendLine("Start: ${"%.4f".format(lat)}, ${"%.4f".format(lng)}")
                appendLine()
                appendLine("Segments (${capped.size}):")
                capped.forEachIndexed { i, seg ->
                    appendLine("  ${i + 1}. ${seg.name}")
                    appendLine("     Distance: ${"%.1f".format(seg.distance)}m | Grade: ${"%.1f".format(seg.avg_grade)}% | Climb: ${climbCategoryLabel(seg.climb_category)} | Elev: ${"%.0f".format(seg.elev_difference)}m")
                }
                appendLine()
                appendLine("Open in Google Maps:")
                appendLine(mapsUrl)
                appendLine()
                appendLine("Google Maps will route you along roads through these popular segments.")
            }
            return@addTool CallToolResult(content = listOf(TextContent(result)))
        } catch (e: Exception) {
            logger.error("Error finding popular routes: {}", e.message)
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "get_gear",
        description = "Get details about a piece of gear (shoes, bike, etc.) by its Strava gear ID. Returns name, brand, model, total distance, and weight. Parameter: 'gear_id' (required, e.g., 'g12345')."
    ) { request ->
        try {
            val gearId = request.arguments?.get("gear_id")?.toString()?.removeSurrounding("\"")
                ?: return@addTool CallToolResult(content = listOf(TextContent("Please provide a 'gear_id' parameter")))
            val gear = getGearById(gearId)
                ?: return@addTool CallToolResult(content = listOf(TextContent("Gear '$gearId' not found or failed to fetch")))
            return@addTool CallToolResult(content = listOf(TextContent(gear.format())))
        } catch (e: Exception) {
            return@addTool CallToolResult(content = listOf(TextContent("An error occurred: ${e.message}")))
        }
    }

    server.addTool(
        name = "logout",
        description = "Clear stored Strava authentication tokens. Use this to switch accounts or fix authentication issues."
    ) { _ ->
        Auth.clearTokens()
        return@addTool CallToolResult(content = listOf(TextContent("Logged out successfully. Stored tokens have been cleared. Use auth_strava to log in again.")))
    }

    return server
}
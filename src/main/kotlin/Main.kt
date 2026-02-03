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
        name = "logout",
        description = "Clear stored Strava authentication tokens. Use this to switch accounts or fix authentication issues."
    ) { _ ->
        Auth.clearTokens()
        return@addTool CallToolResult(content = listOf(TextContent("Logged out successfully. Stored tokens have been cleared. Use auth_strava to log in again.")))
    }

    return server
}
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

fun main() {
    val server = configureServer()
    val transport = StdioServerTransport(System.`in`.asSource().buffered(), System.out.asSink().buffered())

    runBlocking {
        server.createSession(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
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

    return server
}
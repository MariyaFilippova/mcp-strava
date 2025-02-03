import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.mcp.*
import org.jetbrains.kotlinx.mcp.server.Server
import org.jetbrains.kotlinx.mcp.server.ServerOptions
import org.jetbrains.kotlinx.mcp.server.StdioServerTransport


fun main() {
    val server = configureServer()
    val transport = StdioServerTransport()

    runBlocking {
        server.connect(transport)
        val done = Job()
        server.onCloseCallback = {
            done.complete()
        }
        done.join()
    }
}

fun configureServer(): Server {
    val def = CompletableDeferred<Unit>()

    val server = Server(
        Implementation(
            name = "strava mcp server",
            version = "1.0.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        ),
        onCloseCallback = {
            def.complete(Unit)
        }
    )

    server.addTool(
        name = "auth_strava",
        description = "Authorised in Strava",
        inputSchema = Tool.Input()
    ) { request ->
        Auth.auth()
        val athlete = getAthlete()
        return@addTool CallToolResult(content = listOf(TextContent(athlete!!.getAllInfo())))
    }

    server.addTool(
        name = "last_activity",
        description = "Analyze last strava activity",
        inputSchema = Tool.Input()
    ) { request ->
        try {
            val activity = getLastActivity()
            if (activity == null) {
                return@addTool CallToolResult(content = listOf(TextContent("No last activity")))
            }
            return@addTool CallToolResult(content = listOf(TextContent(activity.getAllInfo())))
        } catch (e: Exception) {
            return@addTool CallToolResult(
                content = listOf(TextContent("An error occurred: ${e.message}"))
            )
        }
    }


    server.addTool(
        name = "get_streams",
        description = "returns dynamics of heart rate/distance",
        inputSchema = Tool.Input()
    ) { request ->
        val activity = getLastActivity()
        if (activity == null) {
            return@addTool CallToolResult(content = listOf(TextContent("No last activity")))
        }
        return@addTool CallToolResult(content = listOf(TextContent(getActivityStreams(activity.id))))
    }

    return server
}
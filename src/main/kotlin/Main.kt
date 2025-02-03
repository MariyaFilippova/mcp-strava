import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.mcp.*
import org.jetbrains.kotlinx.mcp.server.SSEServerTransport
import org.jetbrains.kotlinx.mcp.server.Server
import org.jetbrains.kotlinx.mcp.server.ServerOptions
import org.jetbrains.kotlinx.mcp.server.StdioServerTransport


fun main() {
    `run mcp server using stdio`()
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

fun `run mcp server using stdio`() {
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

fun `run sse mcp server with plain configuration`(port: Int): Unit = runBlocking {
    val servers = ConcurrentMap<String, Server>()
    println("Starting sse server on port $port. ")
    println("Use inspector to connect to the http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SSEServerTransport("/message", this)
                val server = configureServer()

                servers[transport.sessionId] = server

                server.onCloseCallback = {
                    servers.remove(transport.sessionId)
                }

                server.connect(transport)
            }
            post("/message") {
                val sessionId: String = call.request.queryParameters["sessionId"]!!
                val transport = servers[sessionId]?.transport as? SSEServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                transport.handlePostMessage(call)
            }
        }
    }.start(wait = true)
}

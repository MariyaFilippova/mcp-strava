import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.awt.Desktop
import java.net.URI

object Auth {
    private const val AUTHORIZE_URL = "https://www.strava.com/oauth/mobile/authorize"
    private const val TOKEN_URL = "https://www.strava.com/oauth/token"

    private var CLIENT_ID : String? = null
    private var CLIENT_SECRET: String? = null

    var TOKEN: String? = null

    private val httpClient = HttpClient(io.ktor.client.engine.cio.CIO)

    suspend fun auth() {
        val dotenv = Dotenv.configure().load()
        CLIENT_ID = dotenv.get("CLIENT_ID")!!
        CLIENT_SECRET = dotenv.get("CLIENT_SECRET")!!
        openAuthorizationUrl(CLIENT_ID!!)
        val deferredToken = CompletableDeferred<String?>()
        val serverJob = CoroutineScope(Dispatchers.IO).launch { startServer(deferredToken) }
        TOKEN = deferredToken.await()
        serverJob.cancel()
    }

    /**
     * Start a local HTTP server to handle redirection and authorization exchange.
     */
    private fun startServer(deferredToken: CompletableDeferred<String?>) {
        embeddedServer(CIO, port = 3008) {
            routing {
                get("/exchange_token") {
                    val code = call.request.queryParameters["code"]
                    if (code != null) {
                        call.respondText("Authorization successful! You can close this tab.")
                        CoroutineScope(Dispatchers.IO).launch { exchangeToken(code, deferredToken) }
                    } else {
                        call.respondText("Authorization failed!", status = HttpStatusCode.BadRequest)
                    }
                }
            }
        }.start(wait = true)
    }

    /**
     * Exchange the authorization code for an access token.
     */
    private suspend fun exchangeToken(code: String, deferredToken: CompletableDeferred<String?>) {
        val response = httpClient.post(TOKEN_URL) {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                    "code" to code,
                    "grant_type" to "authorization_code"
                ).formUrlEncode()
            )
        }

        if (response.status == HttpStatusCode.OK) {
            val responseBody = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
            TOKEN = (jsonObject["access_token"] as? JsonPrimitive)?.content
            deferredToken.complete(TOKEN)
        } else {
            deferredToken.complete(null)
        }
    }

    /**
     * Open the authorization URL in the default browser or display the link in the log.
     */
    fun openAuthorizationUrl(clientId: String, port: Int = 3008) {
        val redirectUri = "http://127.0.0.1:$port/exchange_token"
        val scopes = "read_all,activity:read,activity:read_all,profile:read_all"
        val url =
            "$AUTHORIZE_URL?response_type=code&client_id=$clientId&redirect_uri=$redirectUri&approval_prompt=force&scope=$scopes"
        Desktop.getDesktop().browse(URI(url))
    }
}

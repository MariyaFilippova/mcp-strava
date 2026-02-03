import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.net.URI

private val logger = LoggerFactory.getLogger("strava.Auth")

@Serializable
data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

object Auth {
    private const val AUTHORIZE_URL = "https://www.strava.com/oauth/mobile/authorize"
    private const val TOKEN_URL = "https://www.strava.com/oauth/token"
    private const val AUTH_PORT = 3008
    private const val AUTH_TIMEOUT_MS = 120_000L // 2 minutes timeout for user to complete auth
    private val TOKEN_FILE = File(System.getProperty("user.home"), ".strava-mcp-token.json")

    private var clientId: String? = null
    private var clientSecret: String? = null
    private var tokenData: TokenData? = null

    val TOKEN: String?
        get() = tokenData?.accessToken

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private var httpClient: HttpClient? = null

    private fun getHttpClient(): HttpClient {
        if (httpClient == null) {
            httpClient = HttpClient(io.ktor.client.engine.cio.CIO)
        }
        return httpClient!!
    }

    suspend fun auth() {
        logger.debug("Starting authentication")
        loadCredentials()

        // Try to load existing token
        if (tryLoadPersistedToken()) {
            logger.debug("Loaded persisted token")
            // Check if token is expired or about to expire (within 5 minutes)
            val currentToken = tokenData
            if (currentToken != null) {
                val now = System.currentTimeMillis() / 1000
                if (currentToken.expiresAt > now + 300) {
                    // Token is still valid, verify it works
                    if (validateToken()) {
                        logger.info("Using valid persisted token")
                        return
                    }
                }
                // Token expired or invalid, try to refresh
                logger.debug("Token expired or invalid, attempting refresh")
                if (tryRefreshToken()) {
                    return
                }
            }
        }

        // Need to do full OAuth flow
        logger.info("Starting OAuth flow")
        performOAuthFlow()
    }

    private fun loadCredentials() {
        if (clientId != null && clientSecret != null) return

        val dotenv = Dotenv.configure().load()
        clientId = dotenv.get("CLIENT_ID")
            ?: throw IllegalStateException("CLIENT_ID not found in .env file. Please add your Strava API client ID.")
        clientSecret = dotenv.get("CLIENT_SECRET")
            ?: throw IllegalStateException("CLIENT_SECRET not found in .env file. Please add your Strava API client secret.")
    }

    private fun tryLoadPersistedToken(): Boolean {
        return try {
            if (TOKEN_FILE.exists()) {
                val content = TOKEN_FILE.readText()
                tokenData = jsonConfig.decodeFromString<TokenData>(content)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.warn("Failed to load persisted token: {}", e.message)
            false
        }
    }

    private fun persistToken() {
        try {
            tokenData?.let { data ->
                TOKEN_FILE.writeText(jsonConfig.encodeToString(data))
            }
        } catch (e: Exception) {
            logger.error("Failed to persist token: {}", e.message)
        }
    }

    private suspend fun validateToken(): Boolean {
        return try {
            val response = getHttpClient().get("https://www.strava.com/api/v3/athlete") {
                header("Authorization", "Bearer ${tokenData?.accessToken}")
                accept(ContentType.Application.Json)
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            logger.debug("Token validation failed: {}", e.message)
            false
        }
    }

    private suspend fun tryRefreshToken(): Boolean {
        val currentToken = tokenData ?: return false

        return try {
            val response = getHttpClient().post(TOKEN_URL) {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "grant_type" to "refresh_token",
                        "refresh_token" to currentToken.refreshToken
                    ).formUrlEncode()
                )
            }

            if (response.status == HttpStatusCode.OK) {
                parseAndStoreToken(response.bodyAsText())
                logger.info("Token refreshed successfully")
                true
            } else {
                logger.warn("Token refresh failed with status: {}", response.status)
                false
            }
        } catch (e: Exception) {
            logger.warn("Token refresh error: {}", e.message)
            false
        }
    }

    private suspend fun performOAuthFlow() {
        openAuthorizationUrl()

        val result = CompletableDeferred<Result<TokenData>>()
        val server = startServer(result)

        try {
            withTimeout(AUTH_TIMEOUT_MS) {
                val tokenResult = result.await()
                tokenResult.getOrElse { error ->
                    throw error
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("Authentication timed out. Please try again and complete the authorization within 2 minutes.")
        } finally {
            server.stop(1000, 2000)
        }
    }

    private fun startServer(result: CompletableDeferred<Result<TokenData>>): EmbeddedServer<*, *> {
        val server = embeddedServer(CIO, port = AUTH_PORT) {
            routing {
                get("/exchange_token") {
                    val code = call.request.queryParameters["code"]
                    val error = call.request.queryParameters["error"]

                    when {
                        error != null -> {
                            call.respondText("Authorization denied: $error", status = HttpStatusCode.BadRequest)
                            result.complete(Result.failure(IllegalStateException("User denied authorization: $error")))
                        }
                        code != null -> {
                            call.respondText("Authorization successful! You can close this tab.")
                            // Exchange token in the same coroutine context
                            try {
                                exchangeToken(code)
                                result.complete(Result.success(tokenData!!))
                            } catch (e: Exception) {
                                result.complete(Result.failure(e))
                            }
                        }
                        else -> {
                            call.respondText("Missing authorization code", status = HttpStatusCode.BadRequest)
                            result.complete(Result.failure(IllegalStateException("No authorization code received")))
                        }
                    }
                }
            }
        }
        server.start(wait = false)
        return server
    }

    private suspend fun exchangeToken(code: String) {
        val response = getHttpClient().post(TOKEN_URL) {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "code" to code,
                    "grant_type" to "authorization_code"
                ).formUrlEncode()
            )
        }

        if (response.status == HttpStatusCode.OK) {
            parseAndStoreToken(response.bodyAsText())
        } else {
            val errorBody = response.bodyAsText()
            throw IllegalStateException("Token exchange failed (${response.status}): $errorBody")
        }
    }

    private fun parseAndStoreToken(responseBody: String) {
        val json = Json.parseToJsonElement(responseBody).jsonObject

        val accessToken = json["access_token"]?.toString()?.removeSurrounding("\"")
            ?: throw IllegalStateException("No access_token in response")
        val refreshToken = json["refresh_token"]?.toString()?.removeSurrounding("\"")
            ?: throw IllegalStateException("No refresh_token in response")
        val expiresAt = json["expires_at"]?.toString()?.toLongOrNull()
            ?: throw IllegalStateException("No expires_at in response")

        tokenData = TokenData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt
        )
        persistToken()
    }

    private fun openAuthorizationUrl() {
        val redirectUri = "http://127.0.0.1:$AUTH_PORT/exchange_token"
        val scopes = "read_all,activity:read,activity:read_all,profile:read_all"
        val url = "$AUTHORIZE_URL?response_type=code&client_id=$clientId&redirect_uri=$redirectUri&approval_prompt=force&scope=$scopes"

        val canOpenBrowser = try {
            !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        } catch (_: Exception) {
            false
        }

        if (canOpenBrowser) {
            try {
                Desktop.getDesktop().browse(URI(url))
                logger.info("Browser opened for authorization")
            } catch (e: Exception) {
                logger.warn("Failed to open browser: {}", e.message)
                printManualAuthInstructions(url)
            }
        } else {
            printManualAuthInstructions(url)
        }
    }

    private fun printManualAuthInstructions(url: String) {
        logger.info("Please open the following URL in your browser to authorize:")
        logger.info(url)
    }

    /**
     * Clear stored tokens (useful for testing or forcing re-auth)
     */
    fun clearTokens() {
        tokenData = null
        try {
            if (TOKEN_FILE.exists()) {
                TOKEN_FILE.delete()
                logger.info("Tokens cleared successfully")
            }
        } catch (e: Exception) {
            logger.error("Failed to delete token file: {}", e.message)
        }
    }

    /**
     * Close resources when shutting down
     */
    fun close() {
        httpClient?.close()
        httpClient = null
        logger.debug("Auth resources closed")
    }
}
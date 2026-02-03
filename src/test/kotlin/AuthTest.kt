import kotlin.test.Test
import kotlin.test.assertEquals

class AuthTest {

    @Test
    fun `TokenData serialization works correctly`() {
        val tokenData = TokenData(
            accessToken = "test_access_token",
            refreshToken = "test_refresh_token",
            expiresAt = 1700000000L
        )

        val json = kotlinx.serialization.json.Json.encodeToString(TokenData.serializer(), tokenData)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(TokenData.serializer(), json)

        assertEquals("test_access_token", decoded.accessToken)
        assertEquals("test_refresh_token", decoded.refreshToken)
        assertEquals(1700000000L, decoded.expiresAt)
    }

    @Test
    fun `TokenData deserialization from JSON works`() {
        val json = """
            {
                "accessToken": "abc123",
                "refreshToken": "refresh456",
                "expiresAt": 1699999999
            }
        """.trimIndent()

        val tokenData = kotlinx.serialization.json.Json.decodeFromString(TokenData.serializer(), json)

        assertEquals("abc123", tokenData.accessToken)
        assertEquals("refresh456", tokenData.refreshToken)
        assertEquals(1699999999L, tokenData.expiresAt)
    }

    @Test
    fun `TokenData preserves all fields through round-trip`() {
        val original = TokenData(
            accessToken = "access_abc",
            refreshToken = "refresh_xyz",
            expiresAt = 1234567890L
        )

        val json = kotlinx.serialization.json.Json.encodeToString(TokenData.serializer(), original)
        val restored = kotlinx.serialization.json.Json.decodeFromString(TokenData.serializer(), json)

        assertEquals(original, restored)
    }

    @Test
    fun `Auth TOKEN property returns null when no token data`() {
        // Clear any existing tokens first
        Auth.clearTokens()

        // TOKEN should be null when no auth has occurred
        assertEquals(null, Auth.TOKEN)
    }
}
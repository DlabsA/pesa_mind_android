package cc.dlabs.pesamind.features.auth

import cc.dlabs.pesamind.core.network.models.VerifyGoogleTokenResponse
import cc.dlabs.pesamind.core.network.models.CompleteGoogleSignupResponse
import cc.dlabs.pesamind.core.network.models.CheckUsernameResponse
import cc.dlabs.pesamind.core.network.models.AuthProfile
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for username validation
 */
class UsernameValidationTest {

    private fun validateUsername(username: String): String? = when {
        username.isBlank() -> "Username is required"
        username.length < 3 -> "Username must be at least 3 characters"
        username.length > 50 -> "Username must be at most 50 characters"
        !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
        else -> null
    }

    @Test
    fun testEmptyUsername() {
        val error = validateUsername("")
        assertEquals("Username is required", error)
    }

    @Test
    fun testUsernameTooShort() {
        val error = validateUsername("ab")
        assertEquals("Username must be at least 3 characters", error)
    }

    @Test
    fun testUsernameTooLong() {
        val username = "a".repeat(51)
        val error = validateUsername(username)
        assertEquals("Username must be at most 50 characters", error)
    }

    @Test
    fun testUsernameWithSpecialCharacters() {
        val error = validateUsername("john-doe")
        assertEquals("Username can only contain letters, numbers, and underscores", error)
    }

    @Test
    fun testValidUsername() {
        val error = validateUsername("john_doe")
        assertNull(error)
    }

    @Test
    fun testValidUsernameWithNumbers() {
        val error = validateUsername("john123")
        assertNull(error)
    }

    @Test
    fun testValidUsernameMinLength() {
        val error = validateUsername("abc")
        assertNull(error)
    }

    @Test
    fun testValidUsernameMaxLength() {
        val username = "a".repeat(50)
        val error = validateUsername(username)
        assertNull(error)
    }
}

/**
 * Unit tests for Google OAuth response models
 */
class GoogleOAuthResponseTest {

    @Test
    fun testVerifyGoogleTokenResponseNewUser() {
        val response = VerifyGoogleTokenResponse(
            accessToken = null,
            refreshToken = null,
            isNewUser = true,
            profile = null,
            error = null
        )

        assertTrue(response.isNewUser)
        assertNull(response.accessToken)
        assertNull(response.profile)
    }

    @Test
    fun testVerifyGoogleTokenResponseExistingUser() {
        val profile = AuthProfile(
            id = "user123",
            username = "john_doe",
            balance = 1000.0
        )
        val response = VerifyGoogleTokenResponse(
            accessToken = "token123",
            refreshToken = "refresh123",
            isNewUser = false,
            profile = profile,
            error = null
        )

        assertFalse(response.isNewUser)
        assertEquals("token123", response.accessToken)
        assertEquals("refresh123", response.refreshToken)
        assertEquals("john_doe", response.profile?.username)
    }

    @Test
    fun testCompleteGoogleSignupResponse() {
        val profile = AuthProfile(
            id = "newuser123",
            username = "jane_doe",
            balance = 0.0
        )
        val response = CompleteGoogleSignupResponse(
            accessToken = "newtoken",
            refreshToken = "newrefresh",
            isNewUser = true,
            profile = profile,
            error = null
        )

        assertTrue(response.isNewUser)
        assertEquals("newtoken", response.accessToken)
        assertEquals("jane_doe", response.profile?.username)
    }

    @Test
    fun testCheckUsernameResponseAvailable() {
        val response = CheckUsernameResponse(
            available = true,
            message = "Username is available",
            error = null
        )

        assertTrue(response.available)
        assertEquals("Username is available", response.message)
    }

    @Test
    fun testCheckUsernameResponseTaken() {
        val response = CheckUsernameResponse(
            available = false,
            message = "Username already taken",
            error = null
        )

        assertFalse(response.available)
        assertEquals("Username already taken", response.message)
    }
}

/**
 * Unit tests for GoogleSignInResult sealed class
 */
class GoogleSignInResultTest {

    @Test
    fun testSuccessResult() {
        val result = GoogleSignInResult.Success(
            email = "user@example.com",
            displayName = "John Doe",
            profilePhotoUrl = "https://example.com/photo.jpg",
            googleId = "110169214549386730370"
        )

        assertTrue(result is GoogleSignInResult.Success)
        assertEquals("user@example.com", result.email)
    }

    @Test
    fun testErrorResult() {
        val result = GoogleSignInResult.Error("Sign-in was canceled")

        assertTrue(result is GoogleSignInResult.Error)
        assertEquals("Sign-in was canceled", (result as GoogleSignInResult.Error).message)
    }
}


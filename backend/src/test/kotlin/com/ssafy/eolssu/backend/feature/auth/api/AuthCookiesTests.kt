package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.feature.auth.domain.IssuedTokens
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AuthCookiesTests {
    private val tokens = IssuedTokens("access.jwt", Instant.EPOCH, "refresh-token", Instant.EPOCH)

    @Test
    fun `issues HttpOnly Lax cookies with access on api and refresh only on auth paths`() {
        val (access, refresh) = AuthCookies(secure = true).issue(tokens)
        assertTrue(access.startsWith("access_token=access.jwt; Path=/api; Max-Age=1800;"), access)
        assertTrue(refresh.startsWith("refresh_token=refresh-token; Path=/api/auth; Max-Age=1209600;"), refresh)
        for (cookie in listOf(access, refresh)) {
            listOf("Secure", "HttpOnly", "SameSite=Lax").forEach { assertTrue(cookie.contains("; $it"), cookie) }
        }
    }

    @Test
    fun `non expiring server tokens use persistent secure cookies within browser limits`() {
        val cookies = AuthCookies(true).issue(IssuedTokens("access.jwt", null, "refresh-token", null))
        cookies.forEach {
            assertTrue(it.contains("Max-Age=34560000;"), it)
            assertTrue(it.contains("HttpOnly; SameSite=Lax"), it)
            assertTrue(it.contains("Secure;"), it)
        }
    }

    @Test
    fun `clears both cookies on the same paths and omits Secure only when configured`() {
        val cleared = AuthCookies(secure = false).clear()
        assertEquals(listOf("access_token=; Path=/api; Max-Age=0;", "refresh_token=; Path=/api/auth; Max-Age=0;"), cleared.map { it.substringBefore(" Expires") })
        cleared.forEach { assertFalse(it.contains("Secure"), it) }
    }
}

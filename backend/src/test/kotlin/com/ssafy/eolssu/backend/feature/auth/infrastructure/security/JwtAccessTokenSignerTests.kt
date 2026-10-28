package com.ssafy.eolssu.backend.feature.auth.infrastructure.security

import com.ssafy.eolssu.backend.config.AccessTokenConfig
import com.ssafy.eolssu.backend.config.SecurityProperties
import com.ssafy.eolssu.backend.feature.auth.domain.TokenIssuingUnavailable
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class JwtAccessTokenSignerTests {
    private val secret = "test-signing-key-at-least-32-bytes-long!!"

    @Test
    fun `signed access token passes the cookie decoder with users id as subject`() {
        val issuedAt = Instant.now().minusSeconds(5)
        val token = JwtAccessTokenSigner(secret).sign(7, issuedAt, issuedAt.plusSeconds(1800))
        val decoded = AccessTokenConfig().accessTokenDecoder(SecurityProperties(jwtSecret = secret)).decode(token)
        assertEquals("7", decoded.subject)
        assertEquals(issuedAt.plusSeconds(1800).epochSecond, decoded.expiresAt!!.epochSecond)
    }

    @Test
    fun `missing key makes issuing unavailable instead of using a default key`() {
        assertFailsWith<TokenIssuingUnavailable> { JwtAccessTokenSigner("").sign(7, Instant.now(), Instant.now().plusSeconds(60)) }
        assertFailsWith<IllegalArgumentException> { JwtAccessTokenSigner("short") }
    }
}

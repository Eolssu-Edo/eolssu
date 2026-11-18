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
    fun `non expiring access requires selected user and active server session`() {
        val hash = "a".repeat(64)
        var active = true
        val check = com.ssafy.eolssu.backend.feature.auth.domain.NonExpiringSessionCheck { id, value -> active && id == 7L && value == hash }
        val beans = org.springframework.beans.factory.support.StaticListableBeanFactory(mapOf("sessions" to check))
        val decoder = AccessTokenConfig().accessTokenDecoder(
            SecurityProperties(jwtSecret = secret, nonExpiringUserIds = setOf(7)),
            beans.getBeanProvider(com.ssafy.eolssu.backend.feature.auth.domain.NonExpiringSessionCheck::class.java),
        )
        val signer = JwtAccessTokenSigner(secret)
        // 오래된 발급 시각으로도 유효하며 서버에서 폐기하면 즉시 거부한다. sleep은 쓰지 않는다.
        val token = signer.signNonExpiring(7, Instant.parse("2000-01-01T00:00:00Z"), hash)
        assertEquals("7", decoder.decode(token).subject)
        kotlin.test.assertNull(decoder.decode(token).expiresAt)
        assertFailsWith<org.springframework.security.oauth2.jwt.JwtException> {
            AccessTokenConfig().accessTokenDecoder(SecurityProperties(jwtSecret = secret)).decode(token)
        }
        assertFailsWith<org.springframework.security.oauth2.jwt.JwtException> {
            decoder.decode(signer.signNonExpiring(8, Instant.now(), hash))
        }
        assertFailsWith<org.springframework.security.oauth2.jwt.JwtException> {
            decoder.decode(signer.signNonExpiring(7, Instant.now(), "b".repeat(64)))
        }
        active = false
        assertFailsWith<org.springframework.security.oauth2.jwt.JwtException> { decoder.decode(token) }
    }

    @Test
    fun `missing key makes issuing unavailable instead of using a default key`() {
        assertFailsWith<TokenIssuingUnavailable> { JwtAccessTokenSigner("").sign(7, Instant.now(), Instant.now().plusSeconds(60)) }
        assertFailsWith<IllegalArgumentException> { JwtAccessTokenSigner("short") }
    }
}

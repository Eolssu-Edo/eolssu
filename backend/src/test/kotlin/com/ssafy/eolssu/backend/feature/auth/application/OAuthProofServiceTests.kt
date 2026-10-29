package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.auth.infrastructure.security.EmailVerificationSecrets
import java.time.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class OAuthProofServiceTests {
    private val now = Instant.parse("2026-10-29T00:00:00Z")
    private val repository = MemoryProofs()
    private val service = OAuthProofService(repository, EmailVerificationSecrets(null), Clock.fixed(now, ZoneOffset.UTC))

    @Test fun `state binds browser purpose user provider and exact callback before consumption`() {
        val started = service.start("GOOGLE", "LINK", 7, "https://example.test/callback")
        val stored = checkNotNull(repository.rows.values.singleOrNull())
        assertNotEquals(started.state, stored.hash)
        assertNotEquals(started.binding, stored.bindingHash)
        for ((provider, purpose, user, uri, binding) in listOf(
            arrayOf("NAVER", "LINK", "7", "https://example.test/callback", started.binding),
            arrayOf("GOOGLE", "LOGIN", "7", "https://example.test/callback", started.binding),
            arrayOf("GOOGLE", "LINK", "8", "https://example.test/callback", started.binding),
            arrayOf("GOOGLE", "LINK", "7", "https://example.test/other", started.binding),
            arrayOf("GOOGLE", "LINK", "7", "https://example.test/callback", "other-browser"),
        )) assertFalse(service.consumeState(started.state, binding, provider, purpose, user.toLong(), uri))
        assertTrue(service.consumeState(started.state, started.binding, "GOOGLE", "LINK", 7, "https://example.test/callback"))
        assertFalse(service.consumeState(started.state, started.binding, "GOOGLE", "LINK", 7, "https://example.test/callback"))
    }

    @Test fun `state expires exactly at ten minutes`() {
        val started = service.start("KAKAO", "LOGIN", null, "https://example.test/callback")
        val later = OAuthProofService(repository, EmailVerificationSecrets(null), Clock.fixed(now.plusSeconds(600), ZoneOffset.UTC))
        assertFalse(later.consumeState(started.state, started.binding, "KAKAO", "LOGIN", null, "https://example.test/callback"))
    }

    private class MemoryProofs : OAuthProofRepository {
        val rows = mutableMapOf<String, OAuthProof>()
        val consumed = mutableSetOf<String>()
        override fun insert(proof: OAuthProof) { rows[proof.hash] = proof }
        override fun find(hash: String) = rows[hash]
        override fun consume(hash: String, now: Instant) = rows[hash]?.let { now.isBefore(it.expiresAt) && consumed.add(hash) } ?: false
        override fun deleteExpired(now: Instant) = 0
    }
}

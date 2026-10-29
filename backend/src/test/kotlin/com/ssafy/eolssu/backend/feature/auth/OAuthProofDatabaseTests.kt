package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.feature.auth.domain.OAuthProof
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.JdbcOAuthProofRepository
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuthProofDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    private val now = Instant.parse("2026-10-29T00:00:00Z")
    private val hash = "a".repeat(64)

    @AfterEach fun cleanup() { jdbc.update("DELETE FROM oauth_proofs WHERE token_hash=?", hash) }

    @Test fun `stored proof survives repository recreation and concurrent consumption has one winner`() {
        val repository = JdbcOAuthProofRepository(jdbc)
        repository.insert(OAuthProof(hash,"STATE","GOOGLE","LOGIN","b".repeat(64),null,"https://example.test/callback",null,null,false,now.plusSeconds(600)))
        val reloaded = checkNotNull(JdbcOAuthProofRepository(jdbc).find(hash))
        assertEquals("https://example.test/callback", reloaded.redirectUri)
        assertEquals(now.plusSeconds(600), reloaded.expiresAt)
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { pool.submit(Callable { barrier.await(10,TimeUnit.SECONDS); repository.consume(hash,now) }) }
            assertEquals(1, results.count { it.get(10,TimeUnit.SECONDS) })
            assertNotNull(checkNotNull(repository.find(hash)).consumedAt)
        } finally { pool.shutdownNow() }
    }

    @Test fun `merge proof preserves verified context expires at boundary and can be cleaned`() {
        val repository = JdbcOAuthProofRepository(jdbc)
        repository.insert(OAuthProof(hash,"MERGE","KAKAO","LOGIN","b".repeat(64),123,null,"CaseSensitiveId","member@example.test",true,now))
        val proof = checkNotNull(repository.find(hash))
        assertEquals(123L,proof.userId)
        assertEquals("CaseSensitiveId",proof.subject)
        assertTrue(proof.emailVerified)
        assertFalse(repository.consume(hash,now))
        assertTrue(repository.deleteExpired(now) > 0)
        assertNull(repository.find(hash))
    }

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}

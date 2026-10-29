package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.feature.auth.domain.OAuthProof
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.JdbcOAuthProofRepository
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuthProofDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var manager: PlatformTransactionManager
    @Value("\${local.server.port}") private var port=0
    private val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
    private val hash = "a".repeat(64)

    @AfterEach fun cleanup() { jdbc.update("DELETE FROM oauth_proofs WHERE token_hash=?", hash) }

    @Test fun `missing provider keys allow startup but reject real HTTP login start with 503`() {
        val before=jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs",Int::class.java)
        HttpClient.newHttpClient().use { client ->
            val request=HttpRequest.newBuilder(URI("http://localhost:$port/api/auth/oauth/google/start"))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"redirectUri":"https://example.test/callback"}""")).build()
            val response=client.send(request,HttpResponse.BodyHandlers.ofString())
            assertEquals(503,response.statusCode(),response.body())
            assertTrue(response.body().contains("\"errorCode\":\"SERVICE_UNAVAILABLE\""))
        }
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM oauth_proofs",Int::class.java))
    }

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

    @Test fun `expiry is evaluated after waiting for the proof row lock`() {
        val repository=JdbcOAuthProofRepository(jdbc)
        repository.insert(OAuthProof(hash,"STATE","GOOGLE","LOGIN","b".repeat(64),null,"https://example.test/callback",null,null,false,now.plusSeconds(600)))
        val pool=Executors.newSingleThreadExecutor()
        lateinit var consuming:Future<Boolean>
        try {
            TransactionTemplate(manager).executeWithoutResult {
                jdbc.queryForList("SELECT token_hash FROM oauth_proofs WHERE token_hash=? FOR UPDATE",hash)
                consuming=pool.submit(Callable{repository.consume(hash,now)})
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                var waiting=false
                while(System.nanoTime()<deadline){
                    waiting=jdbc.queryForObject("""SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                        JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                        WHERE l.OBJECT_NAME='oauth_proofs'""",Int::class.java)!!>0
                    if(waiting)break
                    if(consuming.isDone)fail("consume did not wait for proof row lock")
                }
                assertTrue(waiting,"consume must wait for the held proof lock")
                jdbc.update("UPDATE oauth_proofs SET expires_at=UTC_TIMESTAMP(6) WHERE token_hash=?",hash)
            }
            assertFalse(consuming.get(10,TimeUnit.SECONDS))
            assertNull(checkNotNull(repository.find(hash)).consumedAt)
        }finally{pool.shutdownNow()}
    }

    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.oauth.google.client-id") { "" }
            registry.add("eolssu.auth.oauth.google.client-secret") { "" }
            registry.add("eolssu.auth.oauth.google.redirect-uris") { "" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}

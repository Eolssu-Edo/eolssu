package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.config.PasswordConfig
import com.ssafy.eolssu.backend.feature.auth.application.PurgeSummary
import com.ssafy.eolssu.backend.feature.auth.application.SignInResult
import com.ssafy.eolssu.backend.feature.auth.application.SignInService
import com.ssafy.eolssu.backend.feature.auth.application.WithdrawalPurgeService
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(EmailVerificationApiDatabaseTests.Fixtures::class)
class WithdrawalPurgeConcurrencyDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var repository: WithdrawalPurgeRepository
    @Autowired private lateinit var signIn: SignInService
    @Autowired private lateinit var clock: VerificationTestClock
    @Autowired private lateinit var manager: PlatformTransactionManager
    private val expiry = Instant.parse("2026-11-21T19:00:00Z")
    private val email = "withdrawal@race306.test"
    private val key = "recordings/race306/keep.m4a"
    private var userId = 0L

    @BeforeEach
    fun before() {
        cleanup()
        clock.set(expiry.minusNanos(1_000))
        jdbc.update("INSERT INTO instruments (id,code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent) VALUES (990306,'RACE306','경합 검증',1,2,50)")
        jdbc.update(
            """INSERT INTO users (email,password_hash,nickname,instrument_id,status,withdrawal_requested_at,purge_scheduled_at)
               VALUES (?,?,'경합 회원',990306,'WITHDRAWAL_PENDING','2026-10-22 19:00:00','2026-11-21 19:00:00')""",
            email, PasswordConfig().passwordEncoder().encode("Example!234"),
        )
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long::class.java, email)!!
        jdbc.update(
            """INSERT INTO practice_sessions (user_id,song_id,score_version,bpm,bpm_unit,start_note_idx,end_note_idx,recording_url,status,started_at)
               VALUES (?,1,1,90,'DOTTED_QUARTER',0,0,?,'DONE','2026-10-22 01:00:00')""", userId, key,
        )
    }

    @AfterEach
    fun after() { cleanup() }

    private fun cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE instrument_id=990306)")
        jdbc.update("DELETE FROM practice_sessions WHERE user_id IN (SELECT id FROM users WHERE instrument_id=990306)")
        jdbc.update("DELETE FROM users WHERE instrument_id=990306")
        jdbc.update("DELETE FROM instruments WHERE id=990306")
    }

    // 실제 후보 조회를 사용하되 다른 검사의 회원은 이 검사의 저장소 대역으로 보내지 않는다.
    private fun scopedRepository() = object : WithdrawalPurgeRepository by repository {
        override fun dueMembers(now: Instant, afterUserId: Long, limit: Int) =
            repository.dueMembers(now, afterUserId, Int.MAX_VALUE).filter { it == userId }
    }

    @Test
    fun `cancellation begun before expiry commits before purge touches any recording`() {
        val deleted = CopyOnWriteArrayList<String>()
        val purge = WithdrawalPurgeService(scopedRepository(), { deleted += it }, clock)
        var purging: CompletableFuture<PurgeSummary>? = null
        try {
            TransactionTemplate(manager).executeWithoutResult {
                // 실제 로그인으로 취소했지만 외부 트랜잭션이 아직 회원 잠금을 보유한다.
                assertIs<SignInResult.SignedIn>(signIn.signIn(email, "Example!234"))
                clock.set(expiry)
                purging = CompletableFuture.supplyAsync { purge.purgeDue() }
                awaitUserLock(checkNotNull(purging))
                assertTrue(deleted.isEmpty(), "a pending cancellation must resolve before recording deletion")
            }
            assertEquals(PurgeSummary(0, 0, 1, 0), checkNotNull(purging).get(15, TimeUnit.SECONDS))
            assertTrue(deleted.isEmpty())
            assertEquals("ACTIVE", status())
            assertEquals(key, jdbc.queryForObject("SELECT recording_url FROM practice_sessions WHERE user_id=?", String::class.java, userId))
        } finally {
            // 실패 시에도 바깥 트랜잭션이 해제된 뒤 worker가 종료돼야 정리와 경합하지 않는다.
            purging?.get(15, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `login between recording deletion and database purge cannot reactivate the expired member`() {
        clock.set(expiry)
        val deleted = mutableListOf<String>()
        val purge = WithdrawalPurgeService(scopedRepository(), { recording ->
            deleted += recording
            assertEquals(SignInResult.InvalidCredentials, signIn.signIn(email, "Example!234"))
            assertEquals("WITHDRAWAL_PENDING", status())
            assertNoTokens()
        }, clock)
        assertEquals(PurgeSummary(1, 0, 0, 0), purge.purgeDue())
        assertEquals(listOf(key), deleted)
        assertEquals("DELETED", status())
    }

    @Test
    fun `recording deletion failure defers purge without reopening withdrawal cancellation`() {
        clock.set(expiry)
        val purge = WithdrawalPurgeService(scopedRepository(), { throw IllegalStateException("storage unavailable") }, clock)
        assertEquals(PurgeSummary(0, 1, 0, 0), purge.purgeDue())
        assertEquals(SignInResult.InvalidCredentials, signIn.signIn(email, "Example!234"))
        assertEquals("WITHDRAWAL_PENDING", status())
        assertEquals(key, jdbc.queryForObject("SELECT recording_url FROM practice_sessions WHERE user_id=?", String::class.java, userId))
        assertNoTokens()
    }

    private fun status() = jdbc.queryForObject("SELECT status FROM users WHERE id=?", String::class.java, userId)
    private fun assertNoTokens() = assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", Int::class.java, userId))

    private fun awaitUserLock(worker: CompletableFuture<*>) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (System.nanoTime() < deadline) {
            val waiting = jdbc.queryForObject(
                """SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                   JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                   WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='users' AND l.INDEX_NAME='PRIMARY' AND l.LOCK_DATA=?""",
                Int::class.java, userId.toString(),
            )!! > 0
            if (waiting) return
            if (worker.isDone) fail("purge finished without waiting for the cancellation transaction: ${worker.get()}")
        }
        fail("purge must wait for the member lock")
    }

    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { "test-signing-key-at-least-32-bytes-long!!" }
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}

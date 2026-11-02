package com.ssafy.eolssu.backend.feature.auth

import com.ssafy.eolssu.backend.feature.auth.application.PurgeSummary
import com.ssafy.eolssu.backend.feature.auth.application.WithdrawalPurgeService
import com.ssafy.eolssu.backend.feature.auth.domain.WithdrawalPurgeRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional

/** 고정 데이터(mapper-fixtures.sql)의 회원 1에 모든 삭제 대상이 있다. 테스트 트랜잭션은 끝나면 되돌린다. */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WithdrawalPurgeDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var repository: WithdrawalPurgeRepository
    private val now = Instant.parse("2026-11-21T19:00:00Z")

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `due members lose their data and personal fields while other members and accent variants stay`() {
        // 회원 1을 삭제 대상으로, 회원 3(고정 데이터의 탈퇴 대기)은 아직 예정 전으로 둔다.
        jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING', withdrawal_requested_at = '2026-10-22 19:00:00', purge_scheduled_at = '2026-11-21 19:00:00' WHERE id = 1")
        jdbc.update("UPDATE users SET purge_scheduled_at = '2026-11-21 19:00:01' WHERE id = 3")
        jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_user_id) VALUES (1, 'KAKAO', 'kakao-1')")
        jdbc.update("INSERT INTO email_verifications (email, purpose, code_hash, attempt_count, expires_at) VALUES ('tést@example.test', 'SIGN_UP', 'code-hash', 0, '2026-10-22 01:02:03')")
        jdbc.update("INSERT INTO users (id, email, password_hash, nickname, instrument_id, status) VALUES (4, 'other@example.test', 'hash', '다른 회원', 1, 'ACTIVE')")
        jdbc.update("INSERT INTO score_views (user_id, song_id, idempotency_key, started_at, ended_at, visible_seconds, counted_seconds) VALUES (4, 1, 'other-key', '2026-10-22 01:00:00', '2026-10-22 01:10:00', 600, 600)")
        // 다른 회원의 세션·평가·채점 결과는 조인 범위가 틀리면 함께 지워진다.
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, score_version, bpm, bpm_unit, start_note_idx, end_note_idx, recording_url, status, started_at) " +
                "VALUES (40, 4, 1, 1, 90, 'DOTTED_QUARTER', 0, 0, 'recordings/40/other.m4a', 'DONE', '2026-10-22 01:00:00')",
        )
        jdbc.update("INSERT INTO evaluations (id, session_id, total_score, pitch_score, timing_score, duration_score, model_version) VALUES (40, 40, 70, 70, 70, 70, 'test-v1')")
        jdbc.update("INSERT INTO note_results (evaluation_id, note_idx, expected_pitch_id, verdict) VALUES (40, 0, 2, 'MISSED')")

        val deleted = mutableListOf<String>()
        val summary = WithdrawalPurgeService(repository, { deleted += it }, Clock.fixed(now, ZoneOffset.UTC)).purgeDue()

        assertEquals(PurgeSummary(purged = 1, deferred = 0, skipped = 0, failed = 0), summary)
        assertEquals(listOf("https://example.test/recording.wav"), deleted)
        for (table in listOf("practice_sessions", "score_views", "user_term_agreements", "user_song_settings", "lesson_progress", "refresh_tokens", "user_social_accounts")) {
            assertEquals(0, count("SELECT COUNT(*) FROM $table WHERE user_id = 1"), table)
        }
        assertEquals(listOf(40L), jdbc.queryForList("SELECT id FROM practice_sessions", Long::class.java))
        assertEquals(listOf(40L), jdbc.queryForList("SELECT id FROM evaluations", Long::class.java))
        assertEquals(1, count("SELECT COUNT(*) FROM note_results WHERE evaluation_id = 40"))
        assertEquals(1, count("SELECT COUNT(*) FROM note_results"))
        assertEquals(0, count("SELECT COUNT(*) FROM email_verifications WHERE email = 'test@example.test' COLLATE utf8mb4_bin"))
        assertEquals(1, count("SELECT COUNT(*) FROM email_verifications WHERE email = 'tést@example.test' COLLATE utf8mb4_bin"))
        val row = jdbc.queryForMap("SELECT email, password_hash, nickname, status, deleted_at, withdrawal_requested_at, purge_scheduled_at FROM users WHERE id = 1")
        assertEquals(
            mapOf("email" to null, "password_hash" to null, "nickname" to null, "status" to "DELETED",
                "deleted_at" to LocalDateTime.ofInstant(now, ZoneOffset.UTC), "withdrawal_requested_at" to null, "purge_scheduled_at" to null),
            row,
        )
        assertEquals("WITHDRAWAL_PENDING", jdbc.queryForObject("SELECT status FROM users WHERE id = 3", String::class.java))
        assertEquals(1, count("SELECT COUNT(*) FROM user_social_accounts WHERE user_id = 3"))
        assertEquals(1, count("SELECT COUNT(*) FROM score_views WHERE user_id = 4"))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `a member that cancelled after being listed is not purged`() {
        jdbc.update("UPDATE users SET purge_scheduled_at = '2026-11-21 19:00:00' WHERE id = 3")
        assertEquals(listOf(3L), repository.dueMembers(now, 0, 10))
        jdbc.update("UPDATE users SET status = 'ACTIVE', withdrawal_requested_at = NULL, purge_scheduled_at = NULL WHERE id = 3")
        assertEquals(false, repository.purge(3, now))
        assertEquals("pending@example.test", jdbc.queryForObject("SELECT email FROM users WHERE id = 3", String::class.java))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `social only member without email is still purged after retention`() {
        jdbc.update("UPDATE users SET email=NULL,password_hash=NULL,purge_scheduled_at='2026-11-21 19:00:00' WHERE id=3")
        val summary=WithdrawalPurgeService(repository, {}, Clock.fixed(now,ZoneOffset.UTC)).purgeDue()
        assertEquals(PurgeSummary(purged=1,deferred=0,skipped=0,failed=0),summary)
        assertEquals("DELETED",jdbc.queryForObject("SELECT status FROM users WHERE id=3",String::class.java))
        assertEquals(0,count("SELECT COUNT(*) FROM user_social_accounts WHERE user_id=3"))
    }

    private fun count(sql: String) = jdbc.queryForObject(sql, Int::class.java)!!

    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String) = requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}

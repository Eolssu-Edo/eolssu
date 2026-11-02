package com.ssafy.eolssu.backend.feature.practice

import com.ssafy.eolssu.backend.feature.practice.application.RecordingPurgeSummary
import com.ssafy.eolssu.backend.feature.practice.application.UnsavedRecordingPurgeService
import com.ssafy.eolssu.backend.feature.practice.domain.ExpiredRecording
import com.ssafy.eolssu.backend.feature.practice.domain.UnsavedRecordingRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

/**
 * 미저장 녹음 정리(S15P31E102-375). 고정 데이터(mapper-fixtures.sql)의 세션 1은 저장하지 않은 녹음이고
 * `ended_at`이 2026-10-22 01:02:04.123456(UTC)이다. 테스트 트랜잭션은 끝나면 되돌린다.
 */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UnsavedRecordingPurgeDatabaseTests {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var repository: UnsavedRecordingRepository

    // 세션 1의 ended_at에서 30일하고 1마이크로초 뒤. 컷오프는 세션 1보다 1마이크로초 늦고 세션 50과 같다.
    private val now = Instant.parse("2026-11-21T01:02:04.123457Z")
    private val cutoff = LocalDateTime.of(2026, 10, 22, 1, 2, 4, 123_457_000)

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `only unsaved recordings older than thirty days lose their key and file`() {
        session(50, endedAt = "2026-10-22 01:02:04.123457")                                       // 정확히 30일: 아직 아님
        session(51, endedAt = "2026-10-01 00:00:00", savedAt = "2026-10-01 00:01:00")             // 저장한 기록
        session(52, endedAt = "2026-10-01 00:00:00", url = null)                                  // 녹음 없음
        session(53, endedAt = "2026-10-01 00:00:00", userId = 3)                                  // 다른 회원도 대상
        jdbc.update("UPDATE practice_sessions SET saved_at = '2026-10-01 00:01:00', record_deleted_at = '2026-10-02 00:00:00' WHERE id = 52")

        val deleted = mutableListOf<String>()
        val summary = UnsavedRecordingPurgeService(repository, { deleted += it }, Clock.fixed(now, ZoneOffset.UTC)).purgeExpired()

        assertEquals(RecordingPurgeSummary(deleted = 2, skipped = 0, restored = 0, failed = 0), summary)
        assertEquals(listOf("https://example.test/recording.wav", "recordings/53/a.m4a"), deleted)
        assertNull(url(1))
        assertNull(url(53))
        assertEquals("recordings/50/a.m4a", url(50))
        assertEquals("recordings/51/a.m4a", url(51))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `a recording saved after it was listed keeps its key`() {
        val listed = repository.findExpired(cutoff, 0, 10)
        assertEquals(listOf(ExpiredRecording(1, "https://example.test/recording.wav")), listed)

        jdbc.update("UPDATE practice_sessions SET saved_at = '2026-11-21 01:00:00' WHERE id = 1")

        assertFalse(repository.clear(listed.single(), cutoff))
        assertEquals("https://example.test/recording.wav", url(1))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `a key that changed or is no longer past the cutoff is not cleared`() {
        val listed = repository.findExpired(cutoff, 0, 10).single()

        assertFalse(repository.clear(listed.copy(key = "recordings/1/other.m4a"), cutoff))
        assertFalse(repository.clear(listed, LocalDateTime.of(2026, 10, 22, 1, 2, 4, 123_456_000)))
        assertEquals("https://example.test/recording.wav", url(1))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `a key is restored while it is still empty even when the record was saved in between`() {
        val listed = repository.findExpired(cutoff, 0, 10).single()
        assertTrue(repository.clear(listed, cutoff))
        jdbc.update("UPDATE practice_sessions SET saved_at = '2026-11-21 01:00:00' WHERE id = 1")

        assertTrue(repository.restore(listed))
        assertEquals("https://example.test/recording.wav", url(1))
        assertFalse(repository.restore(listed))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `a key is not restored into a deleted record`() {
        val listed = repository.findExpired(cutoff, 0, 10).single()
        assertTrue(repository.clear(listed, cutoff))
        jdbc.update("UPDATE practice_sessions SET saved_at = '2026-11-21 01:00:00', record_deleted_at = '2026-11-21 01:01:00' WHERE id = 1")

        assertFalse(repository.restore(listed))
        assertNull(url(1))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `a file that cannot be deleted stays referenced for the next run`() {
        val summary = UnsavedRecordingPurgeService(
            repository,
            { throw IllegalStateException("storage down") },
            Clock.fixed(now, ZoneOffset.UTC),
        ).purgeExpired()

        assertEquals(RecordingPurgeSummary(deleted = 0, skipped = 0, restored = 1, failed = 0), summary)
        assertEquals("https://example.test/recording.wav", url(1))
    }

    private fun session(id: Long, endedAt: String, savedAt: String? = null, url: String? = "recordings/$id/a.m4a", userId: Long = 1) {
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, score_version, bpm, bpm_unit, start_note_idx, end_note_idx, recording_url, status, started_at, ended_at, saved_at) " +
                "VALUES (?, ?, 1, 1, 90, 'DOTTED_QUARTER', 0, 0, ?, 'DONE', ?, ?, ?)",
            id, userId, url, endedAt, endedAt, savedAt,
        )
    }

    private fun url(id: Long): String? =
        jdbc.queryForObject("SELECT recording_url FROM practice_sessions WHERE id = ?", String::class.java, id)

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

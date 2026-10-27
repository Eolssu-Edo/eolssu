package com.ssafy.eolssu.backend

import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.EmailVerificationMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.EmailVerificationRow
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.RefreshTokenMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.RefreshTokenRow
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.UserSocialAccountMapper
import com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence.UserSocialAccountRow
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.EvaluationMapper
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.EvaluationRow
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.NoteResultMapper
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.NoteResultRow
import com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence.FingeringChartMapper
import com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence.FingeringChartRow
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.ScoreViewMapper
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.ScoreViewRow
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentMapper
import com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence.InstrumentRow
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanMapper
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.LessonMapper
import com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.LessonRow
import com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.LessonProgressMapper
import com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence.LessonProgressRow
import com.ssafy.eolssu.backend.feature.notation.infrastructure.persistence.NotationSymbolMapper
import com.ssafy.eolssu.backend.feature.notation.infrastructure.persistence.NotationSymbolRow
import com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence.PitchMapper
import com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence.PitchRow
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionRow
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongMapper
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.SongRow
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.TermMapper
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.TermRow
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.UserTermAgreementMapper
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.UserTermAgreementRow
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserRow
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserSongSettingMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserSongSettingRow
import java.sql.SQLException
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseIntegrationTests {
    @Autowired
    private lateinit var flyway: Flyway

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `applies V1 and V2 on MySQL 8_4 and does not apply them again`() {
        assertEquals("8.4", jdbc.queryForObject("SELECT SUBSTRING_INDEX(VERSION(), '.', 2)", String::class.java))
        assertEquals("2", assertNotNull(flyway.info().current()).version.toString())
        val tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
            String::class.java,
        ).toSet()
        assertEquals(
            setOf(
                "pitches", "instruments", "fingering_charts", "jangdans", "songs", "notation_symbols",
                "users", "practice_sessions", "evaluations", "note_results", "lessons", "lesson_progress",
                "user_social_accounts", "terms", "user_term_agreements", "email_verifications", "refresh_tokens",
                "user_song_settings", "score_views", "flyway_schema_history",
            ),
            tables,
        )
        assertEquals(0, flyway.migrate().migrationsExecuted)
        assertTrue(flyway.configuration.isCleanDisabled)
        assertFalse(flyway.configuration.isBaselineOnMigrate)
        assertEquals("+00:00", jdbc.queryForObject("SELECT @@session.time_zone", String::class.java))
        flyway.validate()
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `maps all nineteen tables including nullable JSON Unicode and microsecond values`() {
        val time = LocalDateTime.parse("2026-10-22T01:02:03.123456")
        assertEquals(PitchRow(1, "황", -1, "僙", "배황", -12), mapper<PitchMapper>().findById(1))
        assertNull(mapper<PitchMapper>().findById(999))
        assertEquals(
            InstrumentRow(1, "DANSO", "단소", 1, 2, 50, false, time, time),
            mapper<InstrumentMapper>().findById(1),
        )
        assertEquals(
            FingeringChartRow(1, 1, 2, "https://example.test/fingering.png", null, time, time),
            mapper<FingeringChartMapper>().findById(1),
        )
        val jangdan = assertNotNull(mapper<JangdanMapper>().findById(1))
        assertEquals(
            JangdanRow(1, "세마치", "", 90, "DOTTED_QUARTER", "테스트", time, time),
            jangdan.copy(pattern = ""),
        )
        assertJson("""{"beats":["덩","덕"],"group":[2]}""", jangdan.pattern)
        val song = assertNotNull(mapper<SongMapper>().findById(1))
        assertEquals(
            SongRow(1, 1, 1, "아리랑", "경기 민요", 2, 90, "", 3, "황", 1, null, true, "PUBLISHED", time, time, null),
            song.copy(scoreJson = ""),
        )
        assertJson("""{"notes":[{"yul":"황"}]}""", song.scoreJson)
        assertEquals(
            NotationSymbolRow(1, "－", "뻗는표", "음을 이어 연주", null, time, time),
            mapper<NotationSymbolMapper>().findById(1),
        )
        assertEquals("-", assertNotNull(mapper<NotationSymbolMapper>().findById(2)).symbol)
        assertEquals(
            UserRow(1, "test@example.test", "test-fixture-hash", "연습자", 1, "HANGUL", "ACTIVE", null, null, null, time, time, null),
            mapper<UserMapper>().findById(1),
        )
        assertEquals(
            UserRow(2, null, null, null, 1, "HANJA", "DELETED", time, null, null, time, time, time),
            mapper<UserMapper>().findById(2),
        )
        assertEquals(
            UserRow(3, "pending@example.test", null, "대기 회원", 1, "HANJA", "WITHDRAWAL_PENDING", null, time, time.plusDays(30), time, time, null),
            mapper<UserMapper>().findById(3),
        )
        assertEquals(
            PracticeSessionRow(
                1, 1, 1, null, null, 1, 90, "DOTTED_QUARTER", null, null, 0, 0, "https://example.test/recording.wav", "DONE", null,
                time, time.plusSeconds(1), null, null, null, null, time,
            ),
            mapper<PracticeSessionMapper>().findById(1),
        )
        assertEquals(
            PracticeSessionRow(
                2, 1, 1, 7, 1, 3, 90, "DOTTED_QUARTER", "practice-key", "a".repeat(64), 0, 0, null, "DONE", null,
                time, time.plusSeconds(1), time, "다시 연습", time, time, time,
            ),
            mapper<PracticeSessionMapper>().findById(2),
        )
        assertEquals(
            EvaluationRow(1, 1, 85, 80, 90, 85, "test-v1", time),
            mapper<EvaluationMapper>().findById(1),
        )
        assertEquals(
            NoteResultRow(1, 1, 0, 2, null, null, -16, null, "MISSED"),
            mapper<NoteResultMapper>().findById(1),
        )
        val lesson = assertNotNull(mapper<LessonMapper>().findById(1))
        assertEquals(
            LessonRow(1, "YUL", null, 1, "황종", "", "PUBLISHED", time, time),
            lesson.copy(contentJson = ""),
        )
        assertJson("""{"yul":"황"}""", lesson.contentJson)
        assertEquals(
            LessonProgressRow(1, 1, 1, null, null, time, time),
            mapper<LessonProgressMapper>().findById(1),
        )
        assertEquals(
            UserSocialAccountRow(1, 3, "NAVER", "AbC-네이버", time),
            mapper<UserSocialAccountMapper>().findById(1),
        )
        assertEquals(
            TermRow(1, "SERVICE_TERMS", "2026-10", "서비스 이용약관", "https://example.test/terms", true, 1, time, time),
            mapper<TermMapper>().findById(1),
        )
        assertEquals(UserTermAgreementRow(1, 1, 1, false, time), mapper<UserTermAgreementMapper>().findById(1))
        assertEquals(
            EmailVerificationRow(1, "test@example.test", "SIGN_UP", "code-hash", 2, time, null, null, null, null, time, time),
            mapper<EmailVerificationMapper>().findById(1),
        )
        assertEquals(
            EmailVerificationRow(2, "test@example.test", "SIGN_UP", "code-hash", 0, time, time, "b".repeat(64), time, time, null, time),
            mapper<EmailVerificationMapper>().findById(2),
        )
        assertEquals(
            RefreshTokenRow(1, 1, "c".repeat(64), time, time, "ROTATED", time),
            mapper<RefreshTokenMapper>().findById(1),
        )
        assertEquals(
            RefreshTokenRow(2, 1, "d".repeat(64), time, null, null, time),
            mapper<RefreshTokenMapper>().findById(2),
        )
        assertEquals(UserSongSettingRow(1, 1, 1, 72, time, time), mapper<UserSongSettingMapper>().findById(1))
        assertEquals(
            ScoreViewRow(1, 1, 1, "view-key", time, time.plusMinutes(12), 690, 600, time),
            mapper<ScoreViewMapper>().findById(1),
        )
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `keeps foreign keys check constraints and common lesson uniqueness`() {
        assertMysqlError(1452) {
            jdbc.update("INSERT INTO fingering_charts (instrument_id, pitch_id, image_url) VALUES (999, 2, 'test')")
        }
        assertMysqlError(3819) {
            jdbc.update("UPDATE songs SET status = 'UNKNOWN' WHERE id = 1")
        }
        assertMysqlError(1062) {
            jdbc.update("INSERT INTO lessons (kind, instrument_id, seq, title, content_json, status) VALUES ('YUL', NULL, 1, '중복', '{}', 'DRAFT')")
        }
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `keeps V2 account and withdrawal constraints`() {
        jdbc.update("INSERT INTO users (instrument_id) VALUES (1)")
        assertMysqlError(3819, "chk_users_password_email") {
            jdbc.update("INSERT INTO users (password_hash, instrument_id) VALUES ('hash', 1)")
        }
        assertMysqlError(3819, "chk_users_status") {
            jdbc.update("UPDATE users SET status = 'UNKNOWN' WHERE id = 1")
        }
        assertMysqlError(3819, "chk_users_withdrawal") {
            jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING' WHERE id = 1")
        }
        assertMysqlError(3819, "chk_users_withdrawal_times") {
            jdbc.update("UPDATE users SET purge_scheduled_at = NULL WHERE id = 3")
        }
        assertMysqlError(3819, "chk_users_deleted") {
            jdbc.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = 1")
        }
        assertMysqlError(3819, "chk_users_deleted_personal") {
            jdbc.update(
                "UPDATE users SET status = 'DELETED', withdrawal_requested_at = NULL, purge_scheduled_at = NULL, " +
                    "deleted_at = CURRENT_TIMESTAMP(6) WHERE id = 3",
            )
        }
        assertMysqlError(1062, "user_social_accounts.uq_user_social_accounts_provider_user") {
            jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_user_id) VALUES (1, 'NAVER', 'AbC-네이버')")
        }
        jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_user_id) VALUES (1, 'NAVER', 'abc-네이버')")
        assertMysqlError(1062, "user_social_accounts.uq_user_social_accounts_user_provider") {
            jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_user_id) VALUES (1, 'NAVER', 'other')")
        }
        assertMysqlError(3819, "chk_user_social_accounts_provider") {
            jdbc.update("INSERT INTO user_social_accounts (user_id, provider, provider_user_id) VALUES (1, 'APPLE', 'apple-id')")
        }
        assertMysqlError(1062, "terms.uq_terms_code_version") {
            jdbc.update(
                "INSERT INTO terms (code, version, title, content_url, is_required, seq, effective_at) " +
                    "VALUES ('SERVICE_TERMS', '2026-10', '중복', 'x', TRUE, 1, CURRENT_TIMESTAMP(6))",
            )
        }
        assertMysqlError(1062, "user_term_agreements.uq_user_term_agreements_user_term") {
            jdbc.update("INSERT INTO user_term_agreements (user_id, term_id, agreed) VALUES (1, 1, TRUE)")
        }
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `keeps V2 verification and token constraints`() {
        assertMysqlError(3819, "chk_email_verifications_purpose") {
            jdbc.update("UPDATE email_verifications SET purpose = 'LOGIN' WHERE id = 1")
        }
        assertMysqlError(3819, "chk_email_verifications_token") {
            jdbc.update("UPDATE email_verifications SET verified_at = NULL WHERE id = 2")
        }
        assertMysqlError(3819, "chk_email_verifications_attempts") {
            jdbc.update("UPDATE email_verifications SET attempt_count = -1 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_email_verifications_consumed") {
            jdbc.update("UPDATE email_verifications SET consumed_at = CURRENT_TIMESTAMP(6) WHERE id = 1")
        }
        assertMysqlError(1062, "email_verifications.uq_email_verifications_token") {
            jdbc.update("UPDATE email_verifications SET token_hash = REPEAT('b', 64), verified_at = expires_at, token_expires_at = expires_at WHERE id = 1")
        }
        assertMysqlError(3819, "chk_refresh_tokens_revoked") {
            jdbc.update("UPDATE refresh_tokens SET revoke_reason = NULL WHERE id = 1")
        }
        assertMysqlError(3819, "chk_refresh_tokens_revoke_reason") {
            jdbc.update("UPDATE refresh_tokens SET revoke_reason = 'EXPIRED' WHERE id = 1")
        }
        assertMysqlError(1062, "refresh_tokens.uq_refresh_tokens_token") {
            jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (1, REPEAT('d', 64), CURRENT_TIMESTAMP(6))")
        }
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `keeps V2 catalog practice record and score view constraints`() {
        assertMysqlError(1062, "user_song_settings.uq_user_song_settings_user_song") {
            jdbc.update("INSERT INTO user_song_settings (user_id, song_id, bpm) VALUES (1, 1, 80)")
        }
        assertMysqlError(3819, "chk_user_song_settings_bpm") {
            jdbc.update("UPDATE user_song_settings SET bpm = 10 WHERE id = 1")
        }
        assertMysqlError(1364) {
            jdbc.update("INSERT INTO jangdans (name, pattern, default_bpm) VALUES ('굿거리', '{}', 66)")
        }
        assertMysqlError(3819, "chk_jangdans_bpm_unit") {
            jdbc.update("UPDATE jangdans SET bpm_unit = 'HALF' WHERE id = 1")
        }
        assertMysqlError(3819, "chk_songs_score_version") {
            jdbc.update("UPDATE songs SET score_version = 0 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_score_version") {
            jdbc.update("UPDATE practice_sessions SET score_version = 0 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_bpm_unit") {
            jdbc.update("UPDATE practice_sessions SET bpm_unit = 'HALF' WHERE id = 1")
        }
        assertMysqlError(1062, "practice_sessions.uq_sessions_user_idempotency") {
            jdbc.update(
                "INSERT INTO practice_sessions (user_id, song_id, bpm, bpm_unit, idempotency_key, request_hash, end_note_idx, started_at) " +
                    "VALUES (1, 1, 90, 'DOTTED_QUARTER', 'practice-key', REPEAT('e', 64), 0, CURRENT_TIMESTAMP(6))",
            )
        }
        assertMysqlError(3819, "chk_practice_sessions_idempotency") {
            jdbc.update(
                "INSERT INTO practice_sessions (user_id, song_id, bpm, bpm_unit, idempotency_key, end_note_idx, started_at) " +
                    "VALUES (1, 1, 90, 'DOTTED_QUARTER', 'other-key', 0, CURRENT_TIMESTAMP(6))",
            )
        }
        assertMysqlError(1452) {
            jdbc.update("UPDATE practice_sessions SET parent_session_id = 999 WHERE id = 2")
        }
        assertMysqlError(1062, "score_views.uq_score_views_user_idempotency") {
            jdbc.update(
                "INSERT INTO score_views (user_id, song_id, idempotency_key, started_at, ended_at, visible_seconds, counted_seconds) " +
                    "VALUES (1, 1, 'view-key', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, 0)",
            )
        }
        assertMysqlError(3819, "chk_score_views_counted") {
            jdbc.update("UPDATE score_views SET counted_seconds = 691 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_score_views_counted") {
            jdbc.update("UPDATE score_views SET visible_seconds = -1, counted_seconds = 0 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_score_views_period") {
            jdbc.update("UPDATE score_views SET ended_at = started_at - INTERVAL 1 SECOND WHERE id = 1")
        }
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `deletes a member's practice records in one statement during withdrawal purge`() {
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, parent_session_id, bpm, bpm_unit, end_note_idx, started_at) " +
                "VALUES (3, 3, 1, NULL, 90, 'DOTTED_QUARTER', 0, CURRENT_TIMESTAMP(6)), " +
                "(4, 3, 1, 3, 90, 'DOTTED_QUARTER', 0, CURRENT_TIMESTAMP(6)), " +
                "(5, 1, 1, 3, 90, 'DOTTED_QUARTER', 0, CURRENT_TIMESTAMP(6))",
        )
        assertEquals(2, jdbc.update("DELETE FROM practice_sessions WHERE user_id = 3 ORDER BY id"))
        assertNull(jdbc.queryForObject("SELECT parent_session_id FROM practice_sessions WHERE id = 5", Long::class.javaObjectType))
        assertEquals(1L, jdbc.queryForObject("SELECT parent_session_id FROM practice_sessions WHERE id = 2", Long::class.java))
    }

    private inline fun <reified T : Any> mapper(): T = context.getBean(T::class.java)

    private fun assertJson(expected: String, actual: String) {
        val json = JsonMapper.builder().build()
        assertEquals(json.readTree(expected), json.readTree(actual))
    }

    private fun assertMysqlError(code: Int, constraint: String? = null, action: () -> Unit) {
        val failure = assertFailsWith<DataAccessException>(block = action)
        val sqlError = generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<SQLException>().first()
        assertEquals(code, sqlError.errorCode)
        when {
            constraint == null -> Unit
            code == 3819 -> assertEquals("Check constraint '$constraint' is violated.", sqlError.message)
            code == 1062 -> assertTrue(assertNotNull(sqlError.message).endsWith("for key '$constraint'"), sqlError.message)
            else -> error("constraint name check is only defined for 3819 and 1062")
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }

        private fun requiredEnvironment(name: String): String =
            requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}

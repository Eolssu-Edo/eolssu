package com.ssafy.eolssu.backend

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteJudgement
import com.ssafy.eolssu.backend.feature.analysis.domain.NoteVerdict
import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.domain.RecordingReadUrls
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.ScoreNotesException
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
import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewSegment
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.MyBatisPracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.MyBatisScoreViewRepository
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
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadExpiryService
import com.ssafy.eolssu.backend.feature.practice.application.UploadRecording
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingMetadata
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingSessionExpiryRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import com.ssafy.eolssu.backend.feature.practice.domain.UploadedRecording
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.MyBatisPracticeSessionDetails
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionRow
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.UnavailableRecordingStorage
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RecordApplicationEvents
class DatabaseIntegrationTests {
    @Autowired
    private lateinit var events: ApplicationEvents

    @Autowired
    private lateinit var flyway: Flyway

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `applies every migration on MySQL 8_4 and does not apply them again`() {
        assertEquals("8.4", jdbc.queryForObject("SELECT SUBSTRING_INDEX(VERSION(), '.', 2)", String::class.java))
        assertEquals("7", assertNotNull(flyway.info().current()).version.toString())
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
        assertEquals(PitchRow(1, "중", 0, "仲", "중", 5), mapper<PitchMapper>().findById(1))
        assertEquals(PitchRow(11, "중", 2, "㴢", "중청중", 29), mapper<PitchMapper>().findById(11))
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
            UserRow(3, "pending@example.test", "test-fixture-hash-3", "대기 회원", 1, "HANJA", "WITHDRAWAL_PENDING", null, time, time.plusDays(30), time, time, null),
            mapper<UserMapper>().findById(3),
        )
        assertEquals(
            PracticeSessionRow(
                1, 1, 1, null, null, 1, 90, "DOTTED_QUARTER", null, null, 0, 0, "https://example.test/recording.wav",
                20_971_520, 600_500, 48_000, 2, "DONE", null, 0, null,
                time, time.plusSeconds(1), null, null, null, null, time,
            ),
            mapper<PracticeSessionMapper>().findById(1),
        )
        assertEquals(
            PracticeSessionRow(
                2, 1, 1, 7, 1, 3, 90, "DOTTED_QUARTER", "practice-key", "a".repeat(64), 0, 0, null,
                null, null, null, null, "DONE", null, 0, null,
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
        assertEquals(
            1,
            jdbc.update("INSERT INTO users (email, password_hash, nickname, instrument_id) VALUES ('new@example.test', 'hash', '새 회원', 1)"),
        )
        assertMysqlError(3819, "users_chk_2") {
            jdbc.update("INSERT INTO users (instrument_id) VALUES (1)")
        }
        assertMysqlError(3819, "users_chk_2") {
            jdbc.update("INSERT INTO users (email, nickname, instrument_id) VALUES ('social@example.test', '간편 회원', 1)")
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
        assertEquals(1, jdbc.update("UPDATE user_song_settings SET bpm = 20 WHERE id = 1"))
        assertEquals(1, jdbc.update("UPDATE user_song_settings SET bpm = 240 WHERE id = 1"))
        assertMysqlError(3819, "chk_user_song_settings_bpm") {
            jdbc.update("UPDATE user_song_settings SET bpm = 19 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_user_song_settings_bpm") {
            jdbc.update("UPDATE user_song_settings SET bpm = 241 WHERE id = 1")
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
        assertEquals(1, jdbc.update("UPDATE practice_sessions SET bpm = 20 WHERE id = 1"))
        assertEquals(1, jdbc.update("UPDATE practice_sessions SET bpm = 240 WHERE id = 1"))
        assertMysqlError(3819, "chk_practice_sessions_bpm") {
            jdbc.update("UPDATE practice_sessions SET bpm = 19 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_bpm") {
            jdbc.update("UPDATE practice_sessions SET bpm = 241 WHERE id = 1")
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
    fun `keeps V3 recording metadata empty until upload and all-or-nothing positive`() {
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, started_at) " +
                "VALUES (3, 1, 1, 90, 'DOTTED_QUARTER', 0, CURRENT_TIMESTAMP(6))",
        )
        assertEquals(
            listOf(null, null, null, null),
            jdbc.queryForList(
                "SELECT recording_bytes, recording_duration_ms, recording_sample_rate, recording_channels FROM practice_sessions WHERE id = 3",
            ).single().values.toList(),
        )
        assertEquals(
            1,
            jdbc.update(
                "UPDATE practice_sessions SET recording_bytes = 1, recording_duration_ms = 1, " +
                    "recording_sample_rate = 8000, recording_channels = 1 WHERE id = 3",
            ),
        )
        assertMysqlError(3819, "chk_practice_sessions_recording_meta") {
            jdbc.update("UPDATE practice_sessions SET recording_channels = NULL WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_recording_meta") {
            jdbc.update("UPDATE practice_sessions SET recording_bytes = 0 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_recording_meta") {
            jdbc.update("UPDATE practice_sessions SET recording_duration_ms = 0 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_recording_meta") {
            jdbc.update("UPDATE practice_sessions SET recording_sample_rate = 0 WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_recording_meta") {
            jdbc.update("UPDATE practice_sessions SET recording_channels = 0 WHERE id = 1")
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

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `keeps the analysis attempt and failure code consistent with the session state`() {
        // 고정 데이터의 세션 1·2는 DONE, 실패 코드 없음, 분석 시도 전(0번). 같은 트랜잭션의 MyBatis 캐시를 피하려고
        // 바꾸지 않을 세션 2로 기본값을 보고, 세션 1은 바꾼 뒤 한 번만 읽는다.
        val untouched = assertNotNull(mapper<PracticeSessionMapper>().findById(2))
        assertEquals(0, untouched.analysisAttempt)
        assertNull(untouched.analysisStartedAt)

        val startedAt = LocalDateTime.parse("2026-10-27T09:00:00.123456")
        jdbc.update("UPDATE practice_sessions SET status = 'PENDING', analysis_attempt = 1, analysis_started_at = ? WHERE id = 1", startedAt)
        val pending = assertNotNull(mapper<PracticeSessionMapper>().findById(1))
        assertEquals(1, pending.analysisAttempt)
        assertEquals(startedAt, pending.analysisStartedAt)

        listOf("NO_SOUND", "TOO_SHORT", "DECODE_ERROR", "UPLOAD_TIMEOUT", "TIMEOUT", "SERVER_ERROR").forEach {
            assertEquals(1, jdbc.update("UPDATE practice_sessions SET status = 'FAILED', fail_reason = ? WHERE id = 1", it), it)
        }
        assertMysqlError(3819, "chk_practice_sessions_fail_reason") {
            jdbc.update("UPDATE practice_sessions SET fail_reason = 'INTERRUPTED' WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_fail_reason") {
            jdbc.update("UPDATE practice_sessions SET fail_reason = 'no_sound' WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_fail_reason") {
            jdbc.update("UPDATE practice_sessions SET fail_reason = NULL WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_fail_reason") {
            jdbc.update("UPDATE practice_sessions SET status = 'DONE' WHERE id = 1")
        }
        assertMysqlError(3819, "chk_practice_sessions_analysis_attempt") {
            jdbc.update("UPDATE practice_sessions SET analysis_attempt = -1, analysis_started_at = CURRENT_TIMESTAMP(6) WHERE id = 2")
        }
        assertMysqlError(3819, "chk_practice_sessions_analysis_started") {
            jdbc.update("UPDATE practice_sessions SET analysis_attempt = 2 WHERE id = 2")
        }
        assertMysqlError(3819, "chk_practice_sessions_analysis_started") {
            jdbc.update("UPDATE practice_sessions SET analysis_started_at = CURRENT_TIMESTAMP(6) WHERE id = 2")
        }
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `reads only a member's own session with its evaluation and recording availability`() {
        // 고정 데이터: 세션 1은 평가 1과 녹음이 있고, 세션 2는 녹음이 없고 기록을 지웠다. 둘 다 회원 1의 세션이다.
        val details = MyBatisPracticeSessionDetails(mapper<PracticeSessionMapper>())

        val withEvaluation = assertNotNull(details.findOwned(1, 1))
        assertEquals(1L, withEvaluation.evaluationId)
        assertTrue(withEvaluation.recordingAvailable)
        assertEquals("DONE", withEvaluation.status)
        assertEquals(LocalDateTime.parse("2026-10-22T01:02:03.123456"), withEvaluation.startedAt)
        assertEquals(LocalDateTime.parse("2026-10-22T01:02:04.123456"), withEvaluation.endedAt)

        val deletedRecord = assertNotNull(details.findOwned(2, 1))
        assertNull(deletedRecord.evaluationId)
        assertFalse(deletedRecord.recordingAvailable)
        assertEquals("다시 연습", deletedRecord.memo)
        assertEquals(LocalDateTime.parse("2026-10-22T01:02:03.123456"), deletedRecord.savedAt)

        // 녹음 재생 가능은 두 조건이 모두 맞아야 한다: 녹음이 있어도 기록을 지웠으면, 지우지 않았어도 녹음이 없으면 false.
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, recording_url, status, started_at, record_deleted_at) " +
                "VALUES (21, 1, 1, 90, 'DOTTED_QUARTER', 0, 'recordings/21/a.m4a', 'DONE', '2026-10-22 01:02:03', '2026-10-23 01:02:03')," +
                " (22, 1, 1, 90, 'DOTTED_QUARTER', 0, NULL, 'RECORDING', '2026-10-22 01:02:03', NULL)",
        )
        assertFalse(assertNotNull(details.findOwned(21, 1)).recordingAvailable)
        assertFalse(assertNotNull(details.findOwned(22, 1)).recordingAvailable)

        assertNull(details.findOwned(1, 2))
        assertNull(details.findOwned(999, 1))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `fails only RECORDING sessions started before the upload expiry cutoff`() {
        val cutoff = LocalDateTime.parse("2026-10-27T09:20:00")
        for ((id, status, startedAt) in listOf(
            Triple(11L, "RECORDING", cutoff.minusNanos(1_000)),
            Triple(12L, "RECORDING", cutoff),
            Triple(13L, "RECORDING", cutoff.plusMinutes(1)),
            Triple(14L, "PENDING", cutoff.minusHours(2)),
        )) {
            jdbc.update(
                "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, status, started_at) " +
                    "VALUES (?, 1, 1, 90, 'DOTTED_QUARTER', 0, ?, ?)",
                id, status, startedAt,
            )
        }
        val repository = mapper<RecordingSessionExpiryRepository>()

        assertEquals(1, repository.failRecordingStartedBefore(cutoff, "UPLOAD_TIMEOUT"))
        assertEquals(0, repository.failRecordingStartedBefore(cutoff, "UPLOAD_TIMEOUT"))

        val rows = jdbc.query("SELECT id, status, fail_reason, ended_at FROM practice_sessions ORDER BY id") { rs, _ ->
            listOf(rs.getLong("id"), rs.getString("status"), rs.getString("fail_reason"), rs.getObject("ended_at"))
        }
        assertEquals(
            listOf(
                listOf(1L, "DONE", null), listOf(2L, "DONE", null),
                listOf(11L, "FAILED", "UPLOAD_TIMEOUT"), listOf(12L, "RECORDING", null),
                listOf(13L, "RECORDING", null), listOf(14L, "PENDING", null),
            ),
            rows.map { it.take(3) },
        )
        assertNull(rows.single { it[0] == 11L }[3])
        assertEquals(1, context.getBeansOfType(RecordingUploadExpiryService::class.java).size)
        // 공유 테스트 DB에서 실제 시계의 주기 작업이 고정 데이터와 겹치지 않도록 이 테스트 컨텍스트는 스케줄링을 끈다.
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).isEmpty())
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `records an upload and starts the analysis attempt only while the member's session is recording`() {
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, status, started_at) " +
                "VALUES (21, 1, 1, 90, 'DOTTED_QUARTER', 0, 'RECORDING', '2026-10-28 02:59:00.000000')",
        )
        val repository = mapper<RecordingUploadRepository>()
        val uploadedAt = LocalDateTime.parse("2026-10-28T03:00:00.123456")
        val recording = UploadedRecording("recordings/21/a.m4a", RecordingMetadata(20_971_520, 600_500, 48_000, 2), uploadedAt)

        assertEquals(RecordingUploadTarget("RECORDING", null, LocalDateTime.parse("2026-10-28T02:59:00")), repository.findOwned(21, 1))
        assertNull(repository.findOwned(21, 2))
        assertFalse(repository.markUploaded(21, 2, recording))
        assertTrue(repository.markUploaded(21, 1, recording))
        assertFalse(repository.markUploaded(21, 1, recording.copy(key = "recordings/21/b.m4a")))
        // 갱신에 성공한 한 번만 발행한다. 분석은 이 이벤트를 커밋 뒤에 받는다.
        assertEquals(listOf(RecordingUploaded(21)), events.stream(RecordingUploaded::class.java).toList())

        // MyBatis 로컬 캐시를 피해 JDBC로 읽는다.
        val row = jdbc.queryForMap(
            "SELECT status, recording_url, recording_bytes, recording_duration_ms, recording_sample_rate, recording_channels, " +
                "ended_at, analysis_attempt, analysis_started_at, fail_reason FROM practice_sessions WHERE id = 21",
        )
        assertEquals(
            listOf("PENDING", "recordings/21/a.m4a", 20_971_520, 600_500, 48_000, 2, uploadedAt, 1, uploadedAt, null),
            listOf(
                row["status"], row["recording_url"], row["recording_bytes"], row["recording_duration_ms"], row["recording_sample_rate"],
                (row["recording_channels"] as Number).toInt(), row["ended_at"], row["analysis_attempt"], row["analysis_started_at"], row["fail_reason"],
            ),
        )
        // 저장소 연결값이 없는 테스트 서버는 업로드를 503으로 답하는 저장소로 기동한다.
        assertTrue(context.getBean(RecordingStorage::class.java) is UnavailableRecordingStorage)
        assertEquals(1, context.getBeansOfType(UploadRecording::class.java).size)
        assertEquals(1, context.getBeansOfType(RecordingReadUrls::class.java).size)
        assertEquals(1, context.getBeansOfType(PracticeScoreNotes::class.java).size)
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `stores a score view segment once per member and key at microsecond precision`() {
        val repository = MyBatisScoreViewRepository(mapper())
        val segment = ScoreViewSegment(
            userId = 1,
            songId = 1,
            idempotencyKey = "segment-key",
            startedAt = Instant.parse("2026-10-27T14:50:00.123456Z"),
            endedAt = Instant.parse("2026-10-27T15:20:00.654321Z"),
            visibleSeconds = 1800,
            countedSeconds = 1500,
        )
        assertNull(repository.findByKey(1, "segment-key"))
        assertTrue(repository.insertIfAbsent(segment))
        assertEquals(segment, repository.findByKey(1, "segment-key"))
        assertEquals(segment, repository.findLatestByKey(1, "segment-key"))
        assertEquals(
            "2026-10-27 14:50:00.123456",
            jdbc.queryForObject(
                "SELECT DATE_FORMAT(started_at, '%Y-%m-%d %H:%i:%s.%f') FROM score_views WHERE idempotency_key = 'segment-key'",
                String::class.java,
            ),
        )

        assertFalse(repository.insertIfAbsent(segment.copy(countedSeconds = 1)))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM score_views WHERE idempotency_key = 'segment-key'", Int::class.java))
        assertNull(repository.findByKey(3, "segment-key"))
        assertTrue(repository.songExists(1))
        assertFalse(repository.songExists(999))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `sums practice time and done sessions per KST day of their start`() {
        // 고정 데이터: 회원 1의 체류 구간 600초와 DONE 세션 2개(점수는 1번만 85)가 모두 2026-10-22 10:02 KST에 시작
        jdbc.update(
            "INSERT INTO score_views (user_id, song_id, idempotency_key, started_at, ended_at, visible_seconds, counted_seconds) VALUES " +
                "(1, 1, 'before-midnight', '2026-10-25 14:59:59.999999', '2026-10-25 15:30:00', 1800, 100), " + // KST 25일 23:59:59
                "(1, 1, 'at-midnight', '2026-10-25 15:00:00', '2026-10-25 15:10:00', 600, 200), " + // KST 26일 00:00
                "(1, 1, 'same-day', '2026-10-26 03:00:00', '2026-10-26 03:01:00', 60, 30), " +
                "(3, 1, 'other-member', '2026-10-26 03:00:00', '2026-10-26 04:00:00', 3600, 999)",
        )
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, status, fail_reason, started_at) VALUES " +
                "(3, 1, 1, 90, 'DOTTED_QUARTER', 0, 'FAILED', 'NO_SOUND', '2026-10-26 03:00:00'), " +
                "(4, 1, 1, 90, 'DOTTED_QUARTER', 0, 'DONE', NULL, '2026-10-25 15:00:00'), " +
                "(5, 3, 1, 90, 'DOTTED_QUARTER', 0, 'DONE', NULL, '2026-10-26 03:00:00')",
        )
        jdbc.update(
            "INSERT INTO evaluations (session_id, total_score, pitch_score, timing_score, duration_score, model_version) VALUES " +
                "(3, 10, 10, 10, 10, 'test'), (4, 70, 70, 70, 70, 'test'), (5, 99, 99, 99, 99, 'test')",
        )
        val repository = MyBatisPracticeStatsRepository(mapper())
        fun day(text: String) = LocalDate.parse(text)

        assertEquals(
            mapOf(day("2026-10-22") to 600L, day("2026-10-25") to 100L, day("2026-10-26") to 230L),
            repository.dailyPracticeSeconds(1, DateRange(day("2026-10-22"), day("2026-10-26"))),
        )
        assertEquals(mapOf(day("2026-10-26") to 230L), repository.dailyPracticeSeconds(1, DateRange(day("2026-10-26"), day("2026-10-26"))))
        assertEquals(
            mapOf(day("2026-10-22") to SessionTotals(2, 1, 85), day("2026-10-26") to SessionTotals(1, 1, 70)),
            repository.dailyDoneSessions(1, DateRange(day("2026-10-22"), day("2026-10-26"))),
        )
        assertEquals(listOf(day("2026-10-26"), day("2026-10-25"), day("2026-10-22")), repository.practiceDaysUpTo(1, day("2026-10-26"), 60))
        assertEquals(listOf(day("2026-10-26"), day("2026-10-22")), repository.practiceDaysUpTo(1, day("2026-10-26"), 150))
        assertEquals(listOf(day("2026-10-25"), day("2026-10-22")), repository.practiceDaysUpTo(1, day("2026-10-25"), 60))

        val plan = jdbc.queryForList(
            // PracticeStatsMapper.sumCountedSecondsByDay와 같은 형태
            "EXPLAIN SELECT DATE(DATE_ADD(started_at, INTERVAL 9 HOUR)) AS day, SUM(counted_seconds) AS total FROM score_views " +
                "WHERE user_id = 1 AND started_at >= '2026-10-21 15:00:00' AND started_at < '2026-10-26 15:00:00' GROUP BY day",
        ).single()
        // 행이 몇 개뿐인 고정 데이터에서는 옵티마이저가 다른 인덱스를 고를 수 있어 쓸 수 있는지만 본다.
        // 회원 50명·구간 2만 개에서는 이 인덱스로 범위 조회하는 것을 확인했다(docs/plans/S15P31E102-193.md).
        assertTrue("idx_score_views_user_started" in plan["possible_keys"].toString().split(","))
    }

    // V6 시드만 읽는다. 고정 데이터를 쓰지 않으므로 다른 검사가 지운 행이 있으면 실패한다.
    @Test
    fun `seeds the danso songs that open practice sessions and turn into practice notes`() {
        assertEquals(
            mapOf("code" to "DANSO", "range_low_pitch_id" to 1L, "range_high_pitch_id" to 11L, "pitch_tolerance_cent" to 50, "is_active" to true),
            jdbc.queryForMap("SELECT code, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active FROM instruments WHERE id = 1"),
        )
        assertEquals(
            listOf("세마치 96 DOTTED_QUARTER", "굿거리 66 DOTTED_QUARTER", "중모리 88 QUARTER"),
            jdbc.queryForList("SELECT CONCAT(name, ' ', default_bpm, ' ', bpm_unit) FROM jangdans WHERE id BETWEEN 1 AND 3 ORDER BY id", String::class.java),
        )
        val songs = jdbc.queryForList(
            "SELECT id, title, jangdan_id, note_count, source FROM songs " +
                "WHERE status = 'PUBLISHED' AND is_scorable AND deleted_at IS NULL AND instrument_id = 1 AND score_version = 1 ORDER BY id",
        )
        assertEquals(
            listOf(
                "1 아리랑 1 61 ngc-standard-2022-p173", "2 도라지타령 1 91 ngc-standard-2022-p171",
                "3 밀양아리랑 1 85 ngc-standard-2022-p172", "4 늴리리야 2 68 ngc-standard-2022-p174",
            ),
            songs.map { "${it["id"]} ${it["title"]} ${it["jangdan_id"]} ${it["note_count"]} ${it["source"]}" },
        )
        val notes = context.getBean(PracticeScoreNotes::class.java)
        for (song in songs) {
            val last = (song["note_count"] as Int) - 1
            val all = notes.notesFor(PendingAnalysis(1, 1, song["id"] as Long, 1, 0, last, 96, "DOTTED_QUARTER", "recordings/1/a.m4a"))
            assertEquals((0..last).toList(), all.map { it.idx })
            assertTrue(all.all { it.pitchId in 1L..11L && it.beats > 0 })
        }
        // 아리랑 첫 음: 중(옥타브 0, V5 id 1, 반음 5), 0박에서 5/3박.
        assertEquals(
            ScoreNote(0, 5, 0.0, 5.0 / 3, false, 1),
            notes.notesFor(PendingAnalysis(1, 1, 1, 1, 0, 0, 96, "DOTTED_QUARTER", "recordings/1/a.m4a")).single(),
        )
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `turns the song score into practice notes with the seeded pitches`() {
        // 박당 2틱. 청황 · 쉼 · 임(시김새) · 중청중. 고정 데이터의 곡 1은 score_version 3이다.
        jdbc.update(
            "UPDATE songs SET score_json = ? WHERE id = 1",
            """{"schemaVersion":1,"music":{"ticksPerBeat":2},"events":[
              {"kind":"NOTE","yul":"황","octave":1,"startTick":0,"durationTick":2,"sigimsae":[]},
              {"kind":"REST","yul":null,"octave":null,"startTick":2,"durationTick":2,"sigimsae":[]},
              {"kind":"NOTE","yul":"임","octave":0,"startTick":4,"durationTick":1,"sigimsae":["추성"]},
              {"kind":"NOTE","yul":"중","octave":2,"startTick":5,"durationTick":3,"sigimsae":[]}]}""",
        )
        val notes = context.getBean(PracticeScoreNotes::class.java)
        val pending = PendingAnalysis(1, 1, 1, 3, 1, 2, 60, "JEONGGAN", "recordings/1/a.m4a")

        assertEquals(listOf(ScoreNote(1, 7, 2.0, 0.5, true, 2), ScoreNote(2, 29, 2.5, 1.5, false, 11)), notes.notesFor(pending))
        assertEquals(ScoreNote(0, 12, 0.0, 1.0, false, 4), notes.notesFor(pending.copy(startNoteIdx = 0, endNoteIdx = 0)).single())
        // 세션을 만든 뒤 악보가 바뀌었거나 곡이 없으면 음표를 만들지 않는다.
        assertFailsWith<ScoreNotesException> { notes.notesFor(pending.copy(scoreVersion = 2)) }
        assertFailsWith<ScoreNotesException> { notes.notesFor(pending.copy(songId = 999)) }
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `stores an analysis result only while the session is pending on the same attempt`() {
        val startedAt = LocalDateTime.parse("2026-10-28T01:00:00")
        for ((id, attempt) in listOf(21L to 2, 22L to 1)) {
            jdbc.update(
                "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, start_note_idx, end_note_idx, recording_url, " +
                    "recording_bytes, recording_duration_ms, recording_sample_rate, recording_channels, status, analysis_attempt, " +
                    "analysis_started_at, started_at, ended_at) " +
                    "VALUES (?, 1, 1, 60, 'JEONGGAN', 3, 4, ?, 1000, 2000, 48000, 1, 'PENDING', ?, ?, ?, ?)",
                id, "recordings/$id/a.m4a", attempt, startedAt, startedAt.minusMinutes(1), startedAt,
            )
        }
        val repository = mapper<PracticeAnalysisRepository>()
        val inTime = startedAt // 마감 기준이 시작 시각과 같으면 아직 제한 시간 안이다
        val overdue = startedAt.plusNanos(1_000) // 시작 시각이 마감 기준보다 1마이크로초 이르면 넘겼다
        val pending = assertNotNull(repository.findPending(21, inTime))
        assertEquals(PendingAnalysis(21, 2, 1, 1, 3, 4, 60, "JEONGGAN", "recordings/21/a.m4a"), pending)
        assertNull(repository.findPending(21, overdue))
        assertNull(repository.findPending(1, inTime.minusYears(1))) // 고정 데이터의 DONE 세션

        val notes = listOf(ScoreNote(3, 0, 0.0, 1.0, false, 2), ScoreNote(4, -12, 1.0, 2.0, true, 1))
        val done = AnalysisResult.Done(
            "pyin-1", 87, 90, 85, 86,
            listOf(NoteJudgement(3, NoteVerdict.PITCH, -61.6, 20, -15), NoteJudgement(4, NoteVerdict.MISSED, null, null, null)),
        )
        assertFalse(repository.saveDone(pending.copy(attempt = 1), done, notes, inTime)) // 이전 시도의 늦은 결과
        assertFalse(repository.saveDone(pending, done, notes, overdue)) // 마감 뒤에 온 결과
        assertFalse(repository.saveFailed(pending, "SERVER_ERROR", overdue))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluations WHERE session_id = 21", Int::class.java))
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM practice_sessions WHERE id = 21", String::class.java))
        assertFalse(repository.expireOverdue(21, inTime)) // 아직 제한 시간 안

        assertTrue(repository.saveDone(pending, done, notes, inTime))
        assertEquals(
            listOf(87, 90, 85, 86, "pyin-1"),
            jdbc.queryForList("SELECT total_score, pitch_score, timing_score, duration_score, model_version FROM evaluations WHERE session_id = 21")
                .single().values.map { (it as? Number)?.toInt() ?: it },
        )
        assertEquals(
            listOf(listOf(3, 2L, null, -62, 20, -15, "PITCH"), listOf(4, 1L, null, null, null, null, "MISSED")),
            jdbc.query(
                "SELECT n.note_idx, n.expected_pitch_id, n.detected_pitch_id, n.pitch_error_cent, n.onset_error_ms, n.duration_error_ms, n.verdict " +
                    "FROM note_results n JOIN evaluations e ON e.id = n.evaluation_id WHERE e.session_id = 21 ORDER BY n.note_idx",
            ) { rs, _ ->
                listOf(
                    rs.getInt(1), rs.getLong(2), rs.getObject(3), rs.getObject(4)?.let { (it as Number).toInt() },
                    rs.getObject(5)?.let { (it as Number).toInt() }, rs.getObject(6)?.let { (it as Number).toInt() }, rs.getString(7),
                )
            },
        )
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM practice_sessions WHERE id = 21", String::class.java))
        assertFalse(repository.saveDone(pending, done, notes, inTime)) // 이미 DONE
        assertFalse(repository.expireOverdue(21, overdue)) // DONE은 시간 초과로 바꾸지 않는다
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluations WHERE session_id = 21", Int::class.java))

        val failing = assertNotNull(repository.findPending(22, inTime))
        assertFalse(repository.saveFailed(failing.copy(attempt = 2), "NO_SOUND", inTime))
        assertTrue(repository.saveFailed(failing, "NO_SOUND", inTime))
        assertFalse(repository.saveFailed(failing, "TOO_SHORT", inTime))
        assertEquals(
            mapOf("status" to "FAILED", "fail_reason" to "NO_SOUND"),
            jdbc.queryForMap("SELECT status, fail_reason FROM practice_sessions WHERE id = 22"),
        )
        assertNull(repository.findPending(22, inTime))
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `expires only pending analysis attempts started before the deadline`() {
        val cutoff = LocalDateTime.parse("2026-10-28T01:00:00")
        for ((id, status, startedAt) in listOf(
            Triple(31L, "PENDING", cutoff.minusNanos(1_000)),
            Triple(32L, "PENDING", cutoff),
        )) {
            jdbc.update(
                "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, recording_url, recording_bytes, " +
                    "recording_duration_ms, recording_sample_rate, recording_channels, status, analysis_attempt, analysis_started_at, started_at, ended_at) " +
                    "VALUES (?, 1, 1, 60, 'JEONGGAN', 0, 'recordings/a.m4a', 1000, 2000, 48000, 1, ?, 1, ?, ?, ?)",
                id, status, startedAt, startedAt.minusMinutes(1), startedAt,
            )
        }
        val repository = mapper<PracticeAnalysisRepository>()

        assertTrue(repository.expireOverdue(31, cutoff))
        assertFalse(repository.expireOverdue(31, cutoff)) // 이미 TIMEOUT
        assertFalse(repository.expireOverdue(32, cutoff)) // 마감 기준과 같으면 아직 제한 시간 안
        assertEquals(
            listOf(listOf("FAILED", "TIMEOUT"), listOf("PENDING", null)),
            jdbc.query("SELECT status, fail_reason FROM practice_sessions WHERE id IN (31, 32) ORDER BY id") { rs, _ ->
                listOf(rs.getString(1), rs.getString(2))
            },
        )
    }

    // 저장 중 실패가 상태 변경까지 되돌리는지 보려면 테스트 트랜잭션 밖에서 커밋해야 한다. 넣은 행은 끝에서 지운다.
    @Test
    fun `keeps the session pending when storing the note results fails`() {
        jdbc.update("INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone) VALUES (901, '황', 0, '黃', '황', 0)")
        jdbc.update(
            "INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active, created_at, updated_at) " +
                "VALUES (901, 'ROLLBACK_TEST', '시험', 901, 901, 50, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source, created_at, updated_at) " +
                "VALUES (901, '시험', '{}', 60, 'JEONGGAN', '시험', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, status, created_at, updated_at) " +
                "VALUES (901, 'rollback@example.test', 'hash', '시험', 901, 'HANJA', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, note_count, is_scorable, status, created_at, updated_at) " +
                "VALUES (901, 901, 901, '시험', 1, 60, '{}', 1, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, recording_url, recording_bytes, recording_duration_ms, " +
                "recording_sample_rate, recording_channels, status, analysis_attempt, analysis_started_at, started_at, ended_at) " +
                "VALUES (901, 901, 901, 60, 'JEONGGAN', 0, 'recordings/901/a.m4a', 1000, 2000, 48000, 1, 'PENDING', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        try {
            val repository = mapper<PracticeAnalysisRepository>()
            val pending = assertNotNull(repository.findPending(901, LocalDateTime.parse("2000-01-01T00:00:00")))
            val done = AnalysisResult.Done("pyin-1", 50, 50, 50, 50, listOf(NoteJudgement(0, NoteVerdict.CORRECT, 0.0, 0, 0)))

            assertFailsWith<DataAccessException> { repository.saveDone(pending, done, listOf(ScoreNote(0, 0, 0.0, 1.0, false, 999_999)), LocalDateTime.parse("2000-01-01T00:00:00")) }

            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM practice_sessions WHERE id = 901", String::class.java))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluations WHERE session_id = 901", Int::class.java))
        } finally {
            jdbc.update("DELETE n FROM note_results n JOIN evaluations e ON e.id = n.evaluation_id WHERE e.session_id = 901")
            jdbc.update("DELETE FROM evaluations WHERE session_id = 901")
            jdbc.update("DELETE FROM practice_sessions WHERE id = 901")
            jdbc.update("DELETE FROM songs WHERE id = 901")
            jdbc.update("DELETE FROM users WHERE id = 901")
            jdbc.update("DELETE FROM jangdans WHERE id = 901")
            jdbc.update("DELETE FROM instruments WHERE id = 901")
            jdbc.update("DELETE FROM pitches WHERE id = 901")
        }
    }

    @Test
    @Transactional
    @Sql("/db/mapper-fixtures.sql")
    fun `starts a retry only for the owner's TIMEOUT or SERVER_ERROR session with a recording`() {
        val failedAt = LocalDateTime.parse("2026-10-28T01:00:00")
        for ((id, status, failReason, attempt, recording) in listOf(
            Retry(41, "FAILED", "TIMEOUT", 1, true),
            Retry(42, "FAILED", "SERVER_ERROR", 2, true),
            Retry(43, "FAILED", "NO_SOUND", 1, true),
            Retry(44, "FAILED", "TOO_SHORT", 1, true),
            Retry(45, "FAILED", "DECODE_ERROR", 1, true),
            Retry(46, "FAILED", "UPLOAD_TIMEOUT", 0, false),
            Retry(47, "PENDING", null, 1, true),
            Retry(48, "FAILED", "TIMEOUT", 1, false),
            Retry(49, "DONE", null, 1, true),
            // 실제로는 녹음이 없지만, 녹음 조건과 상관없이 실패 코드만으로 거절하는지 보려고 녹음이 있는 행을 둔다
            Retry(50, "FAILED", "UPLOAD_TIMEOUT", 1, true),
        )) {
            jdbc.update(
                "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, recording_url, status, fail_reason, " +
                    "analysis_attempt, analysis_started_at, started_at, ended_at) VALUES (?, 1, 1, 60, 'JEONGGAN', 0, ?, ?, ?, ?, ?, ?, ?)",
                id, if (recording) "recordings/$id/a.m4a" else null, status, failReason, attempt,
                if (attempt == 0) null else failedAt, failedAt.minusMinutes(1), failedAt,
            )
        }
        val repository = mapper<AnalysisRetryRepository>()
        val retriedAt = LocalDateTime.parse("2026-10-28T02:00:00.123456")

        assertEquals(AnalysisState("FAILED", "TIMEOUT", 1), repository.findState(41, 1))
        assertNull(repository.findState(41, 2)) // 다른 회원의 세션
        assertFalse(repository.startRetry(41, 2, 1, retriedAt))
        assertFalse(repository.startRetry(41, 1, 0, retriedAt)) // 읽은 뒤 다른 시도가 시작·실패했다
        assertTrue(repository.startRetry(41, 1, 1, retriedAt))
        assertEquals(AnalysisState("PENDING", null, 2), repository.findState(41, 1))
        assertEquals(retriedAt, jdbc.queryForObject("SELECT analysis_started_at FROM practice_sessions WHERE id = 41", LocalDateTime::class.java))
        assertFalse(repository.startRetry(41, 1, 2, retriedAt)) // 이미 PENDING

        // 분석에 넘기지 못한 시도는 SERVER_ERROR로 끝내 다시 시도할 수 있게 한다. 다른 시도·시작 시각이면 바꾸지 않는다.
        assertFalse(repository.failStart(41, 1, retriedAt))
        assertFalse(repository.failStart(41, 2, retriedAt.plusNanos(1_000)))
        assertTrue(repository.failStart(41, 2, retriedAt))
        assertEquals(AnalysisState("FAILED", "SERVER_ERROR", 2), repository.findState(41, 1))
        assertFalse(repository.failStart(41, 2, retriedAt)) // 이미 끝났다

        assertTrue(repository.startRetry(42, 1, 2, retriedAt))
        assertEquals(AnalysisState("PENDING", null, 3), repository.findState(42, 1))

        for (id in 43L..50L) {
            val before = assertNotNull(repository.findState(id, 1))
            assertFalse(repository.startRetry(id, 1, before.attempt, retriedAt), "session $id")
            assertEquals(before, repository.findState(id, 1), "session $id")
        }
    }

    private data class Retry(val id: Long, val status: String, val failReason: String?, val attempt: Int, val recording: Boolean)

    // 동시 요청은 서로 다른 연결이 보도록 테스트 트랜잭션 밖에서 커밋한다. 넣은 행은 끝에서 지운다.
    @Test
    fun `starts only one retry when two requests arrive together`() {
        jdbc.update("INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone) VALUES (902, '황', 0, '黃', '황', 0)")
        jdbc.update(
            "INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active, created_at, updated_at) " +
                "VALUES (902, 'RETRY_TEST', '시험', 902, 902, 50, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source, created_at, updated_at) " +
                "VALUES (902, '시험', '{}', 60, 'JEONGGAN', '시험', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, status, created_at, updated_at) " +
                "VALUES (902, 'retry@example.test', 'hash', '시험', 902, 'HANJA', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, note_count, is_scorable, status, created_at, updated_at) " +
                "VALUES (902, 902, 902, '시험', 1, 60, '{}', 1, TRUE, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        jdbc.update(
            "INSERT INTO practice_sessions (id, user_id, song_id, bpm, bpm_unit, end_note_idx, recording_url, status, fail_reason, " +
                "analysis_attempt, analysis_started_at, started_at, ended_at) " +
                "VALUES (902, 902, 902, 60, 'JEONGGAN', 0, 'recordings/902/a.m4a', 'FAILED', 'TIMEOUT', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
        )
        val pool = Executors.newFixedThreadPool(2)
        try {
            val repository = mapper<AnalysisRetryRepository>()
            val ready = CountDownLatch(2)
            val go = CountDownLatch(1)
            val retriedAt = LocalDateTime.parse("2026-10-28T02:00:00")
            val results = List(2) {
                pool.submit<Boolean> {
                    ready.countDown()
                    go.await()
                    repository.startRetry(902, 902, 1, retriedAt)
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            go.countDown()

            assertEquals(listOf(false, true), results.map { it.get(10, TimeUnit.SECONDS) }.sorted())
            assertEquals(AnalysisState("PENDING", null, 2), repository.findState(902, 902))
        } finally {
            pool.shutdownNow()
            jdbc.update("DELETE FROM practice_sessions WHERE id = 902")
            jdbc.update("DELETE FROM songs WHERE id = 902")
            jdbc.update("DELETE FROM users WHERE id = 902")
            jdbc.update("DELETE FROM jangdans WHERE id = 902")
            jdbc.update("DELETE FROM instruments WHERE id = 902")
            jdbc.update("DELETE FROM pitches WHERE id = 902")
        }
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
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }

        private fun requiredEnvironment(name: String): String =
            requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}

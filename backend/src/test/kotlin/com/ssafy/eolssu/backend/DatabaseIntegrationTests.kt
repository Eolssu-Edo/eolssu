package com.ssafy.eolssu.backend

import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.EvaluationMapper
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.EvaluationRow
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.NoteResultMapper
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.NoteResultRow
import com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence.FingeringChartMapper
import com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence.FingeringChartRow
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
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserRow
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
    fun `applies V1 on MySQL 8_4 and does not apply it again`() {
        assertEquals("8.4", jdbc.queryForObject("SELECT SUBSTRING_INDEX(VERSION(), '.', 2)", String::class.java))
        assertEquals("1", assertNotNull(flyway.info().current()).version.toString())
        val tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
            String::class.java,
        ).toSet()
        assertEquals(
            setOf(
                "pitches", "instruments", "fingering_charts", "jangdans", "songs", "notation_symbols",
                "users", "practice_sessions", "evaluations", "note_results", "lessons", "lesson_progress",
                "flyway_schema_history",
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
    fun `maps all twelve tables including nullable JSON Unicode and microsecond values`() {
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
            JangdanRow(1, "세마치", "", 90, "테스트", time, time),
            jangdan.copy(pattern = ""),
        )
        assertJson("""{"beats":["덩","덕"],"group":[2]}""", jangdan.pattern)
        val song = assertNotNull(mapper<SongMapper>().findById(1))
        assertEquals(
            SongRow(1, 1, 1, "아리랑", 2, 90, "", "황", 1, null, true, "PUBLISHED", time, time, null),
            song.copy(scoreJson = ""),
        )
        assertJson("""{"notes":[{"yul":"황"}]}""", song.scoreJson)
        assertEquals(
            NotationSymbolRow(1, "－", "뻗는표", "음을 이어 연주", null, time, time),
            mapper<NotationSymbolMapper>().findById(1),
        )
        assertEquals("-", assertNotNull(mapper<NotationSymbolMapper>().findById(2)).symbol)
        assertEquals(
            UserRow(1, "test@example.test", "test-fixture-hash", "연습자", 1, "HANGUL", null, time, time, null),
            mapper<UserMapper>().findById(1),
        )
        assertEquals(
            UserRow(2, null, null, null, 1, "HANJA", time, time, time, time),
            mapper<UserMapper>().findById(2),
        )
        assertEquals(
            PracticeSessionRow(1, 1, 1, 90, 0, 0, "https://example.test/recording.wav", "DONE", null, time, time.plusSeconds(1), time),
            mapper<PracticeSessionMapper>().findById(1),
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

    private inline fun <reified T : Any> mapper(): T = context.getBean(T::class.java)

    private fun assertJson(expected: String, actual: String) {
        val json = JsonMapper.builder().build()
        assertEquals(json.readTree(expected), json.readTree(actual))
    }

    private fun assertMysqlError(code: Int, action: () -> Unit) {
        val error = assertFailsWith<DataAccessException>(block = action)
        val sqlError = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<SQLException>().first()
        assertEquals(code, sqlError.errorCode)
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

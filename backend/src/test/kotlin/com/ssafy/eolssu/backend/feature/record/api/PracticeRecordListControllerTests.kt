package com.ssafy.eolssu.backend.feature.record.api

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.record.application.GetPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.SavePracticeRecord
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.RecordingFile
import com.ssafy.eolssu.backend.feature.record.domain.ScoreColumns
import com.ssafy.eolssu.backend.feature.record.domain.SongRecordSummary
import java.time.Clock
import java.time.LocalDateTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PracticeRecordListControllerTests {
    private val record = RecordSession(
        sessionId = 501, songId = 101, songTitle = "아리랑", noteCount = 6, scoreVersion = 2, startNoteIdx = 0, endNoteIdx = 5,
        bpm = 96, bpmUnit = "DOTTED_QUARTER", status = "DONE", startedAt = LocalDateTime.parse("2026-11-02T03:00:00"),
        recordingDurationMs = 48000, totalScore = 84, savedAt = LocalDateTime.parse("2026-11-02T03:02:00"), memo = "박자가 밀림",
        recordingAvailable = true, viewedAt = null,
    )
    private val queries = mutableListOf<List<Any>>()
    private val records = object : PracticeRecords {
        override fun lockStatus(sessionId: Long, userId: Long): String? = null

        override fun findSession(sessionId: Long, userId: Long): RecordSession? = null

        override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange) = Unit

        override fun songSummaries(userId: Long) = listOf(
            SongRecordSummary(101, "아리랑", 4, LocalDateTime.parse("2026-11-02T03:02:00"), 84),
            SongRecordSummary(102, "도라지타령", 1, LocalDateTime.parse("2026-10-17T12:10:00"), 71),
        )

        override fun songRecords(userId: Long, songId: Long, sort: RecordSort, offset: Long, limit: Int): List<RecordSession> {
            queries += listOf(userId, songId, sort, offset, limit)
            return listOf(record, record.copy(sessionId = 502, startNoteIdx = 2, endNoteIdx = 3, viewedAt = record.savedAt, recordingAvailable = false, memo = null))
        }

        override fun findRecording(sessionId: Long, userId: Long): RecordingFile? = error("not used")
    }
    private val columns = ScoreColumns { _, _ -> listOf(0, 0, 1, 1, 2, 2) }
    private val mvc = MockMvcBuilders.standaloneSetup(
        PracticeRecordController(SavePracticeRecord(records, columns, Clock.systemUTC()), GetPracticeRecords(records, columns)),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).setControllerAdvice(GlobalExceptionHandler()).build()

    @BeforeEach
    fun login() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build())
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `lists songs and the records of the most recent song with defaults`() {
        mvc.perform(get("/api/users/me/practice-records")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.totalRecordCount").value(5))
            .andExpect(jsonPath("$.result.songs.length()").value(2))
            .andExpect(jsonPath("$.result.songs[0].songId").value("101"))
            .andExpect(jsonPath("$.result.songs[0].title").value("아리랑"))
            .andExpect(jsonPath("$.result.songs[0].recordCount").value(4))
            .andExpect(jsonPath("$.result.songs[0].lastSavedAt").value("2026-11-02T03:02:00Z"))
            .andExpect(jsonPath("$.result.songs[0].bestScore").value(84))
            .andExpect(jsonPath("$.result.songId").value("101"))
            .andExpect(jsonPath("$.result.items[0].sessionId").value("501"))
            .andExpect(jsonPath("$.result.items[0].songId").value("101"))
            .andExpect(jsonPath("$.result.items[0].title").value("아리랑"))
            .andExpect(jsonPath("$.result.items[0].isFullSong").value(true))
            .andExpect(jsonPath("$.result.items[0].startColumnIdx").value(0))
            .andExpect(jsonPath("$.result.items[0].endColumnIdx").value(2))
            .andExpect(jsonPath("$.result.items[0].recordingDurationMs").value(48000))
            .andExpect(jsonPath("$.result.items[0].bpm").value(96))
            .andExpect(jsonPath("$.result.items[0].bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.items[0].totalScore").value(84))
            .andExpect(jsonPath("$.result.items[0].startNoteIdx").value(0))
            .andExpect(jsonPath("$.result.items[0].endNoteIdx").value(5))
            .andExpect(jsonPath("$.result.items[0].startedAt").value("2026-11-02T03:00:00Z"))
            .andExpect(jsonPath("$.result.items[0].recordingAvailable").value(true))
            .andExpect(jsonPath("$.result.items[0].memo").value("박자가 밀림"))
            .andExpect(jsonPath("$.result.items[0].isNew").value(true))
            .andExpect(jsonPath("$.result.items[1].isFullSong").value(false))
            .andExpect(jsonPath("$.result.items[1].startColumnIdx").value(1))
            .andExpect(jsonPath("$.result.items[1].recordingAvailable").value(false))
            .andExpect(jsonPath("$.result.items[1].memo").isEmpty)
            .andExpect(jsonPath("$.result.items[1].isNew").value(false))
            .andExpect(jsonPath("$.result.page").value(0))
            .andExpect(jsonPath("$.result.size").value(20))
            .andExpect(jsonPath("$.result.totalElements").value(4))
            .andExpect(jsonPath("$.result.totalPages").value(1))
        assertEquals(listOf(listOf<Any>(1L, 101L, RecordSort.NEWEST, 0L, 20)), queries)
    }

    @Test
    fun `passes the chosen song, order and page`() {
        mvc.perform(get("/api/users/me/practice-records?songId=101&sort=OLDEST&page=1&size=2")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.page").value(1))
            .andExpect(jsonPath("$.result.size").value(2))
            .andExpect(jsonPath("$.result.totalPages").value(2))
        assertEquals(listOf(listOf<Any>(1L, 101L, RecordSort.OLDEST, 2L, 2)), queries)
    }

    @Test
    fun `rejects out of range or malformed queries`() {
        for (query in listOf("page=-1", "size=0", "size=101", "songId=0", "songId=abc", "sort=LATEST", "sort=newest", "page=x")) {
            mvc.perform(get("/api/users/me/practice-records?$query")).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
        assertEquals(emptyList<List<Any>>(), queries)
    }
}

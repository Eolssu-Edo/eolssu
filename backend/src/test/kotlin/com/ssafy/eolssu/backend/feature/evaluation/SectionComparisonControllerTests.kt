package com.ssafy.eolssu.backend.feature.evaluation

import com.ssafy.eolssu.backend.feature.evaluation.api.SectionComparisonController
import com.ssafy.eolssu.backend.feature.evaluation.application.GetSectionComparison
import com.ssafy.eolssu.backend.feature.evaluation.domain.*
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class SectionComparisonControllerTests {
    private val time = Instant.parse("2026-10-29T00:00:00Z")
    private val sessions = mutableMapOf<Long, PracticeEvaluationSnapshot>()
    private val notes = mutableMapOf<Long, List<PracticeEvaluationNote>>()
    private fun save(id: Long, start: Int, end: Int, total: Int, parent: Long? = null, songId: Long = 1, status: String = "DONE", model: String = "m1") {
        sessions[id] = PracticeEvaluationSnapshot(id, songId, 1, 48, "DOTTED_QUARTER", status, time, time, id, total, total + 1, total - 1, total,
            time, null, start, end, parent, model)
    }
    private val repository = object : PracticeEvaluations {
        override fun findOwned(sessionId: Long, userId: Long) = sessions[sessionId]?.takeIf { userId == 2L }
        override fun notes(evaluationId: Long) = notes[evaluationId].orEmpty()
    }
    // 각 0: 음표 0·1, 각 1: 음표 2·3, 각 2: 음표 4·5.
    private val cells = EvaluationScoreCells { _, _ -> (0..5).map { NoteCell(it / 2, "c${it / 2}-j${it % 2}") } }
    private val mvc = MockMvcBuilders.standaloneSetup(SectionComparisonController(GetSectionComparison(repository, cells)))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).build()
    private fun login() { SecurityContextHolder.getContext().authentication=JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","HS256").subject("2").build()) }
    private fun note(idx: Int, verdict: String) = PracticeEvaluationNote(idx, verdict, null, null, null, 1, null)
    @AfterEach fun clear()=SecurityContextHolder.clearContext()

    init {
        save(10, 0, 5, 60)
        notes[10] = listOf(note(0,"PITCH"), note(1,"CORRECT"), note(2,"PITCH"), note(3,"TIMING"), note(4,"CORRECT"), note(5,"CORRECT"))
        save(11, 2, 3, 70)
        notes[11] = listOf(note(2,"PITCH"), note(3,"MISSED"))
        save(12, 2, 3, 90, parent = 11)
        notes[12] = listOf(note(2,"CORRECT"), note(3,"PITCH"))
    }

    @Test fun `parent section retake is comparable with delta corrected and remaining notes`() {
        login()
        mvc.perform(get("/api/practice-sessions/12/comparison")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.baselineSessionId").value("11"))
            .andExpect(jsonPath("$.result.comparable").value(true))
            .andExpect(jsonPath("$.result.startNoteIdx").value(2))
            .andExpect(jsonPath("$.result.endNoteIdx").value(3))
            .andExpect(jsonPath("$.result.startColumnIdx").value(1))
            .andExpect(jsonPath("$.result.endColumnIdx").value(1))
            .andExpect(jsonPath("$.result.before.totalScore").value(70))
            .andExpect(jsonPath("$.result.before.errorCount").value(2))
            .andExpect(jsonPath("$.result.after.pitchScore").value(91))
            .andExpect(jsonPath("$.result.after.errorCount").value(1))
            .andExpect(jsonPath("$.result.after.bpm").value(48))
            .andExpect(jsonPath("$.result.after.bpmUnit").value("DOTTED_QUARTER"))
            .andExpect(jsonPath("$.result.delta.totalScore").value(20))
            .andExpect(jsonPath("$.result.delta.errorCount").value(-1))
            .andExpect(jsonPath("$.result.correctedNoteIndices[0]").value(2))
            .andExpect(jsonPath("$.result.remainingErrors[0].noteIdx").value(3))
            .andExpect(jsonPath("$.result.remainingErrors[0].verdict").value("PITCH"))
    }

    @Test fun `whole song baseline is shown without scores or delta`() {
        login()
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "10")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.comparable").value(false))
            .andExpect(jsonPath("$.result.reason").isNotEmpty)
            .andExpect(jsonPath("$.result.before.totalScore").doesNotExist())
            .andExpect(jsonPath("$.result.before.errorCount").value(2))
            .andExpect(jsonPath("$.result.delta").doesNotExist())
            .andExpect(jsonPath("$.result.correctedNoteIndices[0]").value(2))
    }

    @Test fun `baseline rules follow the parent session rules`() {
        login()
        save(13, 0, 5, 50, songId = 2)
        save(14, 0, 5, 50, status = "PENDING")
        save(15, 3, 5, 50)
        mvc.perform(get("/api/practice-sessions/11/comparison")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "12")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "13")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "15")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "14")).andExpect(status().isConflict)
        mvc.perform(get("/api/practice-sessions/12/comparison").param("baselineSessionId", "99")).andExpect(status().isNotFound)
    }

    @Test fun `current session must be owned and finished`() {
        login()
        save(16, 2, 3, 0, parent = 11, status = "FAILED")
        mvc.perform(get("/api/practice-sessions/99/comparison")).andExpect(status().isNotFound)
        mvc.perform(get("/api/practice-sessions/16/comparison")).andExpect(status().isConflict)
    }
}

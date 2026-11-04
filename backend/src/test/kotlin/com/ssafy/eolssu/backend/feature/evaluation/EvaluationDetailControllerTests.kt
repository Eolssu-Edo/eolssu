package com.ssafy.eolssu.backend.feature.evaluation

import com.ssafy.eolssu.backend.feature.evaluation.api.EvaluationNotesController
import com.ssafy.eolssu.backend.feature.evaluation.api.WeakSectionsController
import com.ssafy.eolssu.backend.feature.evaluation.application.GetEvaluationNotes
import com.ssafy.eolssu.backend.feature.evaluation.application.GetWeakSections
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

class EvaluationDetailControllerTests {
    private val time = Instant.parse("2026-10-29T00:00:00Z")
    private var state = "DONE"
    private var scoreVersion = 3
    // 세션 구간 1~4. 각 0: 음표 0·1, 각 1: 음표 2·3, 각 2: 음표 4.
    private val repository = object : PracticeEvaluations {
        override fun findOwned(sessionId: Long, userId: Long) = if (sessionId == 11L && userId == 2L)
            PracticeEvaluationSnapshot(11,1,3,96,"DOTTED_QUARTER",state,time,time,5,70,60,80,70,time,2876,1,4) else null
        override fun notes(evaluationId: Long) = listOf(
            PracticeEvaluationNote(1,"CORRECT",3,10,-5,7,7),
            PracticeEvaluationNote(2,"PITCH",40,120,-80,12,13,1520),
            PracticeEvaluationNote(3,"MISSED",null,null,null,12,null),
            PracticeEvaluationNote(4,"TIMING",0,200,10,9,9),
        )
    }
    private val cells = EvaluationScoreCells { _, version ->
        if (version == scoreVersion) listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0).map { (c, j) -> NoteCell(c, "c$c-j$j") } else null
    }
    private val mvc = MockMvcBuilders.standaloneSetup(
        EvaluationNotesController(GetEvaluationNotes(repository)), WeakSectionsController(GetWeakSections(repository, cells)),
    ).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).build()
    private fun login(id: String) { SecurityContextHolder.getContext().authentication=JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","HS256").subject(id).build()) }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()

    @Test fun `notes cover the session range with stored errors and verdict counts`() {
        login("2")
        mvc.perform(get("/api/practice-sessions/11/evaluation/notes")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.evaluationId").value("5"))
            .andExpect(jsonPath("$.result.items.length()").value(4))
            .andExpect(jsonPath("$.result.items[1].noteIdx").value(2))
            .andExpect(jsonPath("$.result.items[1].expectedPitchId").value("12"))
            .andExpect(jsonPath("$.result.items[1].detectedPitchId").value("13"))
            .andExpect(jsonPath("$.result.items[1].pitchErrorCent").value(40))
            .andExpect(jsonPath("$.result.items[1].errorTypes[0]").value("PITCH"))
            .andExpect(jsonPath("$.result.items[0].errorTypes.length()").value(0))
            .andExpect(jsonPath("$.result.items[1].recordingOffsetMs").value(1520))
            .andExpect(jsonPath("$.result.items[2].detectedPitchId").doesNotExist())
            .andExpect(jsonPath("$.result.items[2].recordingOffsetMs").doesNotExist())
            .andExpect(jsonPath("$.result.errorCount").value(3))
            .andExpect(jsonPath("$.result.errorCounts.pitch").value(1))
            .andExpect(jsonPath("$.result.errorCounts.timing").value(1))
            .andExpect(jsonPath("$.result.errorCounts.duration").value(0))
            .andExpect(jsonPath("$.result.errorCounts.missed").value(1))
    }

    @Test fun `notes filter by inclusive range and errors only`() {
        login("2")
        mvc.perform(get("/api/practice-sessions/11/evaluation/notes").param("startNoteIdx","1").param("endNoteIdx","3").param("errorsOnly","true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.items.length()").value(2))
            .andExpect(jsonPath("$.result.items[0].noteIdx").value(2))
            .andExpect(jsonPath("$.result.items[1].noteIdx").value(3))
            .andExpect(jsonPath("$.result.errorCount").value(2))
            .andExpect(jsonPath("$.result.errorCounts.timing").value(0))
    }

    @Test fun `notes reject ranges outside the session or reversed`() {
        login("2")
        for ((start, end) in listOf("0" to "3", "1" to "5", "3" to "2"))
            mvc.perform(get("/api/practice-sessions/11/evaluation/notes").param("startNoteIdx",start).param("endNoteIdx",end))
                .andExpect(status().isBadRequest).andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
    }

    @Test fun `weak sections group error cells by column`() {
        login("2")
        mvc.perform(get("/api/practice-sessions/11/weak-sections")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.sessionId").value("11"))
            .andExpect(jsonPath("$.result.totalErrorCellCount").value(3))
            .andExpect(jsonPath("$.result.items.length()").value(1))
            .andExpect(jsonPath("$.result.items[0].startNoteIdx").value(2))
            .andExpect(jsonPath("$.result.items[0].endNoteIdx").value(4))
            .andExpect(jsonPath("$.result.items[0].startColumnIdx").value(1))
            .andExpect(jsonPath("$.result.items[0].endColumnIdx").value(2))
            .andExpect(jsonPath("$.result.items[0].errorCount").value(3))
            .andExpect(jsonPath("$.result.items[0].dominantError").value("MISSED"))
    }

    @Test fun `weak section limit must be between one and ten`() {
        login("2")
        for (limit in listOf("0", "11"))
            mvc.perform(get("/api/practice-sessions/11/weak-sections").param("limit", limit)).andExpect(status().isBadRequest)
        mvc.perform(get("/api/practice-sessions/11/weak-sections").param("limit", "10")).andExpect(status().isOk)
    }

    @Test fun `changed score is a state conflict`() {
        login("2");scoreVersion=4
        mvc.perform(get("/api/practice-sessions/11/weak-sections")).andExpect(status().isConflict)
    }

    @Test fun `ownership and unfinished sessions match the evaluation lookup`() {
        for (path in listOf("/api/practice-sessions/11/evaluation/notes", "/api/practice-sessions/11/weak-sections")) {
            state = "DONE"
            login("3");mvc.perform(get(path)).andExpect(status().isNotFound)
            login("2");state = "PENDING";mvc.perform(get(path)).andExpect(status().isConflict)
        }
    }
}

package com.ssafy.eolssu.backend.feature.evaluation

import com.ssafy.eolssu.backend.feature.evaluation.api.PracticeEvaluationController
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.*
import java.time.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class PracticeEvaluationControllerTests {
    private val time = LocalDateTime.parse("2026-10-29T00:00:00")
    private var state = "DONE"
    private val mapper = object : EvaluationReadMapper {
        override fun findOwned(sessionId: Long,userId: Long) = if (sessionId == 11L && userId == 2L)
            EvaluationReadRow(11,1,3,96,"DOTTED_QUARTER",state,time,time,5,84,88,80,84,time) else null
        override fun notes(evaluationId: Long) = listOf(NoteResultRow(8,5,0,1,null,null,150,0,"TIMING"))
    }
    private val mvc = MockMvcBuilders.standaloneSetup(PracticeEvaluationController(mapper))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()).build()
    private fun login(id: String) { SecurityContextHolder.getContext().authentication=JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","HS256").subject(id).build()) }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    @Test fun `owner receives stored scores and note identity without generated feedback`() {
        login("2")
        mvc.perform(get("/api/practice-sessions/11/evaluation")).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.evaluationId").value("5"))
            .andExpect(jsonPath("$.result.songId").value("1"))
            .andExpect(jsonPath("$.result.totalScore").value(84))
            .andExpect(jsonPath("$.result.notes[0].noteIdx").value(0))
            .andExpect(jsonPath("$.result.notes[0].verdict").value("TIMING"))
            .andExpect(jsonPath("$.result.feedback").doesNotExist())
    }
    @Test fun `other owner and missing session are indistinguishable`() {
        login("3");mvc.perform(get("/api/practice-sessions/11/evaluation")).andExpect(status().isNotFound)
        login("2");mvc.perform(get("/api/practice-sessions/99/evaluation")).andExpect(status().isNotFound)
    }
    @Test fun `owner unfinished session is a state conflict`() {
        login("2");state="PENDING"
        mvc.perform(get("/api/practice-sessions/11/evaluation")).andExpect(status().isConflict)
    }
}

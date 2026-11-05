package com.ssafy.eolssu.backend.feature.practice

import com.ssafy.eolssu.backend.common.error.GlobalExceptionHandler
import com.ssafy.eolssu.backend.feature.practice.api.NotePracticeCriteriaController
import com.ssafy.eolssu.backend.feature.practice.application.NotePracticeCriteriaQuery
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeCriteria
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeSigimsaeMode
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeTuning
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NotePracticeCriteriaControllerTests {
    private val criteria = NotePracticeCriteria(
        criteriaVersion = "fixture-7", pitchToleranceCent = BigDecimal("35.5"), holdMs = 420,
        minConfidence = BigDecimal("0.65"), minLevelDbfs = BigDecimal("-52"), octaveMustMatch = false,
        tuning = NotePracticeTuning.NONE, maxTuningOffsetCent = BigDecimal("20"),
        sigimsaeMode = NotePracticeSigimsaeMode.SKIP, hintAfterMs = 6500,
    )
    private val codes = mapOf(900719925474099L to "DANSO", 2L to "NOT_REGISTERED")
    private val query = NotePracticeCriteriaQuery({ codes[it] }, mapOf("DANSO" to criteria))
    private val mvc = MockMvcBuilders.standaloneSetup(NotePracticeCriteriaController(query))
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test fun `registered instrument receives every configured criterion in the common envelope`() {
        mvc.perform(get("/api/note-practice/criteria").param("instrumentId", "900719925474099"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.httpStatus").value("OK"))
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.message").value("한 음씩 연습 판정 기준 조회 성공"))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.result.instrumentId").value("900719925474099"))
            .andExpect(jsonPath("$.result.criteriaVersion").value("fixture-7"))
            .andExpect(jsonPath("$.result.pitchToleranceCent").value(35.5))
            .andExpect(jsonPath("$.result.holdMs").value(420))
            .andExpect(jsonPath("$.result.minConfidence").value(0.65))
            .andExpect(jsonPath("$.result.minLevelDbfs").value(-52))
            .andExpect(jsonPath("$.result.octaveMustMatch").value(false))
            .andExpect(jsonPath("$.result.tuning").value("NONE"))
            .andExpect(jsonPath("$.result.maxTuningOffsetCent").value(20))
            .andExpect(jsonPath("$.result.sigimsaeMode").value("SKIP"))
            .andExpect(jsonPath("$.result.hintAfterMs").value(6500))
    }

    @Test fun `missing or malformed instrument ID uses the common 400`() {
        mvc.perform(get("/api/note-practice/criteria")).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        for (id in listOf("", "0", "-1", "+1", "01", " 1", "1.0", "abc", "1234567890123456789")) {
            mvc.perform(get("/api/note-practice/criteria").param("instrumentId", id)).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test fun `unknown instrument and instrument without criteria use the common 404`() {
        for (id in listOf("3", "2")) {
            mvc.perform(get("/api/note-practice/criteria").param("instrumentId", id)).andExpect(status().isNotFound)
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.result.errorCode").value("RESOURCE_NOT_FOUND"))
        }
    }
}

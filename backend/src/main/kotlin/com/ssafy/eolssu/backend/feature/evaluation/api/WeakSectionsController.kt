package com.ssafy.eolssu.backend.feature.evaluation.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.evaluation.application.GetWeakSections
import com.ssafy.eolssu.backend.feature.evaluation.application.WeakSectionsLookup
import com.ssafy.eolssu.backend.feature.evaluation.domain.Verdict
import com.ssafy.eolssu.backend.feature.evaluation.domain.WeakSection
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

/** EVA-03. 화면 문구는 FE가 숫자로 조합하며 reason은 보조 설명이다. */
@RestController
@Profile("db | ops")
class WeakSectionsController(private val getWeakSections: GetWeakSections) {
    @GetMapping("/api/practice-sessions/{sessionId}/weak-sections")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable sessionId: Long, @RequestParam(defaultValue = "1") limit: Int): ResponseEntity<*> {
        if (limit !in 1..MAX_LIMIT) return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))
        return when (val outcome = getWeakSections.execute(requireNotNull(jwt.subject).toLong(), sessionId, limit)) {
            WeakSectionsLookup.NotFound -> ResponseEntity.status(404).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
            WeakSectionsLookup.NotReady, WeakSectionsLookup.ScoreChanged ->
                ResponseEntity.status(409).body(ErrorResponse.of(ErrorCode.STATE_CONFLICT))
            is WeakSectionsLookup.Found -> ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "취약 구간 추천 조회 성공", WeakSectionsResult(
                outcome.sessionId.toString(), outcome.report.totalErrorCellCount, outcome.report.sections.map { it.toItem() },
            )))
        }
    }

    private fun WeakSection.toItem() = WeakSectionItem(
        startNoteIdx, endNoteIdx, startColumn, endColumn, errorCellCount, dominantError, REASONS[dominantError],
    )

    private companion object {
        const val MAX_LIMIT = 10
        val REASONS = mapOf(
            Verdict.PITCH to "음높이 오류가 가장 많은 구간이에요",
            Verdict.TIMING to "박자 오류가 가장 많은 구간이에요",
            Verdict.LENGTH to "음 길이 오류가 가장 많은 구간이에요",
            Verdict.MISSED to "놓친 음이 가장 많은 구간이에요",
        )
    }
}

data class WeakSectionsResult(val sessionId: String, val totalErrorCellCount: Int, val items: List<WeakSectionItem>)
data class WeakSectionItem(val startNoteIdx: Int, val endNoteIdx: Int, val startColumnIdx: Int, val endColumnIdx: Int,
    val errorCount: Int, val dominantError: String, val reason: String?)

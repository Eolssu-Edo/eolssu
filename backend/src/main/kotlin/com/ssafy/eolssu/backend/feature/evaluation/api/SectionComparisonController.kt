package com.ssafy.eolssu.backend.feature.evaluation.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.evaluation.application.GetSectionComparison
import com.ssafy.eolssu.backend.feature.evaluation.application.SectionComparisonLookup
import com.ssafy.eolssu.backend.feature.evaluation.domain.NotComparable
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

/** EVA-04. baselineSessionId를 생략하면 세션을 만들 때 넘긴 parentSessionId와 비교한다. 점수는 비교하지 않는다. */
@RestController
@Profile("db | ops")
class SectionComparisonController(private val getComparison: GetSectionComparison) {
    @GetMapping("/api/practice-sessions/{sessionId}/comparison")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable sessionId: Long, @RequestParam(required = false) baselineSessionId: Long?): ResponseEntity<*> =
        when (val outcome = getComparison.execute(requireNotNull(jwt.subject).toLong(), sessionId, baselineSessionId)) {
            SectionComparisonLookup.NotFound -> ResponseEntity.status(404).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
            SectionComparisonLookup.NotReady, SectionComparisonLookup.ScoreChanged ->
                ResponseEntity.status(409).body(ErrorResponse.of(ErrorCode.STATE_CONFLICT))
            SectionComparisonLookup.InvalidBaseline -> ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))
            is SectionComparisonLookup.Found -> with(outcome.comparison) {
                ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "구간 재평가의 이전·이번 결과 비교 성공", SectionComparisonApiResult(
                    outcome.baselineSessionId.toString(), notComparable == null, notComparable?.let { REASONS.getValue(it) },
                    startNoteIdx, endNoteIdx, outcome.startColumn, outcome.endColumn,
                    BeforeErrors(beforeErrorCount), AfterScores(
                        after.totalScore, after.pitchScore, after.timingScore, after.durationScore, after.errorCount, outcome.bpm, outcome.bpmUnit,
                    ),
                    delta?.let { ErrorDelta(it.errorCount) }, correctedNoteIndices,
                    remainingErrors.map { RemainingError(it.noteIdx, it.verdict) },
                )))
            }
        }

    private companion object {
        val REASONS = mapOf(
            NotComparable.SCORE_VERSION to "이전 연주 뒤 악보가 바뀌어 같은 음표끼리 비교할 수 없어요",
            NotComparable.MODEL to "채점 모델이 달라 오류 수를 바로 비교할 수 없어요",
            NotComparable.TEMPO to "연주 속도가 달라 오류 수를 바로 비교할 수 없어요",
        )
    }
}

data class SectionComparisonApiResult(
    val baselineSessionId: String, val comparable: Boolean, val reason: String?, val startNoteIdx: Int, val endNoteIdx: Int,
    val startColumnIdx: Int, val endColumnIdx: Int, val before: BeforeErrors, val after: AfterScores, val delta: ErrorDelta?,
    val correctedNoteIndices: List<Int>, val remainingErrors: List<RemainingError>,
)
/** 이전 결과는 비교 구간 오류 수만 준다. 악보 버전이 다르면 null이다. */
data class BeforeErrors(val errorCount: Int?)
data class AfterScores(val totalScore: Int, val pitchScore: Int, val timingScore: Int, val durationScore: Int, val errorCount: Int,
    val bpm: Int, val bpmUnit: String)
data class ErrorDelta(val errorCount: Int)
data class RemainingError(val noteIdx: Int, val verdict: String)

package com.ssafy.eolssu.backend.feature.evaluation.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.evaluation.application.GetSectionComparison
import com.ssafy.eolssu.backend.feature.evaluation.application.SectionComparisonLookup
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

/** EVA-04. 생략한 baselineSessionId는 parentSessionId, 같은 곡 직전 세션 순서로 채운다. 비교할 세션이 없으면 200과 null 필드다. */
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
                ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "연주 결과 비교 성공", SectionComparisonApiResult(
                    outcome.baseline?.sessionId?.toString(), outcome.baseline?.startedAt, outcome.baseline?.source?.name,
                    startNoteIdx, endNoteIdx, outcome.startColumn, outcome.endColumn, outcome.isFullSong,
                    scoreComparable, errorComparable, notComparableReasons.map { it.name }, bpmChanged,
                    outcome.baseline?.let { BeforeResult(errorCount = beforeErrorCount, bpm = it.bpm, bpmUnit = it.bpmUnit) },
                    AfterScores(after.totalScore, after.pitchScore, after.timingScore, after.durationScore, after.errorCount, outcome.bpm, outcome.bpmUnit),
                    outcome.baseline?.let { ResultDelta(errorCount = errorDelta) }, correctedNoteIndices,
                    remainingErrors.map { RemainingError(it.noteIdx, it.verdict) },
                )))
            }
        }
}

data class SectionComparisonApiResult(
    val baselineSessionId: String?, val baselineStartedAt: Instant?, val baselineSource: String?,
    val startNoteIdx: Int, val endNoteIdx: Int, val startColumnIdx: Int, val endColumnIdx: Int,
    @get:JsonProperty("isFullSong") val isFullSong: Boolean, val scoreComparable: Boolean, val errorComparable: Boolean,
    val notComparableReasons: List<String>, val bpmChanged: Boolean, val before: BeforeResult?, val after: AfterScores,
    val delta: ResultDelta?, val correctedNoteIndices: List<Int>, val remainingErrors: List<RemainingError>,
)
/** 점수는 음표별 부분 점수를 저장하기 전까지 null이다. errorCount는 악보 버전이 다르면 null이다. */
data class BeforeResult(val totalScore: Int? = null, val pitchScore: Int? = null, val timingScore: Int? = null, val durationScore: Int? = null,
    val errorCount: Int?, val bpm: Int, val bpmUnit: String)
data class AfterScores(val totalScore: Int, val pitchScore: Int, val timingScore: Int, val durationScore: Int, val errorCount: Int,
    val bpm: Int, val bpmUnit: String)
/** 값의 null 조건은 [BeforeResult]와 같다. */
data class ResultDelta(val totalScore: Int? = null, val pitchScore: Int? = null, val timingScore: Int? = null, val durationScore: Int? = null,
    val errorCount: Int?)
data class RemainingError(val noteIdx: Int, val verdict: String)

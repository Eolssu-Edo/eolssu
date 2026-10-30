package com.ssafy.eolssu.backend.feature.evaluation.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.evaluation.application.EvaluationLookup
import com.ssafy.eolssu.backend.feature.evaluation.application.GetPracticeEvaluation
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluation
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import org.springframework.http.HttpStatus
import java.time.Instant

@RestController
@Profile("db | ops")
class PracticeEvaluationController(private val getEvaluation: GetPracticeEvaluation) {
    @GetMapping("/api/practice-sessions/{sessionId}/evaluation")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable sessionId: Long): ResponseEntity<*> =
        when (val outcome = getEvaluation.execute(requireNotNull(jwt.subject).toLong(), sessionId)) {
            EvaluationLookup.NotFound -> ResponseEntity.status(404).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
            EvaluationLookup.NotReady -> ResponseEntity.status(409).body(ErrorResponse.of(ErrorCode.STATE_CONFLICT))
            is EvaluationLookup.Found -> ResponseEntity.ok(
                SuccessResponse.of(HttpStatus.OK, "연주 평가 결과 조회 성공", outcome.evaluation.toResponse()),
            )
        }

    private fun PracticeEvaluation.toResponse() = PracticeEvaluationResult(
        sessionId.toString(), evaluationId.toString(), songId.toString(), scoreVersion, bpm, bpmUnit,
        startedAt, endedAt, evaluatedAt, totalScore, pitchScore, timingScore, durationScore,
        notes.map { EvaluationNote(it.noteIdx, it.verdict, it.pitchErrorCent, it.onsetErrorMs, it.durationErrorMs) },
        recordingDurationMs,
        tuningOffsetCent, tuningOutOfRange,
    )
}

data class PracticeEvaluationResult(val sessionId: String,val evaluationId: String,val songId: String,val scoreVersion: Int,val bpm: Int,val bpmUnit: String,
    val startedAt: Instant,val endedAt: Instant?,val evaluatedAt: Instant,val totalScore: Int,val pitchScore: Int,val timingScore: Int,val durationScore: Int,val notes: List<EvaluationNote>, val recordingDurationMs: Int? = null,
    val tuningOffsetCent: Double? = null, val tuningOutOfRange: Boolean? = null)
data class EvaluationNote(val noteIdx: Int,val verdict: String,val pitchErrorCent: Int?,val onsetErrorMs: Int?,val durationErrorMs: Int?)

package com.ssafy.eolssu.backend.feature.evaluation.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence.EvaluationReadMapper
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.transaction.annotation.Transactional
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.ZoneOffset

@RestController
@Profile("db | ops")
class PracticeEvaluationController(private val evaluations: EvaluationReadMapper) {
    @Transactional(readOnly = true)
    @GetMapping("/api/practice-sessions/{sessionId}/evaluation")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable sessionId: Long): ResponseEntity<*> {
        val row = evaluations.findOwned(sessionId, requireNotNull(jwt.subject).toLong())
            ?: return ResponseEntity.status(404).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
        if (row.status != "DONE") return ResponseEntity.status(409).body(ErrorResponse.of(ErrorCode.STATE_CONFLICT))
        val id = requireNotNull(row.evaluationId)
        val notes = evaluations.notes(id).map { EvaluationNote(it.noteIdx,it.verdict,it.pitchErrorCent?.toInt(),it.onsetErrorMs,it.durationErrorMs) }
        val result = PracticeEvaluationResult(row.sessionId.toString(),id.toString(),row.songId.toString(),row.scoreVersion,row.bpm,row.bpmUnit,
            row.startedAt.toInstant(ZoneOffset.UTC),row.endedAt?.toInstant(ZoneOffset.UTC),requireNotNull(row.evaluatedAt).toInstant(ZoneOffset.UTC),
            requireNotNull(row.totalScore),requireNotNull(row.pitchScore),requireNotNull(row.timingScore),requireNotNull(row.durationScore),notes,row.recordingDurationMs)
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK,"연주 평가 결과 조회 성공",result))
    }
}

data class PracticeEvaluationResult(val sessionId: String,val evaluationId: String,val songId: String,val scoreVersion: Int,val bpm: Int,val bpmUnit: String,
    val startedAt: Instant,val endedAt: Instant?,val evaluatedAt: Instant,val totalScore: Int,val pitchScore: Int,val timingScore: Int,val durationScore: Int,val notes: List<EvaluationNote>, val recordingDurationMs: Int? = null)
data class EvaluationNote(val noteIdx: Int,val verdict: String,val pitchErrorCent: Int?,val onsetErrorMs: Int?,val durationErrorMs: Int?)

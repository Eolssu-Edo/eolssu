package com.ssafy.eolssu.backend.feature.evaluation.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.evaluation.application.EvaluationNotesLookup
import com.ssafy.eolssu.backend.feature.evaluation.application.EvaluationNotesQuery
import com.ssafy.eolssu.backend.feature.evaluation.application.GetEvaluationNotes
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluationNote
import com.ssafy.eolssu.backend.feature.evaluation.domain.Verdict
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

/** EVA-02. 저장 판정만 보여 주며 오차로 판정을 다시 만들지 않는다. */
@RestController
@Profile("db | ops")
class EvaluationNotesController(private val getNotes: GetEvaluationNotes) {
    @GetMapping("/api/practice-sessions/{sessionId}/evaluation/notes")
    fun get(
        @AuthenticationPrincipal jwt: Jwt, @PathVariable sessionId: Long,
        @RequestParam(required = false) startNoteIdx: Int?, @RequestParam(required = false) endNoteIdx: Int?,
        @RequestParam(defaultValue = "false") errorsOnly: Boolean,
    ): ResponseEntity<*> {
        val query = EvaluationNotesQuery(startNoteIdx, endNoteIdx, errorsOnly)
        return when (val outcome = getNotes.execute(requireNotNull(jwt.subject).toLong(), sessionId, query)) {
            EvaluationNotesLookup.NotFound -> ResponseEntity.status(404).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
            EvaluationNotesLookup.NotReady -> ResponseEntity.status(409).body(ErrorResponse.of(ErrorCode.STATE_CONFLICT))
            EvaluationNotesLookup.OutOfRange -> ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))
            is EvaluationNotesLookup.Found -> with(outcome.notes) {
                ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "음표별 오류 위치·판정 조회 성공", EvaluationNotesResult(
                    evaluationId.toString(), notes.map { it.toItem() }, errorCount,
                    ErrorCounts(
                        errorCountsByVerdict[Verdict.PITCH] ?: 0, errorCountsByVerdict[Verdict.TIMING] ?: 0,
                        errorCountsByVerdict[Verdict.LENGTH] ?: 0, errorCountsByVerdict[Verdict.MISSED] ?: 0,
                    ),
                )))
            }
        }
    }

    // 저장 판정은 음표당 하나다. 길이 오류는 LENGTH로 저장한다(S15P31E102-912). 인식 음·녹음 위치는 저장값이 없으면 null이다.
    private fun PracticeEvaluationNote.toItem() = EvaluationNoteItem(
        noteIdx, expectedPitchId.toString(), detectedPitchId?.toString(), pitchErrorCent, onsetErrorMs, durationErrorMs,
        verdict, if (isError) listOf(verdict) else emptyList(), recordingOffsetMs,
    )
}

data class EvaluationNotesResult(val evaluationId: String, val items: List<EvaluationNoteItem>, val errorCount: Int, val errorCounts: ErrorCounts)
data class EvaluationNoteItem(val noteIdx: Int, val expectedPitchId: String, val detectedPitchId: String?, val pitchErrorCent: Int?,
    val onsetErrorMs: Int?, val durationErrorMs: Int?, val verdict: String, val errorTypes: List<String>, val recordingOffsetMs: Int?)
data class ErrorCounts(val pitch: Int, val timing: Int, val duration: Int, val missed: Int)

package com.ssafy.eolssu.backend.feature.record.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.record.application.SavePracticeRecord
import com.ssafy.eolssu.backend.feature.record.application.SaveRecordOutcome
import com.ssafy.eolssu.backend.feature.record.domain.ColumnRange
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/** 연습 기록 API(REC-01~04). 계약은 `docs/specs/practice-records.md`를 따른다. */
@RestController
@RequestMapping("/api/practice-sessions/{sessionId}")
@Profile("db | ops")
class PracticeRecordController(private val saveRecord: SavePracticeRecord) {
    /** REC-01 `PUT /practice-sessions/{sessionId}/record`. 본문이 없으면 저장만 하고, `memo`가 있으면 메모도 바꾼다. */
    @PutMapping("/record")
    fun save(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable @Positive sessionId: Long,
        @RequestBody(required = false) body: JsonNode?,
    ): ResponseEntity<*> {
        val memo = memoChange(body) ?: return error(ErrorCode.INVALID_REQUEST)
        return when (val outcome = saveRecord.execute(requireNotNull(jwt.subject).toLong(), sessionId, memo)) {
            SaveRecordOutcome.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            SaveRecordOutcome.NotDone -> error(ErrorCode.STATE_CONFLICT)
            is SaveRecordOutcome.Saved -> ResponseEntity.ok(
                SuccessResponse.of(HttpStatus.OK, "연습 기록 저장 성공", SavedPracticeRecord.of(outcome.record, outcome.columns)),
            )
        }
    }

    // 생략과 null을 구분해야 하므로 DTO 대신 JSON 노드를 읽는다. JSON 객체가 아니거나 memo가 문자열·null이 아니면 null(400).
    private fun memoChange(body: JsonNode?): MemoChange? {
        if (body == null || body.isMissingNode) return MemoChange.Keep
        if (!body.isObject) return null
        val memo = body.path("memo")
        return when {
            memo.isMissingNode -> MemoChange.Keep
            memo.isNull -> MemoChange.Clear
            memo.isString -> MemoChange.of(memo.stringValue())
            else -> null
        }
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

data class SavedPracticeRecord(
    val sessionId: String,
    val savedAt: Instant,
    val memo: String?,
    val songId: String,
    val songTitle: String,
    val startColumnIdx: Int?,
    val endColumnIdx: Int?,
    @get:JsonProperty("isFullSong") val isFullSong: Boolean,
    val startedAt: Instant,
    val recordingDurationMs: Int?,
    val bpm: Int,
    val bpmUnit: String,
    val totalScore: Int,
) {
    companion object {
        fun of(record: RecordSession, columns: ColumnRange?) = with(record) {
            SavedPracticeRecord(
                sessionId.toString(), requireNotNull(savedAt).utc(), memo, songId.toString(), songTitle, columns?.start, columns?.end,
                isFullSong, startedAt.utc(), recordingDurationMs, bpm, bpmUnit, requireNotNull(totalScore),
            )
        }

        private fun LocalDateTime.utc() = toInstant(ZoneOffset.UTC)
    }
}

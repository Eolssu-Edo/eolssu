package com.ssafy.eolssu.backend.feature.record.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.record.application.DeletePracticeRecord
import com.ssafy.eolssu.backend.feature.record.application.DeleteRecordOutcome
import com.ssafy.eolssu.backend.feature.record.application.GetPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.RecordGroupOrder
import com.ssafy.eolssu.backend.feature.record.application.RecordList
import com.ssafy.eolssu.backend.feature.record.application.RecordListQuery
import com.ssafy.eolssu.backend.feature.record.application.SavePracticeRecord
import com.ssafy.eolssu.backend.feature.record.application.SaveRecordOutcome
import com.ssafy.eolssu.backend.feature.record.domain.ColumnRange
import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.SavedRecordWeek
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/** 연습 기록 API(REC-01·02·04·05). 녹음 다시 듣기(REC-03)는 [RecordingPlaybackController]. 계약은 `docs/specs/practice-records.md`를 따른다. */
@RestController
@Profile("db | ops")
class PracticeRecordController(
    private val saveRecord: SavePracticeRecord,
    private val getRecords: GetPracticeRecords,
    private val deleteRecord: DeletePracticeRecord,
) {
    /** REC-01 `PUT /practice-sessions/{sessionId}/record`. 본문이 없으면 저장만 하고, `memo`가 있으면 메모도 바꾼다. */
    @PutMapping("/api/practice-sessions/{sessionId}/record")
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

    /** REC-04 `DELETE /practice-sessions/{sessionId}/record`. 녹음까지 지우고, 지운 기록에 다시 보내면 404다. */
    @DeleteMapping("/api/practice-sessions/{sessionId}/record")
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable @Positive sessionId: Long): ResponseEntity<*> =
        when (val outcome = deleteRecord.execute(requireNotNull(jwt.subject).toLong(), sessionId)) {
            DeleteRecordOutcome.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            DeleteRecordOutcome.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
            is DeleteRecordOutcome.Deleted -> ResponseEntity.ok(
                SuccessResponse.of(
                    HttpStatus.OK, "연습 기록 삭제 성공",
                    DeletedPracticeRecord(sessionId.toString(), true, outcome.recordingDeleted, outcome.deletedAt),
                ),
            )
        }

    /** REC-02 `GET /users/me/practice-records`. 곡을 고르지 않으면 가장 최근에 저장한 곡을 펼친다. */
    @GetMapping("/api/users/me/practice-records")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) songId: Long?,
        @RequestParam(defaultValue = "NEWEST") sort: RecordSort,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "LAST_SAVED") groupOrder: RecordGroupOrder,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekOf: LocalDate?,
    ): ResponseEntity<*> {
        if ((songId != null && songId <= 0) || page < 0 || size !in 1..MAX_PAGE_SIZE ||
            (weekOf != null && weekOf.year !in 1001..9998)
        ) return error(ErrorCode.INVALID_REQUEST)
        val list = getRecords.execute(requireNotNull(jwt.subject).toLong(), RecordListQuery(songId, sort, page, size, groupOrder, weekOf))
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "저장한 이전 연주 목록 조회 성공", PracticeRecordList.of(list, page, size)))
    }

    @GetMapping("/api/users/me/practice-records/recent")
    fun recent(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekOf: LocalDate,
    ): ResponseEntity<*> {
        if (weekOf.year !in 1001..9998) return error(ErrorCode.INVALID_REQUEST)
        val summary = getRecords.recent(requireNotNull(jwt.subject).toLong(), weekOf)
        val response = RecentPracticeRecordResponse(
            summary.latest?.let { PracticeRecordItem.of(it.record, it.columns) },
            summary.week,
        )
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "최근 연주와 주간 기록 조회 성공", response))
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

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

data class DeletedPracticeRecord(val sessionId: String, val deleted: Boolean, val recordingDeleted: Boolean, val deletedAt: Instant)

data class RecentPracticeRecordResponse(val latest: PracticeRecordItem?, val week: SavedRecordWeek)

data class PracticeRecordList(
    val totalRecordCount: Int,
    val songs: List<PracticeRecordSong>,
    val songId: String?,
    val items: List<PracticeRecordItem>,
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val week: SavedRecordWeek?,
) {
    companion object {
        fun of(list: RecordList, page: Int, size: Int) = PracticeRecordList(
            list.totalRecordCount,
            list.songs.map {
                PracticeRecordSong(
                    it.songId.toString(), it.title, it.recordCount, it.lastSavedAt.toInstant(ZoneOffset.UTC), it.bestScore,
                    it.lastStartedAt.toInstant(ZoneOffset.UTC), it.instrumentCode,
                )
            },
            list.songId?.toString(),
            list.items.map { PracticeRecordItem.of(it.record, it.columns) },
            page, size, list.totalElements, list.totalPages, list.week,
        )
    }
}

data class PracticeRecordSong(
    val songId: String,
    val title: String,
    val recordCount: Int,
    val lastSavedAt: Instant,
    val bestScore: Int,
    val lastStartedAt: Instant,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val instrumentCode: String?,
)

data class PracticeRecordItem(
    val sessionId: String,
    val songId: String,
    val title: String,
    @get:JsonProperty("isFullSong") val isFullSong: Boolean,
    val startColumnIdx: Int?,
    val endColumnIdx: Int?,
    val recordingDurationMs: Int?,
    val bpm: Int,
    val bpmUnit: String,
    val totalScore: Int,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val startedAt: Instant,
    val recordingAvailable: Boolean,
    val memo: String?,
    @get:JsonProperty("isNew") val isNew: Boolean,
    val scoreVersion: Int,
) {
    companion object {
        fun of(record: RecordSession, columns: ColumnRange?) = with(record) {
            PracticeRecordItem(
                sessionId.toString(), songId.toString(), songTitle, isFullSong, columns?.start, columns?.end, recordingDurationMs, bpm, bpmUnit,
                requireNotNull(totalScore), startNoteIdx, endNoteIdx, startedAt.toInstant(ZoneOffset.UTC), recordingAvailable, memo, viewedAt == null, scoreVersion,
            )
        }
    }
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
    val scoreVersion: Int,
) {
    companion object {
        fun of(record: RecordSession, columns: ColumnRange?) = with(record) {
            SavedPracticeRecord(
                sessionId.toString(), requireNotNull(savedAt).utc(), memo, songId.toString(), songTitle, columns?.start, columns?.end,
                isFullSong, startedAt.utc(), recordingDurationMs, bpm, bpmUnit, requireNotNull(totalScore), scoreVersion,
            )
        }

        private fun LocalDateTime.utc() = toInstant(ZoneOffset.UTC)
    }
}

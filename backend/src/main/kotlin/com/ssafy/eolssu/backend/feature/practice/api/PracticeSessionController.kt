package com.ssafy.eolssu.backend.feature.practice.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSession
import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSessionCommand
import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSessionResult
import com.ssafy.eolssu.backend.feature.practice.application.GetPracticeSession
import com.ssafy.eolssu.backend.feature.practice.domain.BpmUnit
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetail
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.annotation.JsonDeserialize

// DB ID는 양의 bigint 문자열(명세 공통 타입). Long 범위를 넘는 19자리는 받지 않는다.
private const val ID_PATTERN = "[1-9][0-9]{0,17}"
// BpmUnit 이름과 같게 유지한다.
private const val BPM_UNIT_PATTERN = "QUARTER|DOTTED_QUARTER|JEONGGAN|JANGDAN_BEAT"

@RestController
@RequestMapping("/api/practice-sessions")
@Profile("db | ops")
class PracticeSessionController(
    private val createPracticeSession: CreatePracticeSession,
    private val getPracticeSession: GetPracticeSession,
) {
    /** API 명세 `POST /practice-sessions`(PRA-01 연습 세션 생성). */
    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        // DB 칸이 VARCHAR(100) ascii_bin이라 출력 가능한 ASCII 1~100자만 받는다.
        @RequestHeader("Idempotency-Key") @Pattern(regexp = "[!-~]{1,100}") idempotencyKey: String,
        @Valid @RequestBody request: CreatePracticeSessionRequest,
    ): ResponseEntity<*> {
        val command = CreatePracticeSessionCommand(
            userId = requireNotNull(jwt.subject).toLong(),
            idempotencyKey = idempotencyKey,
            songId = request.songId!!.toLong(),
            bpm = request.bpm!!,
            bpmUnit = request.bpmUnit?.let(BpmUnit::valueOf),
            startNoteIdx = request.startNoteIdx,
            endNoteIdx = request.endNoteIdx,
            parentSessionId = request.parentSessionId?.toLong(),
            jangdanSoundOn = request.jangdanSoundOn,
        )
        return when (val result = createPracticeSession.execute(command)) {
            is CreatePracticeSessionResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(
                result.session.let {
                    CreatedPracticeSessionResponse(
                        result = CreatedSession(
                            it.id.toString(), it.songId.toString(), it.status, it.bpm, it.bpmUnit.name, it.startNoteIdx, it.endNoteIdx, it.startedAt,
                        ),
                    )
                },
            )
            CreatePracticeSessionResult.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            CreatePracticeSessionResult.InvalidRequest -> error(ErrorCode.INVALID_REQUEST)
            CreatePracticeSessionResult.StateConflict -> error(ErrorCode.STATE_CONFLICT)
            CreatePracticeSessionResult.IdempotencyConflict -> error(ErrorCode.IDEMPOTENCY_CONFLICT)
        }
    }

    /** API 명세 `GET /practice-sessions/{sessionId}`(PRA-03 연습 세션·분석 상태 조회). 없거나 다른 회원의 세션, 기록을 지운 세션은 404. */
    @GetMapping("/{sessionId}")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable @Positive sessionId: Long): ResponseEntity<*> {
        val detail = getPracticeSession.execute(requireNotNull(jwt.subject).toLong(), sessionId)
            ?: return error(ErrorCode.RESOURCE_NOT_FOUND)
        return ResponseEntity.ok(PracticeSessionResponse(result = PracticeSessionResult.of(detail)))
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

// 명세 타입(STRING·INT·BOOLEAN)과 다른 JSON 값은 자동 변환하지 않고 400으로 거절한다(StrictJsonDeserializers).
// Swagger Schema 탭에서 키의 뜻과 생략 시 값을 보이도록 설명을 단다. 예시 본문은 OpenApiContracts에 둔다.
data class CreatePracticeSessionRequest(
    @field:Schema(description = "연습할 곡 ID(양의 정수 JSON 문자열). 공개·채점 가능(isScorable)·미삭제 곡이 아니면 404", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val songId: String? = null,
    @field:Schema(description = "빠르기. bpmUnit 한 박을 1분에 몇 번 치는지(20~240 정수)", example = "96", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull @field:Min(20) @field:Max(240) @param:JsonDeserialize(using = StrictIntDeserializer::class) val bpm: Int? = null,
    // enum으로 받으면 숫자 0이 첫 값으로 바뀌어 문자열로 받아 이름을 확인한다.
    @field:Schema(description = "bpm의 한 박 단위. QUARTER 4분음표, DOTTED_QUARTER 점4분음표, JEONGGAN 정간 한 칸, JANGDAN_BEAT 장단 한 박. 생략하면 곡 장단(없으면 악보)의 단위", example = "QUARTER", allowableValues = ["QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT"])
    @field:Pattern(regexp = BPM_UNIT_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val bpmUnit: String? = null,
    @field:Schema(description = "구간 연습의 첫 음 번호(0부터). 생략하면 0", example = "0")
    @field:Min(0) @param:JsonDeserialize(using = StrictIntDeserializer::class) val startNoteIdx: Int? = null,
    @field:Schema(description = "구간 연습의 마지막 음 번호(포함, noteCount - 1 이하). 생략하면 곡의 마지막 음", example = "10")
    @field:Min(0) @param:JsonDeserialize(using = StrictIntDeserializer::class) val endNoteIdx: Int? = null,
    @field:Schema(description = "다시 연습할 때 기준이 되는 이전 세션 ID. 본인·같은 곡·분석이 끝난(DONE) 세션이어야 하며, 아니면 404·400·409. 생략하면 새 연습")
    @field:Pattern(regexp = ID_PATTERN) @param:JsonDeserialize(using = StrictStringDeserializer::class) val parentSessionId: String? = null,
    @field:Schema(description = "장단 소리를 켜고 연주했는지. 지금은 저장하지 않고 같은 키 재전송 비교에만 쓴다. 생략하면 false", example = "false")
    @param:JsonDeserialize(using = StrictBooleanDeserializer::class) val jangdanSoundOn: Boolean? = null,
)

data class CreatedPracticeSessionResponse(
    val httpStatus: String = "CREATED",
    @get:JsonProperty("isSuccess") val isSuccess: Boolean = true,
    val message: String = "전체·구간 연습 세션 생성 성공",
    val code: Int = 201,
    val result: CreatedSession,
)

data class CreatedSession(
    val sessionId: String,
    val songId: String,
    val status: String,
    val bpm: Int,
    val bpmUnit: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val startedAt: Instant,
)

data class PracticeSessionResponse(
    val httpStatus: String = "OK",
    @get:JsonProperty("isSuccess") val isSuccess: Boolean = true,
    val message: String = "연습 세션·분석 상태 조회 성공",
    val code: Int = 200,
    val result: PracticeSessionResult,
)

/** DB ID는 문자열, 시각은 UTC ISO8601(API 명세 공통 타입). */
data class PracticeSessionResult(
    val sessionId: String,
    val songId: String,
    val bpm: Int,
    val bpmUnit: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val status: String,
    val evaluationId: String?,
    val failReason: String?,
    val recordingAvailable: Boolean,
    val startedAt: Instant,
    val endedAt: Instant?,
    val retryable: Boolean,
    val savedAt: Instant?,
    val memo: String?,
    val scoreVersion: Int,
    val songTitle: String,
) {
    companion object {
        fun of(detail: PracticeSessionDetail) = with(detail) {
            PracticeSessionResult(
                sessionId.toString(), songId.toString(), bpm, bpmUnit, startNoteIdx, endNoteIdx, status, evaluationId?.toString(),
                failReason, recordingAvailable, startedAt.utc(), endedAt?.utc(), retryable, savedAt?.utc(), memo, scoreVersion, songTitle,
            )
        }

        private fun LocalDateTime.utc() = toInstant(ZoneOffset.UTC)
    }
}

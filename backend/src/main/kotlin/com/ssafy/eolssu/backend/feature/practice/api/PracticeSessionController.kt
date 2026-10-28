package com.ssafy.eolssu.backend.feature.practice.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSession
import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSessionCommand
import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSessionResult
import com.ssafy.eolssu.backend.feature.practice.domain.BpmUnit
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// DB ID는 양의 bigint 문자열(명세 공통 타입). Long 범위를 넘는 19자리는 받지 않는다.
private const val ID_PATTERN = "[1-9][0-9]{0,17}"

@RestController
@RequestMapping("/api/practice-sessions")
@Profile("db")
class PracticeSessionController(private val createPracticeSession: CreatePracticeSession) {
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
            bpmUnit = request.bpmUnit,
            startNoteIdx = request.startNoteIdx,
            endNoteIdx = request.endNoteIdx,
            parentSessionId = request.parentSessionId?.toLong(),
            missionId = request.missionId?.toLong(),
            jangdanSoundOn = request.jangdanSoundOn,
        )
        return when (val result = createPracticeSession.execute(command)) {
            is CreatePracticeSessionResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(
                result.session.let {
                    PracticeSessionResponse(
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

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

data class CreatePracticeSessionRequest(
    @field:NotNull @field:Pattern(regexp = ID_PATTERN) val songId: String? = null,
    @field:NotNull @field:Min(20) @field:Max(240) val bpm: Int? = null,
    val bpmUnit: BpmUnit? = null,
    @field:Min(0) val startNoteIdx: Int? = null,
    @field:Min(0) val endNoteIdx: Int? = null,
    @field:Pattern(regexp = ID_PATTERN) val parentSessionId: String? = null,
    @field:Pattern(regexp = ID_PATTERN) val missionId: String? = null,
    val jangdanSoundOn: Boolean? = null,
)

data class PracticeSessionResponse(
    val httpStatus: String = "CREATED",
    @get:JsonProperty("isSuccess") val isSuccess: Boolean = true,
    val message: String = "전체·구간·미션 연습 세션 생성 성공",
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

package com.ssafy.eolssu.backend.feature.growth.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.growth.application.RecordScoreView
import com.ssafy.eolssu.backend.feature.growth.application.RecordScoreViewResult
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewSegment
import java.time.DateTimeException
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
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize

/** 악보 화면 체류 구간 기록(GRO-04). 계약은 docs/specs/practice-time.md '체류 기록 API'. */
@RestController
@RequestMapping("/api/users/me/score-views")
@Profile("db | ops")
class ScoreViewController(private val recordScoreView: RecordScoreView) {
    @PostMapping
    fun record(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: RecordScoreViewRequest,
    ): ResponseEntity<*> {
        // 액세스 토큰 검증기가 subject를 양의 정수로 확인한다(AccessTokenConfig).
        val segment = ScoreViewSegment(
            requireNotNull(jwt.subject).toLong(), request.songId, idempotencyKey,
            request.startedAt, request.endedAt, request.visibleSeconds, request.countedSeconds,
        )
        return when (val result = recordScoreView.record(segment)) {
            is RecordScoreViewResult.Created -> success(HttpStatus.CREATED, "체류 기록 저장 성공", result.segment)
            is RecordScoreViewResult.AlreadyRecorded -> success(HttpStatus.OK, "이미 저장된 체류 기록", result.segment)
            RecordScoreViewResult.Conflict -> error(ErrorCode.IDEMPOTENCY_CONFLICT)
            is RecordScoreViewResult.Invalid -> error(ErrorCode.INVALID_REQUEST, RejectionDetails(result.violation.name))
            RecordScoreViewResult.SongNotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            // 토큰은 유효하지만 회원 행이 없다. 앱은 토큰 갱신에 실패하고 로그아웃 때 남은 기록을 지운다.
            RecordScoreViewResult.MemberNotFound -> error(ErrorCode.UNAUTHENTICATED)
        }
    }

    private fun success(status: HttpStatus, message: String, segment: ScoreViewSegment) = ResponseEntity.status(status).body(
        ScoreViewResponse(
            status.name, true, message, status.value(),
            RecordedScoreView(segment.songId.toString(), segment.startedAt, segment.endedAt, segment.visibleSeconds, segment.countedSeconds),
        ),
    )

    private fun error(code: ErrorCode, details: Any? = null) = ResponseEntity.status(code.status).body(ErrorResponse.of(code, details))
}

// 기본 변환은 epoch 숫자 시각·소수·문자열 숫자도 받아 값을 바꿔 저장하므로, 칸마다 계약의 JSON 형식만 받는다.
data class RecordScoreViewRequest(
    @param:JsonDeserialize(using = SongIdDeserializer::class) val songId: Long,
    @param:JsonDeserialize(using = IsoInstantDeserializer::class) val startedAt: Instant,
    @param:JsonDeserialize(using = IsoInstantDeserializer::class) val endedAt: Instant,
    @param:JsonDeserialize(using = JsonIntDeserializer::class) val visibleSeconds: Int,
    @param:JsonDeserialize(using = JsonIntDeserializer::class) val countedSeconds: Int,
)

data class ScoreViewResponse(
    val httpStatus: String,
    @get:JsonProperty("isSuccess") val isSuccess: Boolean,
    val message: String,
    val code: Int,
    val result: RecordedScoreView,
)

data class RecordedScoreView(
    val songId: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val visibleSeconds: Int,
    val countedSeconds: Int,
)

/** 400 응답의 거절 이유. 입력값은 싣지 않는다. */
data class RejectionDetails(val reason: String)

/** ID는 응답과 같이 JSON 문자열로만 받는다(곡 목록이 주는 값을 그대로 보낸다). 양의 정수가 아니면 400이다. */
class SongIdDeserializer : ValueDeserializer<Long>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Long =
        parser.takeIf { it.currentToken() == JsonToken.VALUE_STRING }?.string
            ?.takeIf { text -> text.all { it in '0'..'9' } }?.toLongOrNull()?.takeIf { it > 0 }
            ?: context.reportInputMismatch(Long::class.java, "songId must be a positive integer JSON string")
}

/** ISO-8601 문자열만 받는다. 오프셋이 붙은 시각(`+09:00`)은 UTC로 바꾼다. */
class IsoInstantDeserializer : ValueDeserializer<Instant>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Instant =
        parser.takeIf { it.currentToken() == JsonToken.VALUE_STRING }?.string
            ?.let { text -> try { Instant.parse(text) } catch (_: DateTimeException) { null } }
            ?: context.reportInputMismatch(Instant::class.java, "time must be an ISO-8601 JSON string")
}

/** 소수·문자열·범위 밖 정수를 잘라 저장하지 않도록 Int 범위의 JSON 정수만 받는다. */
class JsonIntDeserializer : ValueDeserializer<Int>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Int =
        parser.takeIf { it.currentToken() == JsonToken.VALUE_NUMBER_INT && it.numberType == JsonParser.NumberType.INT }?.intValue
            ?: context.reportInputMismatch(Int::class.java, "seconds must be a JSON integer")
}

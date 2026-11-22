package com.ssafy.eolssu.backend.feature.inquiry.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.inquiry.application.InquiryResult
import com.ssafy.eolssu.backend.feature.inquiry.application.InquiryService
import com.ssafy.eolssu.backend.feature.inquiry.domain.InquiryPolicy
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 마이페이지 문의하기. 로그인이 필요한 경로라 access_token이 없거나 만료되면 보안 필터가 401로 막는다. */
@RestController
@RequestMapping("/api/users/me/inquiries")
@Profile("db | ops")
class InquiryController(private val service: InquiryService) {
    @PostMapping
    fun send(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: InquiryRequest): ResponseEntity<*> =
        when (val result = service.send(requireNotNull(jwt.subject).toLong(), checkNotNull(request.title), checkNotNull(request.content))) {
            is InquiryResult.Sent -> ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "문의 보내기 성공", InquirySent(result.sentAt)))
            // 토큰은 유효하지만 회원 행이 없거나 ACTIVE가 아니면 내 정보(USR-01)처럼 다시 로그인하게 한다.
            InquiryResult.MemberNotFound -> error(ErrorCode.UNAUTHENTICATED)
            InquiryResult.RateLimited -> error(ErrorCode.TOO_MANY_REQUESTS)
            InquiryResult.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
        }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

/**
 * 제목은 공백이 아닌 한 줄 [InquiryPolicy.TITLE_MAX_LENGTH]자, 내용은 공백이 아닌 [InquiryPolicy.CONTENT_MAX_LENGTH]자까지다(UTF-16 문자 수).
 * 제목은 메일 제목에 들어가므로 줄바꿈을 받지 않는다.
 */
class InquiryRequest(
    @field:NotBlank @field:Size(max = InquiryPolicy.TITLE_MAX_LENGTH) @field:Pattern(regexp = "[^\\r\\n]*") val title: String? = null,
    @field:NotBlank @field:Size(max = InquiryPolicy.CONTENT_MAX_LENGTH) val content: String? = null,
)

data class InquirySent(val sentAt: Instant)

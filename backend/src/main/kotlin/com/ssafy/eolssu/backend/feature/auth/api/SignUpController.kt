package com.ssafy.eolssu.backend.feature.auth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.auth.application.SignUpResult
import com.ssafy.eolssu.backend.feature.auth.application.SignUpService
import com.ssafy.eolssu.backend.feature.auth.domain.SignUpCommand
import com.ssafy.eolssu.backend.feature.auth.domain.TermAgreement
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("db | ops")
class SignUpController(private val service: SignUpService, private val cookies: AuthCookies) {
    @PostMapping("/api/auth/sign-up")
    fun signUp(@Valid @RequestBody request: SignUpRequest): ResponseEntity<*> = when (val result = service.signUp(request.toCommand())) {
        is SignUpResult.Created -> ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, *cookies.issue(result.tokens).toTypedArray())
            .body(SuccessResponse.of(HttpStatus.CREATED, "회원가입 성공", SignedUp(result)))
        SignUpResult.InvalidRequest -> error(ErrorCode.INVALID_REQUEST)
        SignUpResult.EmailNotVerified -> error(ErrorCode.EMAIL_NOT_VERIFIED)
        SignUpResult.EmailAlreadyExists -> error(ErrorCode.EMAIL_ALREADY_EXISTS)
        SignUpResult.TermsAgreementRequired -> error(ErrorCode.TERMS_AGREEMENT_REQUIRED)
        SignUpResult.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

/** 비밀번호 규칙은 서비스의 PasswordPolicy가 판정한다. ID는 명세대로 양의 정수 문자열이다. */
class SignUpRequest(
    @field:NotBlank @field:Email @field:Size(max = 320) val email: String,
    val password: String,
    @field:NotBlank @field:Size(max = 60) val nickname: String,
    @field:NotBlank val verificationToken: String,
    @field:Valid val agreements: List<AgreementRequest>,
    @field:Pattern(regexp = "[1-9][0-9]{0,17}") val instrumentId: String? = null,
    @field:Pattern(regexp = "HANJA|HANGUL|SOLFEGE") val notationMode: String? = null,
) {
    fun toCommand() = SignUpCommand(
        email, password, nickname, verificationToken, agreements.map { TermAgreement(it.termId.toLong(), it.version, it.agreed) },
        instrumentId?.toLong(), notationMode,
    )
}

data class AgreementRequest(
    @field:Pattern(regexp = "[1-9][0-9]{0,17}") val termId: String,
    @field:NotBlank @field:Size(max = 20) val version: String,
    val agreed: Boolean,
)

data class SignedUp(val userId: String, val nickname: String, val instrumentId: String, val notationMode: String, val createdAt: Instant) {
    constructor(created: SignUpResult.Created) : this(
        created.userId.toString(), created.nickname, created.instrumentId.toString(), created.notationMode, created.createdAt,
    )
}

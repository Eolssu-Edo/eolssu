package com.ssafy.eolssu.backend.feature.user.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.user.application.MyInfoService
import com.ssafy.eolssu.backend.feature.user.application.MyInfoWithProviders
import com.ssafy.eolssu.backend.feature.user.application.PreferencesResult
import com.ssafy.eolssu.backend.feature.user.domain.MyInfo
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 내 정보(USR-01~03). 로그인이 필요한 경로라 access_token이 없거나 만료되면 보안 필터가 401로 막는다. */
@RestController
@RequestMapping("/api/users/me")
@Profile("db | ops")
class MyInfoController(private val service: MyInfoService) {
    @GetMapping
    fun find(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<*> {
        val found = service.find(jwt.userId()) ?: return memberUnavailable()
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "내 정보·학습 설정 조회 성공", MyInfoResult(found)))
    }

    @PatchMapping
    fun changeNickname(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: NicknameRequest): ResponseEntity<*> {
        val changed = service.changeNickname(jwt.userId(), request.nickname) ?: return memberUnavailable()
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "회원정보 수정 성공", MyProfileResult(changed)))
    }

    @PatchMapping("/preferences")
    fun changePreferences(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: PreferencesRequest): ResponseEntity<*> {
        if (request.instrumentId == null && request.notationMode == null) return error(ErrorCode.INVALID_REQUEST)
        return when (val result = service.changePreferences(jwt.userId(), request.instrumentId?.toLong(), request.notationMode)) {
            is PreferencesResult.Changed -> ResponseEntity.ok(
                SuccessResponse.of(HttpStatus.OK, "선택 악기·기본 악보 표기 저장 성공", PreferencesSaved(result.info)),
            )
            PreferencesResult.InactiveInstrument -> error(ErrorCode.UNPROCESSABLE_INPUT)
            PreferencesResult.NotFound -> memberUnavailable()
        }
    }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))

    // 토큰은 유효하지만 회원 행이 없거나 ACTIVE가 아니면 다시 로그인하게 401이다(회원 행이 없을 때 401은 GRO-04와 같음).
    private fun memberUnavailable() = error(ErrorCode.UNAUTHENTICATED)

    private fun Jwt.userId() = requireNotNull(subject).toLong()
}

data class MyInfoResult(
    val userId: String,
    val email: String?,
    val hasPassword: Boolean,
    val nickname: String,
    val instrumentId: String,
    val notationMode: String,
    val linkedProviders: List<LinkedProviderResult>,
) {
    constructor(found: MyInfoWithProviders) : this(
        found.info.userId.toString(), found.info.email, found.info.hasPassword, found.info.nickname, found.info.instrumentId.toString(), found.info.notationMode,
        found.linkedProviders.map { LinkedProviderResult(it.provider, it.linkedAt) },
    )
}

/** 닉네임은 가입(AUTH-02)과 같은 규칙이다. */
class NicknameRequest(@field:NotBlank @field:Size(max = 60) val nickname: String)

data class MyProfileResult(val userId: String, val nickname: String, val instrumentId: String, val notationMode: String) {
    constructor(info: MyInfo) : this(info.userId.toString(), info.nickname, info.instrumentId.toString(), info.notationMode)
}

/** 한 필드 이상. 악기는 활성 악기여야 한다. */
class PreferencesRequest(
    @field:Pattern(regexp = "[1-9][0-9]{0,17}") val instrumentId: String? = null,
    @field:Pattern(regexp = "HANJA|HANGUL|SOLFEGE") val notationMode: String? = null,
)

data class PreferencesSaved(val userId: String, val instrumentId: String, val notationMode: String) {
    constructor(info: MyInfo) : this(info.userId.toString(), info.instrumentId.toString(), info.notationMode)
}

data class LinkedProviderResult(val provider: String, val linkedAt: Instant)

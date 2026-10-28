package com.ssafy.eolssu.backend.feature.user.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.user.application.MyInfoService
import com.ssafy.eolssu.backend.feature.user.application.MyInfoWithProviders
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 내 정보(USR-01~03). 로그인이 필요한 경로라 access_token이 없거나 만료되면 보안 필터가 401로 막는다. */
@RestController
@RequestMapping("/api/users/me")
@Profile("db")
class MyInfoController(private val service: MyInfoService) {
    @GetMapping
    fun find(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<*> {
        val found = service.find(jwt.userId()) ?: return notFound()
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "내 정보·학습 설정 조회 성공", MyInfoResult(found)))
    }

    private fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))

    private fun Jwt.userId() = requireNotNull(subject).toLong()
}

data class MyInfoResult(
    val userId: String,
    val nickname: String,
    val instrumentId: String,
    val notationMode: String,
    val linkedProviders: List<LinkedProviderResult>,
) {
    constructor(found: MyInfoWithProviders) : this(
        found.info.userId.toString(), found.info.nickname, found.info.instrumentId.toString(), found.info.notationMode,
        found.linkedProviders.map { LinkedProviderResult(it.provider, it.linkedAt) },
    )
}

data class LinkedProviderResult(val provider: String, val linkedAt: Instant)

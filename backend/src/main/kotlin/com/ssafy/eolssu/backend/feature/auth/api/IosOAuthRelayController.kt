package com.ssafy.eolssu.backend.feature.auth.api

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * iOS 간편 로그인 복귀 중계(`docs/specs/social-auth.md` iOS 인증 복귀). 무료 Apple 계정은 HTTPS 복귀를 앱으로 받을 수 없어,
 * 제공자가 돌려보낸 이 주소에서 앱 전용 주소로 다시 보낸다. 인가 코드는 교환·저장·기록하지 않고 허용한 매개변수만 원래 인코딩 그대로 옮긴다.
 */
@RestController
class IosOAuthRelayController {
    @GetMapping(PATH)
    fun relay(@PathVariable provider: String, request: HttpServletRequest): ResponseEntity<Void> {
        if (provider !in PROVIDERS) return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).build()
        val query = request.queryString.orEmpty().split('&')
            .filter { it.substringBefore('=') in PARAMETERS }
            .joinToString("&")
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "$APP_SCHEME://oauth/$provider/callback" + if (query.isEmpty()) "" else "?$query")
            .header("Referrer-Policy", "no-referrer")
            .cacheControl(CacheControl.noStore())
            .build()
    }

    companion object {
        const val PATH = "/api/auth/ios/oauth/{provider}/callback"
        const val APP_SCHEME = "com.ssafy.eolssu"
        private val PROVIDERS = setOf("kakao", "google", "naver")
        private val PARAMETERS = setOf("code", "state", "error", "error_code", "error_description")
    }
}

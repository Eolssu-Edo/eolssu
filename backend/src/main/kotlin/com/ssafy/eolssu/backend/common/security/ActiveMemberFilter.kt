package com.ssafy.eolssu.backend.common.security

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

/** 토큰의 회원(JWT sub = users.id)이 지금 ACTIVE인지. DB가 있는 프로필에서만 Bean으로 있다. */
fun interface ActiveMemberCheck {
    fun isActive(userId: Long): Boolean
}

/**
 * USR-05 규칙 2: access_token은 만료(최대 30분)까지 유효하므로, 탈퇴 대기·삭제된 회원의 남은 토큰을 요청마다 막는다.
 * 토큰 검증 뒤에 회원 상태를 조회해 ACTIVE가 아니면 401과 함께 두 쿠키를 지워 앱이 로그인 화면으로 가게 한다.
 */
class ActiveMemberFilter(
    private val members: ActiveMemberCheck,
    private val clearCookies: () -> List<String>,
    private val errorWriter: ErrorResponseWriter,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
        val userId = authentication?.token?.subject?.toLongOrNull()
        if (userId != null && !members.isActive(userId)) {
            SecurityContextHolder.clearContext()
            clearCookies().forEach { response.addHeader(HttpHeaders.SET_COOKIE, it) }
            errorWriter.write(response, ErrorCode.UNAUTHENTICATED)
            return
        }
        chain.doFilter(request, response)
    }
}

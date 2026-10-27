package com.ssafy.eolssu.backend.common.security

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * CSRF 방어(S15P31E102-143 확정안). 로그인 쿠키는 SameSite=Lax이고, 상태를 바꾸는 요청은 여기서 한 번 더 막는다.
 * 1. Origin 헤더가 있으면 같은 호스트이거나 설정한 개발 주소여야 한다(403). 앱은 Origin을 보내지 않으므로 없으면 통과한다.
 * 2. 본문은 JSON만 받는다(415). 다른 사이트의 HTML 폼은 JSON을 보낼 수 없다. 녹음 업로드만 multipart를 받는다.
 * 경로와 관계없이 모든 상태 변경 요청에 적용한다. 인코딩된 경로(/%61pi/...)로 건너뛰지 못하게 하려는 것이다.
 *
 * @param recordingUpload 녹음 업로드 요청 판정. 로그인 규칙과 같은 경로 기준(서블릿 경로 포함)이 되도록 SecurityConfig가 만든다.
 */
class StateChangingRequestFilter(
    private val allowedOrigins: Set<String>,
    private val recordingUpload: RequestMatcher,
    private val errorWriter: ErrorResponseWriter,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = request.method !in STATE_CHANGING_METHODS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val origin = request.getHeader("Origin")
        if (origin != null && !isAllowedOrigin(origin, request)) {
            return errorWriter.write(response, ErrorCode.FORBIDDEN)
        }
        if (!hasAcceptedBody(request)) {
            // CORS 필터보다 먼저 응답하므로, 설정한 개발 주소라면 FE가 오류를 읽을 수 있게 CORS 헤더를 직접 붙인다.
            if (origin != null && origin.trimEnd('/').lowercase() in allowedOrigins) {
                response.setHeader("Access-Control-Allow-Origin", origin)
                response.setHeader("Access-Control-Allow-Credentials", "true")
                response.addHeader("Vary", "Origin")
            }
            return errorWriter.write(response, ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        }
        chain.doFilter(request, response)
    }

    private fun isAllowedOrigin(origin: String, request: HttpServletRequest): Boolean {
        if (origin.trimEnd('/').lowercase() in allowedOrigins) return true
        val uri = runCatching { URI(origin) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val port = if (uri.port >= 0) uri.port else defaultPort(uri.scheme) ?: return false
        // Nginx는 HTTPS를 끝내고 Host를 포트 없이 넘긴다(proxy_set_header Host $host). 이때는 호스트 이름만 비교한다.
        // 같은 호스트의 다른 포트는 SameSite 기준으로도 같은 사이트다. scheme은 비교하지 않는다(운영 Nginx가 HTTP를 HTTPS로 넘긴다).
        val requestHost = request.getHeader("Host")?.lowercase() ?: return false
        val requestPort = requestHost.substringAfterLast(':', "").toIntOrNull()
        val requestName = if (requestPort != null) requestHost.substringBeforeLast(':') else requestHost
        return host == requestName && (requestPort == null || port == requestPort)
    }

    private fun hasAcceptedBody(request: HttpServletRequest): Boolean {
        val contentType = request.contentType
            ?: return request.contentLengthLong <= 0 && request.getHeader("Transfer-Encoding") == null
        val mediaType = try {
            MediaType.parseMediaType(contentType)
        } catch (_: InvalidMediaTypeException) {
            return false
        }
        if (mediaType.equalsTypeAndSubtype(MediaType.APPLICATION_JSON)) return true
        return mediaType.equalsTypeAndSubtype(MediaType.MULTIPART_FORM_DATA) && recordingUpload.matches(request)
    }

    private fun defaultPort(scheme: String?) = when (scheme?.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> null
    }

    private companion object {
        val STATE_CHANGING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}

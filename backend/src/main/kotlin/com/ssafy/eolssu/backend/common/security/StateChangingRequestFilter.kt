package com.ssafy.eolssu.backend.common.security

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * CSRF 방어(S15P31E102-143 확정안). 로그인 쿠키는 SameSite=Lax이고, 상태를 바꾸는 API 요청은 여기서 한 번 더 막는다.
 * 1. Origin 헤더가 있으면 같은 사이트이거나 설정한 개발 주소여야 한다(403). 앱은 Origin을 보내지 않으므로 없으면 통과한다.
 * 2. 본문은 JSON만 받는다(415). 다른 사이트의 HTML 폼은 JSON을 보낼 수 없다. 녹음 업로드만 multipart를 받는다.
 */
class StateChangingRequestFilter(
    private val allowedOrigins: Set<String>,
    private val errorWriter: ErrorResponseWriter,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) =
        request.method !in STATE_CHANGING_METHODS || !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val origin = request.getHeader("Origin")
        if (origin != null && !isAllowedOrigin(origin, request)) {
            return errorWriter.write(response, ErrorCode.FORBIDDEN)
        }
        if (!hasAcceptedBody(request)) {
            return errorWriter.write(response, ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        }
        chain.doFilter(request, response)
    }

    private fun isAllowedOrigin(origin: String, request: HttpServletRequest): Boolean {
        if (origin.trimEnd('/').lowercase() in allowedOrigins) return true
        val uri = runCatching { URI(origin) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val port = if (uri.port >= 0) uri.port else defaultPort(uri.scheme) ?: return false
        // Nginx는 Host를 포트 없이 넘긴다. 이때는 기본 포트로 들어온 같은 호스트만 같은 사이트로 본다.
        val requestHost = request.getHeader("Host")?.lowercase() ?: return false
        val requestPort = requestHost.substringAfterLast(':', "").toIntOrNull()
        val requestName = if (requestPort != null) requestHost.substringBeforeLast(':') else requestHost
        return host == requestName && (if (requestPort != null) port == requestPort else port == 80 || port == 443)
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
        return mediaType.equalsTypeAndSubtype(MediaType.MULTIPART_FORM_DATA) &&
            request.method == "POST" && RECORDING_UPLOAD.matches(request.requestURI)
    }

    private fun defaultPort(scheme: String?) = when (scheme?.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> null
    }

    private companion object {
        val STATE_CHANGING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        val RECORDING_UPLOAD = Regex("^/api/practice-sessions/[^/]+/recording$")
    }
}

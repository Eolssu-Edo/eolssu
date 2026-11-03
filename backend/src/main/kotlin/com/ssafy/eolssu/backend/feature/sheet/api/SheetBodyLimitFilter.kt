package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import org.springframework.http.HttpMethod
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * SHT-04·11 본문을 JSON으로 읽기 전에 [maxBytes]로 자른다(413). 컨트롤러가 본문 전체를 트리로 만든 뒤 재면
 * 큰 본문이 메모리를 먼저 쓰기 때문이다. Content-Length가 없는(chunked) 본문은 상한+1바이트까지만 읽어 판단한다.
 * 경로는 Spring MVC와 같은 PathPattern(디코딩한 경로)으로 맞춘다. 원문 URI로 비교하면 `/%73core` 같은 인코딩 경로가 빠져나간다.
 */
class SheetBodyLimitFilter(private val maxBytes: Int, private val errorWriter: ErrorResponseWriter) : OncePerRequestFilter() {
    private val scoreRoutes = PathPatternRequestMatcher.withDefaults().let { paths ->
        listOf(
            paths.matcher(HttpMethod.PUT, "/api/users/me/sheets/{sheetId}/score"),
            paths.matcher(HttpMethod.PUT, "/api/users/me/sheets/{sheetId}/score/draft"),
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest) = scoreRoutes.none { it.matches(request) }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.contentLengthLong > maxBytes) return errorWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE)
        val body = request.inputStream.readNBytes(maxBytes + 1)
        if (body.size > maxBytes) return errorWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE)
        chain.doFilter(BufferedBody(request, body), response)
    }

    private class BufferedBody(request: HttpServletRequest, private val body: ByteArray) : HttpServletRequestWrapper(request) {
        override fun getContentLength() = body.size
        override fun getContentLengthLong() = body.size.toLong()
        override fun getReader() = BufferedReader(InputStreamReader(ByteArrayInputStream(body), characterEncoding?.let(Charset::forName) ?: Charsets.UTF_8))
        override fun getInputStream(): ServletInputStream {
            val bytes = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read() = bytes.read()
                override fun read(b: ByteArray, off: Int, len: Int) = bytes.read(b, off, len)
                override fun isFinished() = bytes.available() == 0
                override fun isReady() = true
                override fun setReadListener(listener: ReadListener) = throw UnsupportedOperationException()
            }
        }
    }
}

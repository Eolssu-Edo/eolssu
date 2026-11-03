package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayInputStream
import org.springframework.web.filter.OncePerRequestFilter

/**
 * SHT-04·11 본문을 JSON으로 읽기 전에 [maxBytes]로 자른다(413). 컨트롤러가 본문 전체를 트리로 만든 뒤 재면
 * 큰 본문이 메모리를 먼저 쓰기 때문이다. Content-Length가 없는(chunked) 본문은 상한+1바이트까지만 읽어 판단한다.
 */
class SheetBodyLimitFilter(private val maxBytes: Int, private val errorWriter: ErrorResponseWriter) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = request.method != "PUT" || !SCORE_PATH.matches(request.requestURI)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.contentLengthLong > maxBytes) return errorWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE)
        val body = request.inputStream.readNBytes(maxBytes + 1)
        if (body.size > maxBytes) return errorWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE)
        chain.doFilter(BufferedBody(request, body), response)
    }

    private class BufferedBody(request: HttpServletRequest, private val body: ByteArray) : HttpServletRequestWrapper(request) {
        override fun getContentLength() = body.size
        override fun getContentLengthLong() = body.size.toLong()
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

    companion object {
        private val SCORE_PATH = Regex("^/api/users/me/sheets/[^/]+/score(/draft)?$")
    }
}

package com.ssafy.eolssu.backend.common.error

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** 컨트롤러 밖(보안 필터)에서 공통 오류 응답을 쓴다. 컨트롤러 안은 GlobalExceptionHandler가 맡는다. */
@Component
class ErrorResponseWriter(private val jsonMapper: JsonMapper) {
    fun write(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.outputStream, ErrorResponse.of(errorCode))
    }
}

package com.ssafy.eolssu.backend.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorCode = when (statusCode.value()) {
            400 -> ErrorCode.INVALID_REQUEST
            404 -> ErrorCode.RESOURCE_NOT_FOUND
            405 -> ErrorCode.METHOD_NOT_ALLOWED
            415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
            500 -> ErrorCode.INTERNAL_SERVER_ERROR
            else -> return super.handleExceptionInternal(ex, body, headers, statusCode, request)
        }
        // Allow 등 프레임워크가 제공한 응답 헤더를 보존한다. 입력·예외 메시지는 본문에 싣지 않는다.
        return super.handleExceptionInternal(ex, ErrorResponse.of(errorCode), headers, statusCode, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        LoggerFactory.getLogger(javaClass).error("Unhandled request exception", ex)
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR))
    }
}

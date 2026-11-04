package com.ssafy.eolssu.backend.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
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
            401 -> ErrorCode.UNAUTHENTICATED
            403 -> ErrorCode.FORBIDDEN
            404 -> ErrorCode.RESOURCE_NOT_FOUND
            405 -> ErrorCode.METHOD_NOT_ALLOWED
            406 -> ErrorCode.NOT_ACCEPTABLE
            // multipart 한도(25MB)를 넘은 요청. 업로드 경로마다 자기 오류 코드로 답한다.
            413 -> if (request.path().startsWith(SHEET_IMPORT_PATH)) ErrorCode.FILE_TOO_LARGE else ErrorCode.RECORDING_TOO_LARGE
            415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
            429 -> ErrorCode.TOO_MANY_REQUESTS
            500 -> ErrorCode.INTERNAL_SERVER_ERROR
            503 -> ErrorCode.SERVICE_UNAVAILABLE
            else -> null
        }
        // Allow 등 프레임워크가 제공한 응답 헤더를 보존한다. 입력·예외 메시지는 본문에 싣지 않는다.
        // 지원하지 않는 Accept에도 오류 계약의 JSON을 쓴다. 재협상하면 본문이 사라질 수 있다.
        val responseHeaders = if (statusCode.value() == 406) HttpHeaders().apply {
            putAll(headers)
            contentType = MediaType.APPLICATION_JSON
        } else headers
        // 표에 없는 상태도 ProblemDetail·이유 문구 대신 공통 형식으로 응답한다.
        val errorBody = errorCode?.let { ErrorResponse.of(it) } ?: ErrorResponse.ofStatus(statusCode)
        return super.handleExceptionInternal(ex, errorBody, responseHeaders, statusCode, request)
    }

    private fun WebRequest.path() = (this as? ServletWebRequest)?.request?.let { it.requestURI.removePrefix(it.contextPath) }.orEmpty()

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        LoggerFactory.getLogger(javaClass).error("Unhandled request exception", ex)
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR))
    }
}

/** 내 악보 불러오기(SHT-07). 413은 FILE_TOO_LARGE로 답한다. */
private const val SHEET_IMPORT_PATH = "/api/users/me/sheets/imports"

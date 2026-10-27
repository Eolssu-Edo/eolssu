package com.ssafy.eolssu.backend.common.error

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(): ResponseEntity<ErrorResponse> = respond(ErrorCode.RESOURCE_NOT_FOUND)

    private fun respond(errorCode: ErrorCode) = ResponseEntity.status(errorCode.status).body(ErrorResponse.of(errorCode))
}

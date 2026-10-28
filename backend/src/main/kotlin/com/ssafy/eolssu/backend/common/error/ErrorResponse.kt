package com.ssafy.eolssu.backend.common.error

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode

/** API 명세 공통 응답의 오류 형태: {httpStatus, isSuccess=false, message, code, result{errorCode, details}}. */
data class ErrorResponse(
    val httpStatus: String,
    @get:JsonProperty("isSuccess") val isSuccess: Boolean,
    val message: String,
    val code: Int,
    val result: ErrorResult,
) {
    companion object {
        fun of(errorCode: ErrorCode, details: Any? = null) = ErrorResponse(
            httpStatus = errorCode.status.name,
            isSuccess = false,
            message = errorCode.message,
            code = errorCode.status.value(),
            result = ErrorResult(errorCode.name, details),
        )

        /** 업무 오류 코드가 없는 프레임워크 상태. errorCode는 HTTP 상태 이름이다. */
        fun ofStatus(status: HttpStatusCode): ErrorResponse {
            val name = HttpStatus.resolve(status.value())?.name ?: status.value().toString()
            val message = if (status.is5xxServerError) "서버 오류가 발생했어요" else "요청을 처리할 수 없어요"
            return ErrorResponse(name, false, message, status.value(), ErrorResult(name, null))
        }
    }
}

data class ErrorResult(
    val errorCode: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val details: Any?,
)

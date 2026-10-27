package com.ssafy.eolssu.backend.common.error

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

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
    }
}

data class ErrorResult(
    val errorCode: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val details: Any?,
)

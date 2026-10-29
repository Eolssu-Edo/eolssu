package com.ssafy.eolssu.backend.common.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus

/** API 명세 공통 응답의 성공 형태: {httpStatus, isSuccess=true, message, code, result}. */
data class SuccessResponse<T>(
    val httpStatus: String,
    @get:JsonProperty("isSuccess") val isSuccess: Boolean,
    val message: String,
    val code: Int,
    val result: T,
) {
    companion object {
        fun <T> of(status: HttpStatus, message: String, result: T) = SuccessResponse(status.name, true, message, status.value(), result)
    }
}

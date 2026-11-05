package com.ssafy.eolssu.backend.feature.sheet.application

/** SHT-14: TIMEOUT은 한 번, 서버 장애는 합계 세 번, 인식 엔진·입력 오류는 재시도하지 않는다. */
object ConversionRetryPolicy {
    fun allowed(status: String, cause: String?, attempt: Int, timeoutCount: Int): Boolean =
        status == "FAILED" && attempt in 1..3 && when (cause) {
            "TIMEOUT" -> timeoutCount == 1
            "SERVER_ERROR" -> true
            else -> false
        }
}

package com.ssafy.eolssu.backend.feature.sheet.application

/** 원본은 처리 종료 후 폐기한다. 실패한 변환은 파일을 새로 업로드해야 한다(S15P31E102-534). */
object ConversionRetryPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun allowed(status: String, cause: String?, attempt: Int, timeoutCount: Int): Boolean = false
}

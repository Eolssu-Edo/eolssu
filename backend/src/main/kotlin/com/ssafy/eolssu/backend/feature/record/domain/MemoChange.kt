package com.ssafy.eolssu.backend.feature.record.domain

/** REC-01 요청의 메모 변경. `memo`를 생략하면 [Keep], `null`이면 [Clear]. */
sealed interface MemoChange {
    data object Keep : MemoChange

    data object Clear : MemoChange

    data class Replace(val memo: String) : MemoChange

    companion object {
        /** DB 칸(`memo` VARCHAR(500))과 같은 문자 수 기준이다. 이모지도 1자로 센다. */
        const val MAX_LENGTH = 500

        /** 문자열 메모를 정리한다. 앞뒤 공백을 자르고 비면 지운다. 자른 뒤 [MAX_LENGTH]자를 넘으면 null(요청 오류). */
        fun of(text: String): MemoChange? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Clear
            return if (trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH) null else Replace(trimmed)
        }
    }
}

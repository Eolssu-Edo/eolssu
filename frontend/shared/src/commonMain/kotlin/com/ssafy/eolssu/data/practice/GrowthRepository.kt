package com.ssafy.eolssu.data.practice

/**
 * 연습 기록지 통계(GRO-02 날짜별). 기준은 `docs/specs/practice-time.md`의 '성장 화면 통계'.
 * 날짜는 KST 1970-01-01부터의 날 수(`KoreanDateTime.epochDay`)이고 양 끝을 포함한다. 주·월 기간과 합계는 앱이 날짜별 값으로 계산한다.
 * 점수를 다루는 GRO-01 요약의 평균 점수·성취도와 GRO-03 점수 변화는 기록지에서 쓰지 않는다(S15P31E102-795).
 */
interface GrowthRepository {
    /** [fromDay]~[toDay]의 날짜별 값(최대 [MAX_CALENDAR_DAYS]일). 서버가 기록 없는 날도 0으로 채워 준다. 실패하면 null. */
    suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>?

    companion object {
        /** GRO-02 한 번에 받을 수 있는 최대 날 수(양 끝 포함). */
        const val MAX_CALENDAR_DAYS = 366
    }
}

/** GRO-02 하루 값. */
data class DailyPractice(val epochDay: Long, val practiceSeconds: Long, val practiceCount: Long)

package com.ssafy.eolssu.backend.feature.growth.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** KST 날짜 범위. 양 끝을 포함한다(docs/specs/practice-time.md '성장 화면 통계'). */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(start <= endInclusive) { "start must not be after endInclusive" }
    }

    val length: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1

    operator fun contains(day: LocalDate) = day in start..endInclusive

    fun days(): List<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= endInclusive }.toList()

    /** 한 달 전체(1일~말일)면 지난 달력 월, 아니면 [start] 바로 앞의 같은 일수 구간. */
    fun previous(): DateRange =
        if (start.dayOfMonth == 1 && endInclusive == start.with(TemporalAdjusters.lastDayOfMonth())) {
            start.minusMonths(1).let { DateRange(it, it.with(TemporalAdjusters.lastDayOfMonth())) }
        } else {
            DateRange(start.minusDays(length), start.minusDays(1))
        }
}

/** 하루에 시작해 분석이 끝난(`DONE`) 연주 세션. 점수가 있는 세션만 평균에 쓴다. */
data class SessionTotals(val doneCount: Long, val scoredCount: Long, val scoreSum: Long) {
    operator fun plus(other: SessionTotals) =
        SessionTotals(doneCount + other.doneCount, scoredCount + other.scoredCount, scoreSum + other.scoreSum)

    companion object {
        val EMPTY = SessionTotals(0, 0, 0)
    }
}

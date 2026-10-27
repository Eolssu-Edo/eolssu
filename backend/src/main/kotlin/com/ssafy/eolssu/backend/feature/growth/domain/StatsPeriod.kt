package com.ssafy.eolssu.backend.feature.growth.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 성장 화면 통계의 기간. 날짜는 모두 KST 기준이다(docs/specs/practice-time.md '성장 화면 통계'). */
enum class StatsPeriod {
    /** 월요일~일요일, 막대는 요일별 7개. */
    WEEK,

    /** 달력 월, 막대는 그 달 안의 월~일 주차별 4~6개. */
    MONTH,
    ;

    fun rangeOf(day: LocalDate): DateRange = when (this) {
        WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { DateRange(it, it.plusDays(6)) }
        MONTH -> DateRange(day.withDayOfMonth(1), day.with(TemporalAdjusters.lastDayOfMonth()))
    }

    fun previousOf(range: DateRange): DateRange = rangeOf(range.start.minusDays(1))

    fun barsOf(range: DateRange): List<DateRange> = when (this) {
        WEEK -> range.days().map { DateRange(it, it) }
        MONTH -> generateSequence(range.start) { it.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) }
            .takeWhile { it <= range.endInclusive }
            .map { start -> DateRange(start, minOf(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), range.endInclusive)) }
            .toList()
    }
}

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(day: LocalDate) = day in start..endInclusive

    fun days(): List<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= endInclusive }.toList()
}

/** 하루에 시작해 분석이 끝난(`DONE`) 연주 세션. 점수가 있는 세션만 평균에 쓴다. */
data class SessionTotals(val doneCount: Long, val scoredCount: Long, val scoreSum: Long) {
    operator fun plus(other: SessionTotals) =
        SessionTotals(doneCount + other.doneCount, scoredCount + other.scoredCount, scoreSum + other.scoreSum)

    companion object {
        val EMPTY = SessionTotals(0, 0, 0)
    }
}

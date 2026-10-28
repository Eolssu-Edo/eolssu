package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import java.time.LocalDate
import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 성장 화면 통계 조회. 날짜는 시작 시각(UTC 저장)에 9시간을 더한 KST 날짜다. */
@Mapper
interface PracticeStatsMapper {
    fun sumCountedSecondsByDay(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<DailySecondsRow>

    fun sumDoneSessionsByDay(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<DailySessionsRow>

    fun findPracticeDays(
        @Param("userId") userId: Long,
        @Param("to") to: LocalDateTime,
        @Param("minSeconds") minSeconds: Long,
    ): List<LocalDate>
}

data class DailySecondsRow(val day: LocalDate, val total: Long)

data class DailySessionsRow(val day: LocalDate, val doneCount: Long, val scoredCount: Long, val scoreSum: Long)

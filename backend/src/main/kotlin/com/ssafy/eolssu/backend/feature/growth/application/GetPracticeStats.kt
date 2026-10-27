package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import com.ssafy.eolssu.backend.feature.growth.domain.StatsPeriod
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 성장 화면(GROWTH-01) 주·월 통계. 기준은 docs/specs/practice-time.md '성장 화면 통계'.
 * 오늘이 든 기간을 보여 주고 지난 기간 전체와 비교한다.
 */
class GetPracticeStats(
    private val repository: PracticeStatsRepository,
    private val clock: Clock,
) {
    fun stats(userId: Long, period: StatsPeriod): PracticeStats {
        val today = LocalDate.now(clock.withZone(KST))
        val current = period.rangeOf(today)
        val previous = period.previousOf(current)
        val both = DateRange(previous.start, current.endInclusive)
        val seconds = repository.dailyPracticeSeconds(userId, both)
        val sessions = repository.dailyDoneSessions(userId, both)

        fun secondsIn(range: DateRange) = seconds.filterKeys { it in range }.values.sum()
        fun sessionsIn(range: DateRange) =
            sessions.filterKeys { it in range }.values.fold(SessionTotals.EMPTY, SessionTotals::plus)

        val currentSeconds = secondsIn(current)
        val currentSessions = sessionsIn(current)
        val currentAverage = averageScore(currentSessions)
        val previousAverage = averageScore(sessionsIn(previous))
        return PracticeStats(
            period = period,
            range = current,
            practiceSeconds = currentSeconds,
            practiceSecondsChange = currentSeconds - secondsIn(previous),
            sessionCount = currentSessions.doneCount,
            streakDays = streakDays(userId, today),
            averageScore = currentAverage,
            averageScoreChange = if (currentAverage != null && previousAverage != null) currentAverage - previousAverage else null,
            bars = period.barsOf(current).map { PracticeBar(it, secondsIn(it)) },
        )
    }

    // 오늘부터 거꾸로 이어진 연습한 날. 오늘 아직 연습하지 않았으면 어제부터 센다.
    private fun streakDays(userId: Long, today: LocalDate): Int {
        val days = repository.practiceDaysUpTo(userId, today, PRACTICE_DAY_MIN_SECONDS)
        var expected = if (days.firstOrNull() == today) today else today.minusDays(1)
        var streak = 0
        for (day in days) {
            if (day > expected) continue
            if (day < expected) break
            streak++
            expected = expected.minusDays(1)
        }
        return streak
    }

    // 표시 값끼리 비교하도록 반올림한 평균끼리 차이를 낸다.
    private fun averageScore(totals: SessionTotals): Int? =
        if (totals.scoredCount == 0L) {
            null
        } else {
            BigDecimal.valueOf(totals.scoreSum).divide(BigDecimal.valueOf(totals.scoredCount), 0, RoundingMode.HALF_UP).toInt()
        }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")

        /** 연습한 날: 그날 시작한 구간의 인정 시간 합이 1분 이상. */
        const val PRACTICE_DAY_MIN_SECONDS = 60L
    }
}

data class PracticeStats(
    val period: StatsPeriod,
    val range: DateRange,
    val practiceSeconds: Long,
    val practiceSecondsChange: Long,
    val sessionCount: Long,
    val streakDays: Int,
    val averageScore: Int?,
    val averageScoreChange: Int?,
    val bars: List<PracticeBar>,
)

data class PracticeBar(val range: DateRange, val practiceSeconds: Long)

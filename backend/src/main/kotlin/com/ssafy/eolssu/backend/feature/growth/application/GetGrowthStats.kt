package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.FullSongPlay
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import com.ssafy.eolssu.backend.feature.growth.domain.SongBest
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 연습 기록지(GROWTH-01)의 요약(GRO-01)과 날짜별 값(GRO-02). 기준은 docs/specs/practice-time.md '성장 화면 통계'.
 * 주·월 기간과 막대는 앱이 정하고, 서버는 받은 날짜 범위를 집계한다.
 */
class GetGrowthStats(
    private val repository: PracticeStatsRepository,
    private val clock: Clock,
) {
    fun summary(userId: Long, range: DateRange): GrowthSummary {
        val previous = range.previous()
        val both = DateRange(previous.start, range.endInclusive)
        val seconds = repository.dailyPracticeSeconds(userId, both)
        val sessions = repository.dailyDoneSessions(userId, both)

        fun totalsIn(period: DateRange): PeriodTotals {
            val done = sessions.filterKeys { it in period }.values.fold(SessionTotals.EMPTY, SessionTotals::plus)
            return PeriodTotals(seconds.filterKeys { it in period }.values.sum(), done.doneCount, averageScore(done))
        }

        return GrowthSummary(
            range = range,
            totals = totalsIn(range),
            practiceDays = seconds.count { (day, total) -> day in range && total >= PRACTICE_DAY_MIN_SECONDS },
            currentStreakDays = streakDays(userId, LocalDate.now(clock.withZone(KST))),
            previous = totalsIn(previous),
            bestBySong = repository.bestFullSongScores(userId),
            metricGrowth = metricGrowth(userId),
        )
    }

    fun calendar(userId: Long, range: DateRange): List<DailyPractice> {
        val seconds = repository.dailyPracticeSeconds(userId, range)
        val sessions = repository.dailyDoneSessions(userId, range)
        return range.days().map { DailyPractice(it, seconds[it] ?: 0, sessions[it]?.doneCount ?: 0) }
    }

    // 성취도는 from·to와 관계없이 회원의 전체 기록으로 낸다.
    private fun metricGrowth(userId: Long): MetricGrowth {
        val latest = repository.latestFullSongPlay(userId) ?: return MetricGrowth.EMPTY
        val first = repository.firstFullSongPlayLike(userId, latest)?.takeIf { it.sessionId != latest.sessionId }
        fun change(score: (FullSongPlay) -> Int) = if (first == null) ScoreChange.NONE else ScoreChange(score(first), score(latest))
        return MetricGrowth(
            latest.songId, latest.title, latest.bpm,
            change { it.pitchScore }, change { it.timingScore }, change { it.durationScore },
        )
    }

    // 오늘부터 거꾸로 이어진 연습한 날. 오늘 아직 연습하지 않았으면 어제부터 센다.
    private fun streakDays(userId: Long, today: LocalDate): Int {
        val days = repository.practiceDaysUpTo(userId, today, PRACTICE_DAY_MIN_SECONDS)
        var expected = if (days.firstOrNull() == today) today else today.minusDays(1)
        var streak = 0
        // days는 오늘 이하의 날을 최근 순으로 담는다.
        for (day in days) {
            if (day < expected) break
            streak++
            expected = expected.minusDays(1)
        }
        return streak
    }

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

        /** 한 번에 조회할 수 있는 최대 일수(API 명세). */
        const val MAX_RANGE_DAYS = 366L
    }
}

data class GrowthSummary(
    val range: DateRange,
    val totals: PeriodTotals,
    val practiceDays: Int,
    val currentStreakDays: Int,
    val previous: PeriodTotals,
    val bestBySong: List<SongBest>,
    val metricGrowth: MetricGrowth,
)

data class PeriodTotals(val practiceSeconds: Long, val practiceCount: Long, val averageTotalScore: Int?)

/** 기준 연주(가장 최근 전체 곡 연주)와 같은 조건 연주의 첫 번째 → 최근 항목 점수. 같은 조건이 2회 미만이면 점수는 null. */
data class MetricGrowth(
    val songId: Long?,
    val title: String?,
    val bpm: Int?,
    val pitch: ScoreChange,
    val timing: ScoreChange,
    val duration: ScoreChange,
) {
    companion object {
        val EMPTY = MetricGrowth(null, null, null, ScoreChange.NONE, ScoreChange.NONE, ScoreChange.NONE)
    }
}

data class ScoreChange(val first: Int?, val last: Int?) {
    companion object {
        val NONE = ScoreChange(null, null)
    }
}

data class DailyPractice(val date: LocalDate, val practiceSeconds: Long, val practiceCount: Long)

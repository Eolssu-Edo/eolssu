package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric

/**
 * 연습 기록지 통계(GRO-01 요약, GRO-02 날짜별). 기준은 `docs/specs/practice-time.md`의 '성장 화면 통계'.
 * 날짜는 KST 1970-01-01부터의 날 수(`KoreanDateTime.epochDay`)이고 양 끝을 포함한다. 주·월 기간은 앱이 정한다.
 */
interface GrowthRepository {
    /** [fromDay]~[toDay] 요약. 응답을 받지 못했거나 실패 응답이면 null. */
    suspend fun summary(fromDay: Long, toDay: Long): GrowthSummary?

    /** [fromDay]~[toDay]의 날짜별 값. 서버가 기록 없는 날도 0으로 채워 준다. 실패하면 null. */
    suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>?
}

/**
 * GRO-01 요약. 미션 수는 미션이 구현되기 전까지 null이다.
 * [bestBySong]·[metricGrowth]는 기간과 관계없이 회원의 전체 기록으로 낸다.
 */
data class GrowthSummary(
    val current: PeriodTotals,
    val previous: PeriodTotals,
    val practiceDays: Int,
    val currentStreakDays: Int,
    val completedMissionCount: Int?,
    val totalMissionCount: Int?,
    val bestBySong: List<SongBestScore>,
    val metricGrowth: MetricGrowth?,
)

/** 기간 합계. [averageTotalScore]는 평가가 있는 연주가 없으면 null. */
data class PeriodTotals(val practiceSeconds: Long, val practiceCount: Long, val averageTotalScore: Int?)

/** 곡별 전체 곡 연주 최고 총점. 최근에 연주한 곡 먼저. */
data class SongBestScore(val songId: String, val title: String, val bestTotalScore: Int)

/**
 * 가장 최근 전체 곡 연주([title]·[bpm])와 곡·악보 버전·채점 모델·BPM이 같은 연주 중 첫 번째 → 최근 항목 점수.
 * 같은 조건 연주가 2회 미만인 항목은 [changes]에 없다.
 */
data class MetricGrowth(val songId: String, val title: String, val bpm: Int, val changes: List<MetricChange>)

data class MetricChange(val metric: EvaluationMetric, val first: Int, val last: Int)

/** GRO-02 하루 값. */
data class DailyPractice(val epochDay: Long, val practiceSeconds: Long, val practiceCount: Long)

package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.model.EvaluationMetric

/**
 * 연습 기록지 통계(GRO-01 요약, GRO-02 날짜별, GRO-03 점수 변화). 기준은 `docs/specs/practice-time.md`의 '성장 화면 통계'.
 * 날짜는 KST 1970-01-01부터의 날 수(`KoreanDateTime.epochDay`)이고 양 끝을 포함한다. 주·월 기간은 앱이 정한다.
 */
interface GrowthRepository {
    /** [fromDay]~[toDay] 요약. 응답을 받지 못했거나 실패 응답이면 null. */
    suspend fun summary(fromDay: Long, toDay: Long): GrowthSummary?

    /** [fromDay]~[toDay]의 날짜별 값. 서버가 기록 없는 날도 0으로 채워 준다. 실패하면 null. */
    suspend fun calendar(fromDay: Long, toDay: Long): List<DailyPractice>?

    /**
     * GRO-03 곡 하나의 최근 점수 변화(`docs/specs/growth-score-trend.md`). [songId]가 null이면 서버가 가장 최근에 연주한 곡을 고른다.
     * 실패하면 null.
     */
    suspend fun scoreTrend(songId: String?, scope: TrendScope, limit: Int = DEFAULT_TREND_LIMIT): ScoreTrend?

    companion object {
        /** 기록지 그래프의 점 개수(보드 ‘최근 5회’). */
        const val DEFAULT_TREND_LIMIT = 5
    }
}

/** 점수 변화 범위: 전체 곡 연주(`FULL`) 또는 구간 연습(`SECTION`). */
enum class TrendScope { Full, Section }

/**
 * GRO-03 응답.
 *
 * @property songId 보여 준 곡. 세는 연주가 하나도 없어 고를 곡이 없으면 null.
 * @property plays 최근 연주 점수, 오래된 순.
 * @property comparable 2개 이상이고 악보 버전·채점 모델·BPM(구간이면 범위까지)이 모두 같을 때만 true.
 * @property songs 연주 기록이 있는 곡, 최근 연주 순. 요청 곡·범위와 관계없이 같다.
 */
data class ScoreTrend(
    val songId: String?,
    val scope: TrendScope,
    val plays: List<TrendPlay>,
    val comparable: Boolean,
    val songs: List<TrendSong>,
)

/**
 * 점수 변화의 연주 하나. [startedAtMillis]는 연주 시작 시각(UTC 밀리초).
 * [startColumn]·[endColumn]은 연주 범위의 각 번호(0부터)이고, 연주한 악보 판본이 지금과 다르면 null이다.
 */
data class TrendPlay(
    val sessionId: String,
    val startedAtMillis: Long,
    val bpm: Int,
    val startColumn: Int?,
    val endColumn: Int?,
    val totalScore: Int,
)

/** 점수 변화를 고를 수 있는 곡. 전체 곡 연주 수와 구간 연습 수로 빈 그래프·구간 전환을 미리 정한다. */
data class TrendSong(val songId: String, val title: String, val fullPlayCount: Int, val sectionPlayCount: Int)

/**
 * GRO-01 요약. 응답의 미션 수는 쓰지 않는다(기록지의 '완료한 단계'는 기기의 학습 완료 기록으로 센다).
 * [bestBySong]·[metricGrowth]는 기간과 관계없이 회원의 전체 기록으로 낸다.
 */
data class GrowthSummary(
    val current: PeriodTotals,
    val previous: PeriodTotals,
    val practiceDays: Int,
    val currentStreakDays: Int,
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

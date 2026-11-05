package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.DailyPractice
import com.ssafy.eolssu.data.practice.GrowthRepository
import com.ssafy.eolssu.data.practice.GrowthSummary
import com.ssafy.eolssu.data.practice.MetricGrowth
import com.ssafy.eolssu.data.practice.ScoreTrend
import com.ssafy.eolssu.data.practice.SongBestScore
import com.ssafy.eolssu.data.practice.TrendScope
import com.ssafy.eolssu.data.practice.TrendSong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 연습 기록지 기간. */
enum class GrowthRange { Week, Month }

/** 막대 하나(요일·주 단위 연습 시간, 분). */
data class GrowthBar(val label: String, val minutes: Long)

/**
 * 고른 기간(KST, 양 끝 포함). 주는 오늘이 든 월~일, 월은 오늘이 든 달력 월이다(`docs/specs/practice-time.md` '앱의 주·월 탭').
 *
 * @property month 기간 이름의 월. 주 보기는 오늘의 월이다.
 * @property weekOfMonth 주 보기에서 [month]의 몇째 주인지(1일이 든 주가 1주차). 월 보기는 0.
 * @property barDays 막대마다 더할 날. 주는 요일별 7개, 월은 그 달 날짜를 월~일로 묶은 4~6개(앞뒤 달 날짜는 뺀다).
 * @property todayBar 오늘이 든 막대.
 */
data class GrowthPeriod(
    val range: GrowthRange,
    val fromDay: Long,
    val toDay: Long,
    val month: Int,
    val weekOfMonth: Int,
    val barDays: List<LongRange>,
    val barLabels: List<String>,
    val todayBar: Int,
) {
    companion object {
        private val Weekdays = listOf("월", "화", "수", "목", "금", "토", "일")

        fun of(range: GrowthRange, today: KoreanDateTime): GrowthPeriod {
            val day = today.epochDay
            val firstOfMonth = KoreanDateTime(today.year, today.month, 1, 0, 0).epochDay
            val firstWeekMonday = firstOfMonth - dayOfWeek(firstOfMonth)
            return when (range) {
                GrowthRange.Week -> {
                    val monday = day - today.dayOfWeek
                    GrowthPeriod(
                        range, monday, monday + 6, today.month, ((monday - firstWeekMonday) / 7 + 1).toInt(),
                        barDays = (0 until 7).map { monday + it..monday + it },
                        barLabels = Weekdays,
                        todayBar = today.dayOfWeek,
                    )
                }
                GrowthRange.Month -> {
                    val next = if (today.month == 12) KoreanDateTime(today.year + 1, 1, 1, 0, 0) else KoreanDateTime(today.year, today.month + 1, 1, 0, 0)
                    val lastOfMonth = next.epochDay - 1
                    val weeks = generateSequence(firstOfMonth) { start -> (start + 7 - dayOfWeek(start)).takeIf { it <= lastOfMonth } }
                        .map { start -> start..minOf(start + 6 - dayOfWeek(start), lastOfMonth) }
                        .toList()
                    GrowthPeriod(
                        range, firstOfMonth, lastOfMonth, today.month, 0,
                        barDays = weeks,
                        barLabels = weeks.indices.map { "${it + 1}주" },
                        todayBar = weeks.indexOfFirst { day in it },
                    )
                }
            }
        }

        private fun dayOfWeek(epochDay: Long): Int = KoreanDateTime.ofEpochDay(epochDay).dayOfWeek
    }
}

/**
 * 기간 통계. 연습 시간은 초를 분으로 내림한다. 요약 칸은 합계를, 막대는 막대마다 내림하므로 둘이 1~2분 다를 수 있다.
 *
 * @property minutesChange 지난 기간(지난주·지난달)보다 늘어난 분. 두 쪽을 각각 내림한 값의 차이다.
 * @property averageChange 평균 점수 변화. 두 기간 평균이 모두 있을 때만 있다.
 * @property metricGrowth 같은 조건 연주끼리 비교한 항목 성장. 비교할 항목이 없으면 null.
 */
data class GrowthStats(
    val range: GrowthRange,
    val practiceMinutes: Long,
    val minutesChange: Long,
    val practiceCount: Long,
    val practiceDays: Int,
    val streakDays: Int,
    val averageScore: Int?,
    val averageChange: Int?,
    val bars: List<GrowthBar>,
    val highlightBar: Int,
    val bestBySong: List<SongBestScore>,
    val metricGrowth: MetricGrowth?,
) {
    /** 월 보기의 주 평균 연습 횟수(보드 ‘주 3.5회’): 그 달 연습 횟수를 막대(주) 수로 나눈다. */
    val perWeek: Double get() = if (bars.isEmpty()) 0.0 else practiceCount.toDouble() / bars.size

    companion object {
        fun of(period: GrowthPeriod, summary: GrowthSummary, days: List<DailyPractice>): GrowthStats {
            val current = summary.current
            val previous = summary.previous
            val minutes = current.practiceSeconds / 60
            val average = current.averageTotalScore
            val previousAverage = previous.averageTotalScore
            return GrowthStats(
                range = period.range,
                practiceMinutes = minutes,
                minutesChange = minutes - previous.practiceSeconds / 60,
                practiceCount = current.practiceCount,
                practiceDays = summary.practiceDays,
                streakDays = summary.currentStreakDays,
                averageScore = average,
                averageChange = if (average != null && previousAverage != null) average - previousAverage else null,
                bars = period.barDays.mapIndexed { i, range ->
                    GrowthBar(period.barLabels[i], days.filter { it.epochDay in range }.sumOf { it.practiceSeconds } / 60)
                },
                highlightBar = period.todayBar,
                bestBySong = summary.bestBySong,
                metricGrowth = summary.metricGrowth?.takeIf { it.changes.isNotEmpty() },
            )
        }
    }
}

/** 기간 하나의 불러오기 상태. */
sealed interface GrowthLoad {
    data object Loading : GrowthLoad

    data object Failed : GrowthLoad

    data class Loaded(val stats: GrowthStats) : GrowthLoad
}

/**
 * GROWTH-01 상태. 주·월 통계를 서버(GRO-01·02)에서 받아 기간마다 따로 들고 있어, 탭을 바꿔도 받은 값을 다시 받지 않는다.
 *
 * @property today 화면을 연 때의 KST 날짜. 기간은 이 날을 기준으로 정한다.
 * @property completedSteps 연습 악기로 기초부터 배우기에서 완료한 단계 수. 기간과 관계없이 기기의 학습 완료 기록으로 센다.
 * @property scoreTrend ‘점수 변화 · 최근 5회’ 카드(GRO-03). 주·월 기간과 관계없이 곡의 최근 연주로 그린다.
 */
data class GrowthUiState(
    val today: KoreanDateTime,
    val range: GrowthRange = GrowthRange.Week,
    val loads: Map<GrowthRange, GrowthLoad> = emptyMap(),
    val completedSteps: Int? = null,
    val scoreTrend: ScoreTrendState = ScoreTrendState(),
) {
    val period: GrowthPeriod get() = GrowthPeriod.of(range, today)

    val load: GrowthLoad get() = loads[range] ?: GrowthLoad.Loading
}

/** [period]의 요약과 날짜별 값을 함께 받아 통계로 만든다. 둘 중 하나라도 받지 못하면 [GrowthLoad.Failed]. */
suspend fun loadGrowth(repository: GrowthRepository, period: GrowthPeriod): GrowthLoad = coroutineScope {
    val summary = async { repository.summary(period.fromDay, period.toDay) }
    val days = async { repository.calendar(period.fromDay, period.toDay) }
    val s = summary.await()
    val d = days.await()
    if (s == null || d == null) GrowthLoad.Failed else GrowthLoad.Loaded(GrowthStats.of(period, s, d))
}

/** 점수 변화 그래프의 점 하나(오래된 순). [section]이면 날짜 뒤에 ‘구간’을 붙인다. */
data class ScoreTrendPoint(val total: Int, val playedAt: KoreanDateTime, val section: Boolean)

/**
 * ‘점수 변화’ 카드 상태. 곡·범위를 바꿔 다시 받는 동안에도 앞서 받은 [view]를 두어 곡 고르기·범위 칩이 사라지지 않는다.
 *
 * @property songId 요청할 곡. null이면 서버가 가장 최근에 연주한 곡을 고른다. 받은 뒤에는 서버가 고른 곡이다.
 * @property loading 받는 중. [view]가 없으면 카드에 진행 표시만 보인다.
 * @property failed 마지막 요청이 실패했다. 카드에 안내와 '다시 시도'가 보인다.
 */
data class ScoreTrendState(
    val songId: String? = null,
    val scope: TrendScope = TrendScope.Full,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val view: ScoreTrendView? = null,
) {
    /** 요청을 보낼 때의 상태. 앞서 받은 [view]는 그대로 둔다. */
    fun requesting(songId: String?, scope: TrendScope) = copy(songId = songId, scope = scope, loading = true, failed = false)

    /** 받은 결과. [trend]가 null이면 실패다. */
    fun received(trend: ScoreTrend?): ScoreTrendState =
        if (trend == null) copy(loading = false, failed = true)
        else ScoreTrendState(trend.songId, trend.scope, loading = false, failed = false, view = ScoreTrendView.of(trend))
}

/**
 * 받은 점수 변화를 카드에 그릴 값.
 *
 * @property song 보여 준 곡. 연주 기록이 하나도 없으면 null.
 * @property songs 고를 수 있는 곡(최근 연주 순). 비어 있으면 아직 평가한 연주가 없다.
 * @property mixedConditions 점이 2개 이상인데 속도·악보 판본·채점 모델(구간이면 범위까지)이 섞였다(`comparable=false`).
 */
data class ScoreTrendView(
    val scope: TrendScope,
    val song: TrendSong?,
    val songs: List<TrendSong>,
    val points: List<ScoreTrendPoint>,
    val mixedConditions: Boolean,
) {
    /** 이 범위에는 연주가 없지만 다른 범위(전체 곡·구간)에는 있다. 카드가 다른 범위를 고르도록 안내한다. */
    val onlyOtherScope: Boolean
        get() = points.isEmpty() && song != null && song.playCount(scope) == 0 && song.playCount(scope.other) > 0

    companion object {
        fun of(trend: ScoreTrend) = ScoreTrendView(
            scope = trend.scope,
            song = trend.songs.firstOrNull { it.songId == trend.songId },
            songs = trend.songs,
            points = trend.plays.map { ScoreTrendPoint(it.totalScore, KoreanDateTime.fromEpochMillis(it.startedAtMillis), trend.scope == TrendScope.Section) },
            mixedConditions = trend.plays.size >= 2 && !trend.comparable,
        )
    }
}

/** ‘점수 변화’ 카드의 곡 고르기·범위 칩·다시 시도. */
data class ScoreTrendActions(val onSong: (String) -> Unit, val onScope: (TrendScope) -> Unit, val onRetry: () -> Unit) {
    companion object {
        val None = ScoreTrendActions({}, {}, {})
    }
}

/** [song]으로 바꿀 때 볼 범위: 지금 범위에 연주가 없고 다른 범위에만 있으면 다른 범위로 바꾼다. */
fun trendScopeFor(song: TrendSong, current: TrendScope): TrendScope =
    if (song.playCount(current) == 0 && song.playCount(current.other) > 0) current.other else current

fun TrendSong.playCount(scope: TrendScope): Int = if (scope == TrendScope.Section) sectionPlayCount else fullPlayCount

private val TrendScope.other: TrendScope get() = if (this == TrendScope.Full) TrendScope.Section else TrendScope.Full

/** 보드 그래프 좌표계(viewBox 310×104)와 가로선(y 18 · 48 · 78). */
const val TrendWidth: Float = 310f
const val TrendHeight: Float = 104f
val TrendGridLines: List<Float> = listOf(18f, 48f, 78f)

/** 점 위치(보드 좌표). */
data class TrendDot(val x: Float, val y: Float)

/**
 * 보드 꺾은선 배치: x는 14~294를 같은 간격으로 나누고(점 하나면 가운데), y는 가장 낮은 점수 72 · 가장 높은 점수 24로 맞춘다
 * (보드 68→72 · 84→24). 점수가 모두 같으면 가운데 48에 둔다.
 */
fun scoreTrendDots(scores: List<Int>): List<TrendDot> {
    if (scores.isEmpty()) return emptyList()
    val min = scores.min()
    val max = scores.max()
    return scores.mapIndexed { i, s ->
        val x = if (scores.size == 1) TrendWidth / 2 else 14f + i * (280f / (scores.size - 1))
        val y = if (max == min) 48f else 72f - (s - min) * 48f / (max - min)
        TrendDot(x, y)
    }
}

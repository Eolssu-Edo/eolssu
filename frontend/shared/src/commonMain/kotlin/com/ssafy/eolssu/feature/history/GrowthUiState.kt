package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.DailyPractice

/** 기간 그래프 단위: 주별 · 월별. */
enum class GrowthRange {
    Week,
    Month;

    /** 그래프 막대 수. 작게 보기([dense])는 주별 8주 · 월별 12개월, 크게 보기는 주별 4주 · 월별 6개월이다. */
    fun barCount(dense: Boolean): Int = when (this) {
        Week -> if (dense) 8 else 4
        Month -> if (dense) 12 else 6
    }
}

/** 기간 그래프가 보이는 값: 연습 시간(분) · 연습 횟수. */
enum class GrowthMetric { Time, Count }

/** KST 날 수로 하는 날짜 계산. 주는 월~일이다. */
internal object GrowthDays {
    fun dayOfWeek(day: Long): Int = KoreanDateTime.ofEpochDay(day).dayOfWeek

    fun weekStart(day: Long): Long = day - dayOfWeek(day)

    fun monthStart(day: Long): Long = KoreanDateTime.ofEpochDay(day).run { KoreanDateTime(year, month, 1, 0, 0).epochDay }

    /** [monthStart] 달에서 [months]만큼 옮긴 달의 1일. */
    fun addMonths(monthStart: Long, months: Int): Long = KoreanDateTime.ofEpochDay(monthStart).run {
        val index = year * 12 + (month - 1) + months
        KoreanDateTime(index.floorDiv(12), index.mod(12) + 1, 1, 0, 0).epochDay
    }

    fun monthEnd(monthStart: Long): Long = addMonths(monthStart, 1) - 1

    /** [monthStart] 달을 덮는 주(월요일)들. 달력 줄 하나가 주 하나다(4~6줄). */
    fun monthWeeks(monthStart: Long): List<Long> {
        val end = monthEnd(monthStart)
        return generateSequence(weekStart(monthStart)) { it + 7 }.takeWhile { it <= end }.toList()
    }
}

/**
 * 받은 날짜별 연습 기록(GRO-02). 기록이 있는 날(연습 시간이나 횟수가 0보다 큰 날)만 [days]에 둔다.
 *
 * @property loadedFrom 받은 가장 이른 날. 그 전은 받지 않았거나, 더 찾아봐도 기록이 없었다.
 * @property searchingOlder [loadedFrom] 전 기록을 더 찾는 중. 찾으면 [firstDay]가 앞당겨진다.
 */
data class PracticeHistory(
    val days: Map<Long, DailyPractice>,
    val loadedFrom: Long,
    val searchingOlder: Boolean = false,
) {
    /** 첫 기록 날. 기록이 없으면 null. */
    val firstDay: Long? = days.keys.minOrNull()

    operator fun get(day: Long): DailyPractice? = days[day]

    /** [fromDay]~[toDay](양 끝 포함)의 연습 시간(초)·횟수 합. */
    fun sum(fromDay: Long, toDay: Long): Pair<Long, Long> {
        var seconds = 0L
        var count = 0L
        days.forEach { (day, value) ->
            if (day in fromDay..toDay) {
                seconds += value.practiceSeconds
                count += value.practiceCount
            }
        }
        return seconds to count
    }

    /** 새로 받은 날짜별 값을 더한다. [from]은 이번에 받은 범위의 첫날이다. */
    fun merged(received: List<DailyPractice>, from: Long): PracticeHistory = copy(
        days = days + received.filter { it.hasRecord }.associateBy { it.epochDay },
        loadedFrom = minOf(loadedFrom, from),
    )

    companion object {
        fun of(received: List<DailyPractice>, from: Long, searchingOlder: Boolean = false) =
            PracticeHistory(received.filter { it.hasRecord }.associateBy { it.epochDay }, from, searchingOlder)
    }
}

/** 연습 시간이나 횟수가 있는 날. */
internal val DailyPractice.hasRecord: Boolean get() = practiceSeconds > 0 || practiceCount > 0

/** 연습 시간(분, 초를 내림). */
internal val DailyPractice.minutes: Long get() = practiceSeconds / 60

/** 그래프 막대 하나: [fromDay]~[toDay] 기간(주 또는 달)의 합계. */
data class PeriodBar(val fromDay: Long, val toDay: Long, val seconds: Long, val count: Long) {
    val minutes: Long get() = seconds / 60

    fun value(metric: GrowthMetric): Long = if (metric == GrowthMetric.Time) minutes else count
}

/**
 * 기간 그래프. 마지막 막대가 고른 날짜가 든 주·달이다.
 * 합계 분은 초를 모두 더한 뒤 내림하고 막대는 막대마다 내림하므로, 막대 합과 1~2분 다를 수 있다(`docs/specs/practice-time.md` '분 표시').
 */
data class PeriodChart(val range: GrowthRange, val bars: List<PeriodBar>) {
    val totalSeconds: Long get() = bars.sumOf { it.seconds }
    val totalMinutes: Long get() = totalSeconds / 60
    val totalCount: Long get() = bars.sumOf { it.count }

    val fromDay: Long get() = bars.first().fromDay
    val toDay: Long get() = bars.last().toDay

    /** 위 점선(최댓값) 눈금. 모두 0이면 1이다. */
    fun max(metric: GrowthMetric): Long = maxOf(1L, bars.maxOf { it.value(metric) })

    /** 가운데 점선(절반) 눈금. 반올림한다. */
    fun half(metric: GrowthMetric): Long = (max(metric) + 1) / 2

    companion object {
        fun of(history: PracticeHistory, range: GrowthRange, dense: Boolean, anchorDay: Long): PeriodChart {
            val n = range.barCount(dense)
            val bars = (n - 1 downTo 0).map { back ->
                val (from, to) = when (range) {
                    GrowthRange.Week -> GrowthDays.weekStart(anchorDay).let { start -> (start - back * 7).let { it to it + 6 } }
                    GrowthRange.Month -> GrowthDays.addMonths(GrowthDays.monthStart(anchorDay), -back).let { it to GrowthDays.monthEnd(it) }
                }
                val (seconds, count) = history.sum(from, to)
                PeriodBar(from, to, seconds, count)
            }
            return PeriodChart(range, bars)
        }
    }
}

/**
 * 주별 막대 이름: 주의 월요일 날짜. 앞 막대와 달이 바뀌는 막대(첫 막대 포함)는 `M/d`, 나머지는 `d`(보드 '9/21 · 28 · 10/5').
 */
fun weekBarLabels(bars: List<PeriodBar>): List<String> {
    var previousMonth = -1
    return bars.map { bar ->
        val date = KoreanDateTime.ofEpochDay(bar.fromDay)
        val label = if (date.month != previousMonth) "${date.month}/${date.day}" else "${date.day}"
        previousMonth = date.month
        label
    }
}

/**
 * 연습량 달력의 진하기 단계(0 없음 · 1~4). 그날 연습 시간(분)이 [HeatThresholds]의 몇 번째 경계 이상인지다.
 * 1분이 안 되는 날은 '연습한 날'이 아니므로(`docs/specs/practice-time.md`) 0이다.
 */
fun heatLevel(minutes: Long): Int = HeatThresholds.count { minutes >= it }

/** 진하기 경계(분): 1~14분 · 15~29분 · 30~44분 · 45분 이상(2026-11-12 사용자 확정, S15P31E102-795). */
val HeatThresholds: List<Long> = listOf(1, 15, 30, 45)

/** 기간 하나의 불러오기 상태. */
sealed interface GrowthLoad {
    data object Loading : GrowthLoad

    data object Failed : GrowthLoad

    data class Loaded(val history: PracticeHistory) : GrowthLoad
}

/**
 * GROWTH-01 상태(연습 기록지 A안 2차, S15P31E102-795). 점수는 다루지 않고 연습 시간 · 연습 횟수만 보인다.
 *
 * @property today 화면을 연 때의 KST 날짜.
 * @property dense 작게 보기(막대가 많은 쪽). 처음은 주별 8주 · 월별 12개월이다.
 * @property anchorDay 기간 그래프의 기준 날짜. 이 날이 든 주·달이 마지막 막대다.
 * @property heatMonth 연습량 달력의 달(1일).
 * @property heatDay 연습량 달력에서 고른 날. 달을 넘겨도 그대로 두고 아래에 그날 기록을 보인다.
 */
data class GrowthUiState(
    val today: KoreanDateTime,
    val range: GrowthRange = GrowthRange.Week,
    val metric: GrowthMetric = GrowthMetric.Time,
    val dense: Boolean = true,
    val anchorDay: Long = today.epochDay,
    val heatMonth: Long = GrowthDays.monthStart(today.epochDay),
    val heatDay: Long = today.epochDay,
    val load: GrowthLoad = GrowthLoad.Loading,
) {
    val todayDay: Long get() = today.epochDay

    val history: PracticeHistory? get() = (load as? GrowthLoad.Loaded)?.history

    val chart: PeriodChart? get() = history?.let { PeriodChart.of(it, range, dense, anchorDay) }

    /** 고를 수 있는 가장 이른 날: 첫 기록 날. 기록이 없으면 오늘이다. */
    val earliestDay: Long get() = minOf(history?.firstDay ?: todayDay, todayDay)

    val earliestMonth: Long get() = GrowthDays.monthStart(earliestDay)

    val latestMonth: Long get() = GrowthDays.monthStart(todayDay)

    /** 기간 시트에서 고를 수 있는 날: 첫 기록 날 ~ 오늘. */
    fun canPickAnchor(day: Long): Boolean = day in earliestDay..todayDay

    /** 달을 넘길 수 있는지(첫 기록이 든 달 ~ 이번 달). */
    fun canShowMonth(monthStart: Long): Boolean = monthStart in earliestMonth..latestMonth
}

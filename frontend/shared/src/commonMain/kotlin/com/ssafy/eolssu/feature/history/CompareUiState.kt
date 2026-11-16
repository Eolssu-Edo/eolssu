package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.data.practice.PreviousRecord
import com.ssafy.eolssu.data.practice.CheckLoad
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.data.practice.RecordComparison

/** 두 연주에서 한 정간의 관계. [Fixed] 이전에만 틀림(고침), [Still] 둘 다 틀림, [New] 이번에만 틀림. */
enum class CompareMark { Fixed, Still, New }

/** 이전·이번 틀린 정간을 정간마다 나눈다. 틀린 종류는 보지 않고 위치만 비교한다. */
fun compareMarks(previous: List<ScoreError>, current: List<ScoreError>): Map<ScorePosition, CompareMark> {
    val before = previous.map { ScorePosition(it.column, it.row) }.toSet()
    val now = current.map { ScorePosition(it.column, it.row) }.toSet()
    return (before + now).associateWith { p ->
        when {
            p in before && p in now -> CompareMark.Still
            p in before -> CompareMark.Fixed
            else -> CompareMark.New
        }
    }
}

/**
 * 범례의 수. [previous]·[current]는 각 연주에서 틀린 정간 수이고 [still]이 겹친다.
 */
data class CompareCounts(val previous: Int, val current: Int, val still: Int) {
    companion object {
        fun of(marks: Map<ScorePosition, CompareMark>): CompareCounts {
            val still = marks.values.count { it == CompareMark.Still }
            return CompareCounts(
                previous = still + marks.values.count { it == CompareMark.Fixed },
                current = still + marks.values.count { it == CompareMark.New },
                still = still,
            )
        }
    }
}

/**
 * [records] 중 이 기록보다 먼저 같은 곡을 처음부터 끝까지 연주한 기록 전부(최신순). 기간 제한은 없고,
 * 이 기록 이후의 기록은 서버 비교(EVA-04)가 400으로 거절하므로 넣지 않는다.
 */
fun PracticeRecord.compareCandidates(records: List<PracticeRecord>): List<PracticeRecord> = records
    .filter { it.id != id && it.songId == songId && it.section == null && it.recordedAtMillis < recordedAtMillis }
    .sortedByDescending { it.recordedAtMillis }

/**
 * [records] 중 이 기록 바로 전에 같은 곡을 처음부터 끝까지 연주한 기록(기록 상세 ‘지난 연주와 비교’). 없으면 null.
 */
fun PracticeRecord.previousIn(records: List<PracticeRecord>): PracticeRecord? = compareCandidates(records).firstOrNull()

/** 달력의 한 달. */
data class CompareMonth(val year: Int, val month: Int) : Comparable<CompareMonth> {
    override fun compareTo(other: CompareMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })

    fun plus(months: Int): CompareMonth {
        val index = year * 12 + (month - 1) + months
        return CompareMonth(index.floorDiv(12), index.mod(12) + 1)
    }

    /** 1일의 날 수(1970-01-01부터). */
    val firstDay: Long get() = KoreanDateTime(year, month, 1, 0, 0).epochDay

    val length: Int get() = (plus(1).firstDay - firstDay).toInt()

    companion object {
        fun of(date: KoreanDateTime) = CompareMonth(date.year, date.month)
    }
}

/**
 * 기록 고르기 달력의 하루.
 *
 * @property records 그날의 고를 수 있는 이전 기록(최신순).
 * @property current 이번 기록의 날.
 * @property after 이번 기록 이후의 날(고를 수 없고 더 흐리게).
 */
data class CompareDay(val date: KoreanDateTime, val records: List<PracticeRecord>, val current: Boolean, val after: Boolean)

/**
 * 비교할 기록 고르기 시트의 바탕. 이전 연주 결과의 ‘지난 연주와 비교’와 이전 연주와 비교의 ‘비교 기록 바꾸기’가 같이 쓴다.
 *
 * @property candidates [current]보다 먼저 같은 곡을 처음부터 끝까지 연주한 기록(최신순, [compareCandidates]).
 * @property selectedId 지금 비교 중인 기록. null이면 아무것도 비교하지 않는 중이다.
 */
data class ComparePick(val current: PracticeRecord, val candidates: List<PracticeRecord>, val selectedId: String?) {
    /** 처음 보일 날: 비교 중인 기록의 날, 없으면 바로 전 기록의 날. 이전 기록이 없으면 이번 기록의 날. */
    val initialDay: Long
        get() = (candidates.firstOrNull { it.id == selectedId } ?: candidates.firstOrNull())?.recordedAt?.epochDay
            ?: current.recordedAt.epochDay

    /** 넘길 수 있는 달: 가장 이른 이전 기록이 있는 달부터 이번 기록이 있는 달까지. */
    val firstMonth: CompareMonth get() = candidates.minByOrNull { it.recordedAtMillis }?.let { CompareMonth.of(it.recordedAt) } ?: lastMonth
    val lastMonth: CompareMonth get() = CompareMonth.of(current.recordedAt)

    fun recordsOn(epochDay: Long): List<PracticeRecord> = candidates.filter { it.recordedAt.epochDay == epochDay }

    /**
     * [month]의 달력(월요일 시작, 연습 기록지 달력과 같음). 1일 앞의 빈칸은 null이다. 날마다 그날의 이전 기록을 담는다.
     */
    fun month(month: CompareMonth): List<CompareDay?> {
        val currentDay = current.recordedAt.epochDay
        val first = month.firstDay
        // KoreanDateTime.dayOfWeek는 월요일 0 … 일요일 6이라 그대로 앞 빈칸 수가 된다.
        val lead = KoreanDateTime.ofEpochDay(first).dayOfWeek
        val byDay = candidates.groupBy { it.recordedAt.epochDay }
        return List(lead) { null } + (0 until month.length).map { offset ->
            val day = first + offset
            CompareDay(KoreanDateTime.ofEpochDay(day), byDay[day].orEmpty(), current = day == currentDay, after = day > currentDay)
        }
    }
}

/** 점수 비교 카드 가운데 칩. */
sealed interface ScoreChange {
    /** 서버 비교 결과를 받는 중. 칩 없이 화살표만 보인다. */
    data object Pending : ScoreChange

    data class Up(val points: Int) : ScoreChange

    data class Down(val points: Int) : ScoreChange

    /** ‘= 같아요’. */
    data object Same : ScoreChange

    /** 두 기록의 점수가 없어 차이를 구할 수 없음(‘점수 차 없음’). */
    data object Unavailable : ScoreChange
}

/**
 * 점수 차이 칩. 서버 총점 차이(EVA-04 `totalScoreDelta`)가 있으면 그 값을, 없으면(채점 방식·판본이 달라 서버가 주지 않거나
 * 비교 결과를 받지 못하면) 두 기록의 총점 [before] → [after] 차이를 쓴다. 1점이라도 다르면 오름·내림으로 보인다.
 * 서버 비교 결과를 받기 전에는 [ScoreChange.Pending]이다.
 */
fun scoreChange(check: CheckLoad?, before: Int?, after: Int?): ScoreChange {
    if (check == null) return ScoreChange.Pending
    val delta = (check as? CheckLoad.Loaded)?.check?.totalScoreDelta
        ?: if (before != null && after != null) after - before else return ScoreChange.Unavailable
    return when {
        delta > 0 -> ScoreChange.Up(delta)
        delta < 0 -> ScoreChange.Down(-delta)
        else -> ScoreChange.Same
    }
}

/**
 * 항목별 점수 한 줄. 서버 비교 결과에는 총점 차이만 있어 항목 차이([delta] = 이번 − 이전)는 두 기록의 항목 점수로 앱에서 구한다.
 */
data class MetricChange(val metric: EvaluationMetric, val before: Int, val after: Int) {
    val delta: Int get() = after - before
}

/**
 * COMPARE-01 이전 연주와 비교 상태.
 *
 * @property previousId 비교할 이전 기록. null이면 가장 최근 이전 기록.
 * @property metricsOpen ‘항목별 점수 보기’를 펼쳤는지.
 * @property previousDetail 비교 중인 이전 기록의 틀린 정간과 서버 비교 결과(EVA-04). 고른 기록을 받는 동안·받지 못하면 null.
 */
data class CompareUiState(
    val loading: Boolean = true,
    val comparison: RecordComparison? = null,
    val previousId: String? = null,
    val sheetOpen: Boolean = false,
    val metricsOpen: Boolean = false,
    val previousDetail: PreviousRecord? = null,
) {
    val previous: PracticeRecord?
        get() = comparison?.previous?.let { list -> list.firstOrNull { it.id == previousId } ?: list.maxByOrNull { it.recordedAtMillis } }

    /** 지금 고른 이전 기록의 상세. 다른 기록을 받는 중이면 null. */
    private val loadedPrevious: PreviousRecord? get() = previousDetail?.takeIf { it.record.id == previous?.id }

    /** 이전 기록의 틀린 정간을 받기 전에는 이번 틀린 정간만 표시하지 않도록 비워 둔다. */
    val marks: Map<ScorePosition, CompareMark>
        get() {
            val comparison = comparison ?: return emptyMap()
            val loaded = loadedPrevious?.check as? CheckLoad.Loaded ?: return emptyMap()
            if (!loaded.check.comparable) return emptyMap()
            val before = loadedPrevious?.record?.errors ?: return emptyMap()
            return compareMarks(before, comparison.current.errors)
        }

    /** 서버 비교 결과(악보 판본·채점 모델·속도가 같아 견줄 수 있는지). 받기 전이면 null. */
    val check: CheckLoad? get() = loadedPrevious?.check

    val counts: CompareCounts get() = CompareCounts.of(marks)

    /** 이전 → 이번 점수 차이(서버 값). 견줄 수 없거나 받기 전이면 null. */
    val delta: Int? get() = (check as? CheckLoad.Loaded)?.check?.totalScoreDelta

    val scoreChange: ScoreChange
        get() = scoreChange(check, (loadedPrevious?.record ?: previous)?.score, comparison?.current?.score)

    /**
     * 음정·박자·음 길이마다 이전·이번 점수와 차이. 이전 점수는 받은 상세가 있으면 그 값을, 없으면 목록의 값을 쓴다.
     * 이번 기록에 없는 항목은 뺀다.
     */
    val metricChanges: List<MetricChange>
        get() {
            val current = comparison?.current ?: return emptyList()
            val before = (loadedPrevious?.record ?: previous ?: return emptyList()).metrics.associate { it.metric to it.score }
            return current.metrics.mapNotNull { now ->
                val earlier = before[now.metric] ?: return@mapNotNull null
                MetricChange(now.metric, earlier, now.score)
            }
        }

    /** 기록 고르기 시트의 바탕. */
    val pick: ComparePick? get() = comparison?.let { ComparePick(it.current, it.previous, previous?.id) }
}

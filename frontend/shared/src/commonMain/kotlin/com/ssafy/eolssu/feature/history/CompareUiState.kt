package com.ssafy.eolssu.feature.history

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

/** 기록 고르기 달력의 하루. */
data class CompareDay(val date: KoreanDateTime, val records: List<PracticeRecord>, val today: Boolean)

/** [today]까지 [days]일(기본 2주) 달력. 날마다 그날 녹음한 이전 기록을 최신순으로 담는다. */
fun compareCalendar(today: KoreanDateTime, previous: List<PracticeRecord>, days: Int = 14): List<CompareDay> {
    val last = today.epochDay
    return (last - days + 1..last).map { day ->
        CompareDay(
            date = KoreanDateTime.ofEpochDay(day),
            records = previous.filter { it.recordedAt.epochDay == day }.sortedByDescending { it.recordedAtMillis },
            today = day == last,
        )
    }
}

/**
 * [records] 중 이 기록 바로 전에 같은 곡을 처음부터 끝까지 연주한 기록(기록 상세 ‘지난 연주와 비교’). 없으면 null.
 */
fun PracticeRecord.previousIn(records: List<PracticeRecord>): PracticeRecord? = records
    .filter { it.id != id && it.songId == songId && it.section == null && it.recordedAtMillis < recordedAtMillis }
    .maxByOrNull { it.recordedAtMillis }

/**
 * COMPARE-01 이전 연주와 비교 상태.
 *
 * @property previousId 비교할 이전 기록. null이면 가장 최근 이전 기록.
 * @property selected 듣기를 시작할 정간. 정간보에 따로 표시하지 않고 아래 안내 문구에만 나온다.
 * @property pickDay 기록 고르기 시트에서 고른 날(1970-01-01부터의 날 수). null이면 비교 중인 기록의 날.
 * @property playing 고른 정간부터 이전 → 이번을 차례로 듣는 중. 지금은 소리 없이 버튼 상태만 바뀐다.
 * @property previousDetail 비교 중인 이전 기록의 틀린 정간과 서버 비교 결과(EVA-04). 고른 기록을 받는 동안·받지 못하면 null.
 */
data class CompareUiState(
    val loading: Boolean = true,
    val comparison: RecordComparison? = null,
    val previousId: String? = null,
    val selected: ScorePosition? = null,
    val playing: Boolean = false,
    val sheetOpen: Boolean = false,
    val pickDay: Long? = null,
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
            val before = loadedPrevious?.record?.errors ?: return emptyMap()
            return compareMarks(before, comparison.current.errors)
        }

    /** 서버 비교 결과(악보 판본·채점 모델·속도가 같아 견줄 수 있는지). 받기 전이면 null. */
    val check: CheckLoad? get() = loadedPrevious?.check

    val counts: CompareCounts get() = CompareCounts.of(marks)

    /** 듣기 시작 정간. 고르지 않았으면 이번에 처음 틀린 정간, 없으면 첫 정간. */
    val listenFrom: ScorePosition
        get() = selected ?: comparison?.current?.errors?.minWithOrNull(compareBy({ it.column }, { it.row }))?.let { ScorePosition(it.column, it.row) }
            ?: ScorePosition(0, 0)

    /** 이전 → 이번 점수 차이. 이전 기록이 없으면 null. */
    val delta: Int? get() = previous?.let { p -> comparison?.current?.score?.minus(p.score) }

    val calendar: List<CompareDay> get() = comparison?.let { compareCalendar(it.today, it.previous) }.orEmpty()

    /** 시트에서 보일 날. */
    val shownDay: Long? get() = pickDay ?: previous?.recordedAt?.epochDay
}

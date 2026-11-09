package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.key
import kotlin.math.abs
import kotlin.math.log2

/**
 * 평가 연주 중 악보의 음 자리에 칠하는 실시간 음높이 안내(S15P31E102-597). 다른 음을 불었는지만 알리며 점수가 아니다.
 * 음정 기준은 서버 채점(`ai/.../scoring.py`)을 따르지만, 녹음 전체 정렬(DTW)·박자 판정은 하지 않아 결과와 다를 수 있다.
 */
enum class LivePitchMark {
    /** 맞는 음. */
    Correct,

    /** 다른 음. */
    Wrong,

    /** 소리는 들었지만 측정 오류로 보이는 값뿐이라 판단을 보류했다. */
    Listening,
}

/** 서버 `Tolerance.pitch_cent`. 조율 보정 뒤 이보다 벗어나면 다른 음이다. */
internal const val LivePitchToleranceCents = 50.0

/** 서버 `Tolerance.max_tuning_cent`. 곡에 가까운 이웃 음이 있으면 [livePitchMaxTuningCents]가 더 낮춘다. */
private const val MaxTuningCents = 100.0

/** 조율 차이를 낼 때 이보다 크게 벗어난 음은 뺀다. 다른 음을 불었거나 박자가 어긋나 앞뒤 음 소리가 섞인 값으로 본다. */
internal const val LivePitchTuningOutlierCents = 150.0

/**
 * 한 번의 평가 연주 동안 음(원본 이벤트 ID)마다 들어온 확정 음높이(Hz)와, 지나가서 색을 고정한 음.
 * 같은 입력 창(입력 종료 시각)은 현재·직전으로 다시 분류되거나 만료로 다시 와도 한 번만 넣는다.
 * 지나간 음의 색은 [freezeBefore]로 고정해 뒤에서 조율 차이 추정이 바뀌어도 다시 칠하지 않는다.
 */
data class LivePitchTally(
    private val notes: Map<String, Heard> = emptyMap(),
    private val frozen: Map<String, LivePitchMark> = emptyMap(),
) {
    /** [hz]는 들어온 순서대로다. [held]는 측정 오류로 보여 음정에 넣지 않은 창 수다([comparePitch]의 보류). */
    data class Heard(val hz: List<Double> = emptyList(), val held: Int = 0, val lastEndNanos: Long = Long.MIN_VALUE)

    /** [PracticePitchSession]이 내보낸 결과를 더한다. 쉼·확정된 음높이가 없는 입력과 이미 고정한 음의 늦은 값은 넣지 않는다. */
    fun add(feedback: PracticePitchFeedback): LivePitchTally {
        var next = notes
        for (value in listOfNotNull(feedback.previous, feedback.current)) {
            val hz = value.heardHz
            if (value.rest || hz == null || value.eventId in frozen) continue
            val heard = next[value.eventId] ?: Heard()
            if (value.capturedEndNanos <= heard.lastEndNanos) continue
            next = next + (value.eventId to if (value.correct == null) {
                heard.copy(held = heard.held + 1, lastEndNanos = value.capturedEndNanos)
            } else {
                heard.copy(hz = heard.hz + hz, lastEndNanos = value.capturedEndNanos)
            })
        }
        return if (next === notes) this else copy(notes = next)
    }

    /**
     * 연주 순서가 [index]보다 앞인 음의 지금 색을 고정한다. 직전 음 결과는 다음 음으로 넘어간 뒤에도 늦게 올 수 있으므로
     * 화면은 지금 위치보다 두 음 앞까지(`position - 1`)를 넘긴다.
     */
    fun freezeBefore(song: Song, index: Int): LivePitchTally {
        val passed = song.timeline.take(index.coerceAtLeast(0)).map { it.id }.filter { it in notes && it !in frozen }
        if (passed.isEmpty()) return this
        val live = marks(song)
        return copy(frozen = frozen + passed.mapNotNull { id -> live[id]?.let { id to it } })
    }

    /**
     * 음마다 색을 정한다(꾸밈음과 목표 높이가 없는 음은 뺀다). 음정 기준은 서버 채점을 따른다.
     * 1. 음마다 들어온 값 중 가운데 60%(앞뒤 20%를 버림)의 중앙값 오차(센트)를 그 음의 값으로 쓴다.
     * 2. 음 값들 중 ±[LivePitchTuningOutlierCents]센트 안의 중앙값을 조율 차이로 보고, [livePitchMaxTuningCents] 안이면 뺀다.
     *    서버는 모든 프레임의 중앙값을 쓰지만, 실시간은 긴 음이 추정을 끌고 가거나 섞인 소리가 상한을 넘겨 보정이 꺼지지 않게 한다.
     * 3. 조율 차이를 뺀 오차가 ±[LivePitchToleranceCents]센트를 넘으면 다른 음이다. 옥타브 차이도 다른 음이다.
     * 보류 값만 들어온 음은 [LivePitchMark.Listening]이다. 고정한 음은 고정한 색 그대로다.
     */
    fun marks(song: Song): Map<String, LivePitchMark> {
        val hwangHz = song.score.timing?.hwangHz ?: return frozen
        val events = song.timeline.associateBy { it.id }
        val values = notes.mapNotNull { (id, heard) ->
            val event = events[id]?.takeIf { it.sigimsae.isEmpty() } ?: return@mapNotNull null
            val target = targetHz(event.note, hwangHz) ?: return@mapNotNull null
            val n = heard.hz.size
            val middle = heard.hz.subList((n * 0.2).toInt(), maxOf((n * 0.8).toInt(), 1).coerceAtMost(n)).map { cents(target, it) }
            median(middle)?.let { id to it }
        }.toMap()
        val applied = livePitchTuningOffset(values.values, livePitchMaxTuningCents(song.timeline, hwangHz))
        return notes.mapNotNull { (id, heard) ->
            frozen[id]?.let { return@mapNotNull id to it }
            if (events[id]?.sigimsae?.isNotEmpty() != false) return@mapNotNull null
            val error = values[id]?.minus(applied)
            when {
                error != null -> id to if (abs(error) > LivePitchToleranceCents) LivePitchMark.Wrong else LivePitchMark.Correct
                heard.held > 0 -> id to LivePitchMark.Listening
                else -> null
            }
        }.toMap()
    }
}

/**
 * 조율 보정 상한(센트). 서버와 같이 `min(100, 곡에서 가장 가까운 두 음 간격 − 50 − 10)`이다. 반음 이웃이 있으면 40센트다.
 * 상한을 넘는 조율 차이는 다른 음으로 분 것일 수 있어 보정하지 않는다.
 */
internal fun livePitchMaxTuningCents(events: List<ScoreEvent>, hwangHz: Double): Double {
    val songHz = songTargetHz(events, hwangHz)
    val gap = songHz.zipWithNext { low, high -> cents(low, high) }.filter { it > 1 }.minOrNull() ?: return MaxTuningCents
    return minOf(MaxTuningCents, gap - LivePitchToleranceCents - 10.0)
}

/**
 * 음마다의 오차(센트)에서 뺄 조율 차이. ±[LivePitchTuningOutlierCents] 안의 값만 모은 중앙값이고, [maxTuning]을 넘으면
 * 서버처럼 보정하지 않는다(0).
 */
internal fun livePitchTuningOffset(noteErrors: Collection<Double>, maxTuning: Double): Double {
    val offset = median(noteErrors.filter { abs(it) <= LivePitchTuningOutlierCents }) ?: return 0.0
    return if (abs(offset) > maxTuning) 0.0 else offset
}

private fun cents(from: Double, to: Double) = 1200 * log2(to / from)

private fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/**
 * 평가 연주에서 모은 판정을 악보 표시 자리별 안내로 바꾼다. 같은 음이 여러 정간에 걸치면 모두 칠한다.
 * 혼자 연습은 비운다. 언제 지울지는 [PracticeViewModel]이 집계를 비우는 시점(새 연주·준비·화면 이탈)을 따른다.
 */
internal fun livePitchMarks(state: PracticeUiState, tally: LivePitchTally): Map<ScoreUnitKey, LivePitchMark> {
    val song = state.song ?: return emptyMap()
    if (state.solo) return emptyMap()
    val marks = tally.marks(song)
    if (marks.isEmpty()) return emptyMap()
    return buildMap {
        for (unit in song.displayUnits) marks[unit.event.id]?.let { put(unit.key, it) }
    }
}

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
 * 한 번의 평가 연주 동안 음(원본 이벤트 ID)마다 들어온 확정 음높이(Hz)와, 측정이 끝나 판정을 확정한 음.
 * 같은 입력 창(입력 종료 시각)은 현재·직전으로 다시 분류되거나 만료로 다시 와도 한 번만 넣는다.
 * 확정한 음([confirmed])은 그때의 색을 그대로 두고 늦게 온 값을 받지 않아, 뒤에서 조율 차이 추정이 바뀌어도 다시 칠하지 않는다.
 * 화면은 확정한 음만 칠한다([shownMarks], S15P31E102-980).
 */
data class LivePitchTally(
    private val notes: Map<String, Heard> = emptyMap(),
    private val confirmed: Map<String, Confirmed> = emptyMap(),
) {
    /** [hz]는 들어온 순서대로다. [held]는 측정 오류로 보여 음정에 넣지 않은 창 수다([comparePitch]의 보류). */
    data class Heard(val hz: List<Double> = emptyList(), val held: Int = 0, val lastEndNanos: Long = Long.MIN_VALUE)

    /**
     * 판정을 확정한 음. [mark]는 확정할 때의 색이고 값이 하나도 없던 음은 null이다(그래도 늦은 값은 받지 않는다).
     * [atIndex]는 확정한 순간의 연주 순서로, 칠한 색을 한 음 뒤에 가라앉힐 때 쓴다.
     */
    data class Confirmed(val mark: LivePitchMark?, val atIndex: Int)

    /** [PracticePitchSession]이 내보낸 결과를 더한다. 쉼·확정된 음높이가 없는 입력과 이미 확정한 음의 늦은 값은 넣지 않는다. */
    fun add(feedback: PracticePitchFeedback): LivePitchTally {
        var next = notes
        for (value in listOfNotNull(feedback.previous, feedback.current)) {
            val hz = value.heardHz
            if (value.rest || hz == null || value.eventId in confirmed) continue
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
     * 지금 연주 위치([position]번 음, 연주 시각 [elapsedMs])에서 측정이 끝난 음의 판정을 확정한다.
     * 음은 끝난 뒤 [PitchFeedbackLifetimeNanos](300ms)가 지나거나 두 음 뒤로 넘어가면 확정한다. 그 뒤에는 그 음의 값이 오지 않는다.
     * - 300ms: 입력 창은 캡처 끝에서 300ms 안에 처리된 것만 받는다([PracticePitchTracker.accept]).
     * - 두 음 뒤: 결과는 현재·직전 음에만 연결한다. 빠른 곡에서 300ms보다 먼저 온다.
     * 연주 시계와 캡처 시계가 조금 어긋나 확정 뒤에 값이 오더라도 [add]가 받지 않으므로 색은 바뀌지 않는다.
     */
    fun confirm(song: Song, position: Int, elapsedMs: Double, bpm: Int): LivePitchTally {
        val ticksPerBeat = song.score.timing?.ticksPerBeat ?: return this
        if (bpm <= 0 || ticksPerBeat <= 0) return this
        val msPerTick = 60_000.0 / bpm / ticksPerBeat
        val lateMs = PitchFeedbackLifetimeNanos / 1_000_000.0
        return close(song, position) { event ->
            event.index <= position - 2 || run {
                val end = (event.startTick ?: return@run false) + (event.durationTick ?: return@run false)
                elapsedMs >= end * msPerTick + lateMs
            }
        }
    }

    /** [index]번보다 앞의 음을 모두 확정한다. 녹음을 멈추거나 마쳐 그 음들에 더 올 값이 없을 때 쓴다. */
    fun confirmBefore(song: Song, index: Int): LivePitchTally = close(song, index) { true }

    private inline fun close(song: Song, before: Int, due: (ScoreEvent) -> Boolean): LivePitchTally {
        var live: Map<String, LivePitchMark>? = null
        var next = confirmed
        for (event in song.timeline) {
            if (event.index >= before) break
            if (event.id in next || !due(event)) continue
            val marks = live ?: marks(song).also { live = it }
            next = next + (event.id to Confirmed(marks[event.id], before))
        }
        return if (next === confirmed) this else copy(confirmed = next)
    }

    /** 확정한 음의 판정. 화면에 칠하는 것은 이것뿐이다. */
    fun shownMarks(): Map<String, Confirmed> = confirmed.filterValues { it.mark != null }

    /**
     * 음마다 색을 정한다(꾸밈음과 목표 높이가 없는 음은 뺀다). 음정 기준은 서버 채점을 따른다.
     * 1. 음마다 들어온 값 중 가운데 60%(앞뒤 20%를 버림)의 중앙값 오차(센트)를 그 음의 값으로 쓴다.
     * 2. 음 값들 중 ±[LivePitchTuningOutlierCents]센트 안의 중앙값을 조율 차이로 보고, [livePitchMaxTuningCents] 안이면 뺀다.
     *    서버는 모든 프레임의 중앙값을 쓰지만, 실시간은 긴 음이 추정을 끌고 가거나 섞인 소리가 상한을 넘겨 보정이 꺼지지 않게 한다.
     * 3. 조율 차이를 뺀 오차가 ±[LivePitchToleranceCents]센트를 넘으면 다른 음이다. 옥타브 차이도 다른 음이다.
     * 보류 값만 들어온 음은 [LivePitchMark.Listening]이다. 확정한 음은 확정한 색 그대로다. 아직 확정하지 않은 음도 지금 판정을 넣는다.
     */
    fun marks(song: Song): Map<String, LivePitchMark> {
        val frozen = confirmed.mapNotNull { (id, value) -> value.mark?.let { id to it } }.toMap()
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
            if (id in confirmed) return@mapNotNull frozen[id]?.let { id to it }
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
 * 악보 표시 자리 하나에 칠할 실시간 판정. [settled]면 맞음의 진한 연두를 옅은 연두로 가라앉힌다(3-A '옅어지는 바탕', S15P31E102-980).
 */
data class LivePitchCell(val mark: LivePitchMark, val settled: Boolean = false)

/**
 * 평가 연주에서 확정한 판정을 악보 표시 자리별 안내로 바꾼다. 같은 음이 여러 정간에 걸치면(이음표) 모든 자리를 한꺼번에 칠한다.
 * 확정한 뒤 진행이 한 음 더 넘어가거나 연주를 마치면 가라앉힌다. 혼자 연습과 ‘퍼펙트 스코어’(실시간 채점)를 끈 동안은 비운다.
 * 언제 지울지는 [PracticeViewModel]이 집계를 비우는 시점(새 연주·준비·화면 이탈)을 따른다.
 */
internal fun livePitchMarks(state: PracticeUiState, tally: LivePitchTally): Map<ScoreUnitKey, LivePitchCell> {
    val song = state.song ?: return emptyMap()
    if (state.solo || !state.settings.liveMarks) return emptyMap()
    val shown = tally.shownMarks()
    if (shown.isEmpty()) return emptyMap()
    return buildMap {
        for (unit in song.displayUnits) {
            val value = shown[unit.event.id] ?: continue
            put(unit.key, LivePitchCell(value.mark ?: continue, settled = state.finished || state.position >= value.atIndex + 1))
        }
    }
}

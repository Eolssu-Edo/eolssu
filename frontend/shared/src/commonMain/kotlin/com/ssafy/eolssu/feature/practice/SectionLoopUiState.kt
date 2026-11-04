package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song

/**
 * PRACTICE-03 구간 반복 상태. 연습 중에는 점수를 매기지 않고, 고른 구간을 [bpm]으로 [repeats]번(null이면 계속) 따라간다.
 * 지금은 녹음·소리 없이 화면의 진행만 보인다.
 *
 * @property errors 지난번 틀린 정간. 구간 안만 종류별로 표시한다.
 * @property cells 구간의 정간(연주 순서).
 * @property round 지금 반복 회차(1부터).
 * @property step 지금 정간의 [cells] 안 순서.
 * @property countdown 다시 시작 전 3·2·1에서 보일 숫자. null이면 세지 않는다.
 * @property resumed 처음 시작이 아니라 멈췄다가 이어 가는 카운트다운인지(안내 문구가 다르다).
 */
data class SectionLoopUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val errors: List<ScoreError> = emptyList(),
    val section: ScoreSection? = null,
    val cells: List<ScorePosition> = emptyList(),
    val bpm: Int = 60,
    val repeats: Int? = null,
    val round: Int = 1,
    val step: Int = 0,
    val running: Boolean = false,
    val countdown: Int? = null,
    val resumed: Boolean = false,
    val finished: Boolean = false,
) {
    val current: ScorePosition? get() = cells.getOrNull(step)

    /** 정간 하나의 길이(ms). */
    val cellMillis: Double get() = song?.cellDurationMs(bpm) ?: 0.0

    /** 이번 회차에서 흐른 시간과 한 회차 길이(초). */
    val elapsedSeconds: Double get() = step * cellMillis / 1000
    val roundSeconds: Double get() = cells.size * cellMillis / 1000

    /** 회차마다의 진행 막대 비율(0~1). ‘계속’이면 이번 회차 하나만 보인다. */
    val roundProgress: List<Float>
        get() {
            val fraction = if (cells.isEmpty()) 0f else if (finished) 1f else step.toFloat() / cells.size
            val total = repeats ?: return listOf(fraction)
            return List(total) { i ->
                when {
                    i + 1 < round -> 1f
                    i + 1 == round -> fraction
                    else -> 0f
                }
            }
        }

    /** 앞뒤 각(흐리게 보일 맥락). */
    val contextColumns: Set<Int>
        get() {
            val s = section ?: return emptySet()
            val last = song?.score?.columns?.lastIndex ?: return emptySet()
            return setOfNotNull((s.start.column - 1).takeIf { it >= 0 }, (s.end.column + 1).takeIf { it <= last })
        }

    /** 화면에 그릴 각: 구간 각과 앞뒤 한 각씩. */
    val shownColumns: IntRange?
        get() {
            val s = section ?: return null
            val last = song?.score?.columns?.lastIndex ?: return null
            return (s.start.column - 1).coerceAtLeast(0)..(s.end.column + 1).coerceAtMost(last)
        }
}

/**
 * 한 정간 나아간다. 구간 끝이면 다음 회차의 처음으로 돌아가고, 정한 횟수를 마치면 멈추고 [SectionLoopUiState.finished]가 된다.
 */
fun SectionLoopUiState.advance(): SectionLoopUiState = when {
    cells.isEmpty() || finished -> this
    step + 1 < cells.size -> copy(step = step + 1)
    repeats == null || round < repeats -> copy(round = round + 1, step = 0)
    else -> copy(running = false, finished = true)
}

/** 3·2·1 카운트다운을 시작한다. 반복을 마친 뒤라면 처음 회차부터 다시 한다. */
fun SectionLoopUiState.beginCountdown(): SectionLoopUiState {
    val restart = finished
    return copy(
        running = false,
        countdown = CountdownBeats,
        resumed = !restart && (round > 1 || step > 0),
        finished = false,
        round = if (restart) 1 else round,
        step = if (restart) 0 else step,
    )
}

/** 카운트다운 한 숫자(1초). 1 다음에는 막을 걷고 반복을 이어 간다. */
fun SectionLoopUiState.countdownTick(): SectionLoopUiState {
    val count = countdown ?: return this
    return if (count > 1) copy(countdown = count - 1) else copy(countdown = null, running = true)
}

fun SectionLoopUiState.pause(): SectionLoopUiState = copy(running = false, countdown = null)

/** 다시 시작할 때 세는 박 수. */
const val CountdownBeats = 3

/** 카운트다운 숫자 하나의 길이(ms). 구간 속도와 상관없이 1초씩 센다. */
const val CountdownTickMillis = 1_000L

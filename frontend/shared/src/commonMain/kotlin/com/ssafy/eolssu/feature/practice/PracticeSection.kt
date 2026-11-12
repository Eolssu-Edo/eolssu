package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreSection

/**
 * 구간 연습(S15P31E102-794). 연습 기록의 ‘구간 연습’ → 구간 고르기에서 정한 구간으로 연습 화면을 연다. 속도는 연습 화면 설정 그대로다.
 * 타임라인(시간·막대·−5초·+5초)도 구간 안에서만 움직인다.
 * 연습 화면과 같되 모범 연주·장단으로 연습·한 음씩이 그 구간만 한 번 연습하고 구간 처음에 멈춘다. 구간은 이 화면에서 바꾸지 않는다.
 * 아래 평가 버튼은 곡 전체 평가 대신 같은 구간·속도의 구간 재평가로 간다.
 *
 * @property section 연습할 구간.
 */
data class PracticeSectionState(val section: ScoreSection)

/** 구간 연습이면 구간 안의 첫·마지막 연주 순서. 아니면 null. */
val PracticeUiState.sectionEvents: IntRange?
    get() {
        val section = sectionPractice?.section ?: return null
        val events = song?.timeline?.filter { section.contains(it.column, it.row) }.orEmpty()
        if (events.isEmpty()) return null
        return events.first().index..events.last().index
    }

/** 준비·멈춤 상태에서 구간 첫 음. 악보가 그 자리로 넘어가고 지금 자리로 칠한다. 그 밖에는 null. */
val PracticeUiState.sectionReadyStart: Int?
    get() = sectionEvents?.first?.takeIf { mode == PracticeMode.Ready || mode == PracticeMode.Model && !playing || mode == PracticeMode.Rhythm && !playing }

/** 구간 연습의 시작 자리(첫 음의 연주 순서). 구간이 없으면 곡 처음(0). */
val PracticeUiState.sectionStartIndex: Int get() = sectionEvents?.first ?: 0

/** 한 음씩이 기다릴 음들. 구간 연습이면 구간 안의 음만이다. */
fun PracticeUiState.stepTargetsInSection(): List<Int> {
    val targets = stepTargets(song?.timeline.orEmpty())
    val range = sectionEvents ?: return targets
    return targets.filter { it in range }.ifEmpty { targets }
}

/** 구간 연습으로 연다: [section]을 고정하고 시작 자리와 한 음씩의 첫 음을 구간 첫 음으로 옮긴다. */
fun PracticeUiState.openSectionPractice(section: ScoreSection): PracticeUiState {
    val opened = copy(sectionPractice = PracticeSectionState(section))
    val first = opened.sectionEvents?.first ?: return opened
    return opened.copy(
        selected = first, selectedCell = opened.cellForEvent(first), position = first,
        step = opened.step.copy(target = first, phase = StepPhase.Idle),
    )
}

/** 재생이 구간 끝을 지났는지. */
val PracticeUiState.passedSectionEnd: Boolean
    get() {
        val range = sectionEvents ?: return false
        return position > range.last
    }

/** 구간 끝에 닿았다: 구간 처음으로 돌아가 멈춘다. 다시 누르면 또 한 번 연습한다. */
fun PracticeUiState.stopAtSectionStart(): PracticeUiState {
    val first = sectionEvents?.first ?: return this
    return copy(position = first, eventElapsedMs = 0.0, pausedFraction = 0f, eventStartedAt = null, follow = true, playing = false)
}

/** 구간의 악보 tick 범위(첫 음 시작 ~ 마지막 음 끝). 구간 연습이 아니거나 tick이 없으면 null. */
internal val PracticeUiState.sectionTicks: ClosedFloatingPointRange<Double>?
    get() {
        val range = sectionEvents ?: return null
        val timeline = song?.timeline ?: return null
        val start = timeline.getOrNull(range.first)?.startTick ?: return null
        val last = timeline.getOrNull(range.last) ?: return null
        val end = (last.startTick ?: return null) + (last.durationTick ?: return null)
        return start.toDouble()..end.toDouble()
    }

/** 지금 속도의 tick 하나 길이(ms). */
private val PracticeUiState.msPerTick: Double?
    get() {
        val ticksPerBeat = song?.score?.timing?.ticksPerBeat ?: return null
        return if (bpm > 0) 60_000.0 / bpm / ticksPerBeat else null
    }

/** 플레이어에 보일 지금 시간(초). 구간 연습이면 구간 처음부터 잰다. */
val PracticeUiState.playerElapsedSeconds: Double
    get() {
        val ticks = sectionTicks ?: return elapsedMs / 1000
        val ms = msPerTick ?: return 0.0
        val tick = (currentTick ?: ticks.start).coerceIn(ticks.start, ticks.endInclusive)
        return (tick - ticks.start) * ms / 1000
    }

/** 플레이어에 보일 전체 시간(초). 구간 연습이면 구간 길이다. */
val PracticeUiState.playerTotalSeconds: Double
    get() {
        val ticks = sectionTicks ?: return totalSeconds.toDouble()
        val ms = msPerTick ?: return 0.0
        return (ticks.endInclusive - ticks.start) * ms / 1000
    }

/** 플레이어 막대 비율(0~1). 구간 연습이면 구간 안의 비율이다. */
val PracticeUiState.playerFraction: Float
    get() {
        val ticks = sectionTicks ?: return progressPercent / 100f
        val length = ticks.endInclusive - ticks.start
        if (length <= 0) return 0f
        val tick = (currentTick ?: ticks.start).coerceIn(ticks.start, ticks.endInclusive)
        return ((tick - ticks.start) / length).toFloat()
    }

/** 구간 연습 막대의 [fraction]이 가리키는 악보 tick. 구간 끝은 넘지 않는다. */
internal fun PracticeUiState.sectionTickAt(fraction: Float): Double? {
    val ticks = sectionTicks ?: return null
    return ticks.start + (ticks.endInclusive - ticks.start) * fraction.coerceIn(0f, 1f)
}

/** 지금 자리에서 [deltaMs]만큼 옮긴 악보 tick을 구간 안으로 맞춘다. */
internal fun PracticeUiState.sectionTickBy(deltaMs: Long): Double? {
    val ticks = sectionTicks ?: return null
    val ms = msPerTick ?: return null
    return ((currentTick ?: ticks.start) + deltaMs / ms).coerceIn(ticks.start, ticks.endInclusive)
}

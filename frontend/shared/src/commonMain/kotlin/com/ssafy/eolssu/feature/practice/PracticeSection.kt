package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.data.practice.WeakSectionSource

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
    get() = sectionEvents?.first?.takeIf {
        // 구간 재평가의 평가 시작 3·2·1에도 구간 처음을 보인 채로 둔다(연주가 그 자리에서 시작한다).
        mode == PracticeMode.Ready || mode == PracticeMode.Model && !playing || mode == PracticeMode.Rhythm && !playing ||
            mode == PracticeMode.Countdown && !resume
    }

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

/** 재생이 구간 끝을 지났는지. 구간 시작 직전의 native seek 보고는 종료가 아니다. */
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

/**
 * 구간 재평가(S15P31E102-824): 연주 평가([PracticeUiState.direct])가 구간([PracticeUiState.sectionPractice])만 녹음한다.
 * 화면·진행·확인 창은 연주 평가와 같고, 구간을 악보에 표시하는 것과 녹음이 구간 첫 음에서 시작해 마지막 음에서 끝나는 것만 다르다.
 */
val PracticeUiState.sectionEvaluation: Boolean get() = direct && sectionPractice != null

/** 평가 연주가 시작하는 음의 연주 순서. 구간 재평가면 구간 첫 음, 아니면 곡 처음(0). */
val PracticeUiState.recordingStartIndex: Int get() = if (sectionEvaluation) sectionStartIndex else 0

/** 평가 연주가 끝나는 음의 연주 순서. 구간 재평가면 구간 마지막 음, 아니면 곡 마지막 음. */
val PracticeUiState.recordingEndIndex: Int get() = sectionEvents?.last?.takeIf { sectionEvaluation } ?: (eventCount - 1)

/**
 * 곡 처음부터 [index]번째 음이 시작할 때까지의 연주 시간(ms). 곡 끝을 넘으면 곡 길이다.
 * 평가 연주의 시계([PracticeUiState.elapsedMs])는 구간 재평가에서도 이 곡 처음 기준 시간으로 가서, 장단 소리와 실시간 음높이 안내가 같은 기준을 쓴다.
 */
internal fun PracticeUiState.eventStartMs(index: Int): Double {
    val song = song ?: return 0.0
    if (bpm <= 0) return 0.0
    val event = song.timeline.getOrNull(index) ?: return song.durationMs(bpm)
    val ticksPerBeat = song.score.timing?.ticksPerBeat
    val tick = event.startTick
    return if (ticksPerBeat != null && tick != null) tick * 60_000.0 / bpm / ticksPerBeat else event.unitStart * song.cellDurationMs(bpm)
}

/** 평가 연주가 시작하는 시간(ms, 곡 처음 기준). 구간 재평가면 구간 첫 음이 시작하는 시간이다. */
internal val PracticeUiState.recordingStartMs: Double get() = if (sectionEvaluation) eventStartMs(recordingStartIndex) else 0.0

/** 연주 중 하단 막대의 경과 시간(초). 구간 재평가는 구간 처음부터 잰다. */
val PracticeUiState.recordingElapsedSeconds: Double
    get() = if (sectionEvaluation) (elapsedMs - recordingStartMs).coerceAtLeast(0.0) / 1000 else elapsedMs / 1000

/** 연주 중 하단 막대의 전체 시간(초). 구간 재평가는 구간 길이다. */
val PracticeUiState.recordingTotalSeconds: Double
    get() = if (sectionEvaluation) (eventStartMs(recordingEndIndex + 1) - recordingStartMs).coerceAtLeast(0.0) / 1000 else totalSeconds.toDouble()

/** 연주 중 하단 진행 막대의 비율(0~1). 구간 재평가는 구간 안에서 지난 몫이다. */
val PracticeUiState.recordingProgress: Float
    get() {
        if (!sectionEvaluation) return progressPercent / 100f
        if (finished) return 1f
        val total = recordingTotalSeconds
        return if (total <= 0.0) 0f else (recordingElapsedSeconds / total).toFloat().coerceIn(0f, 1f)
    }

/**
 * 구간 재평가의 바탕(S15P31E102-824). 바탕 세션 [sourceId]가 연주한 악보를 불러오고, 새로 녹음하기 전에 그 악보가 지금도 공개 판본인지 확인한다.
 */
class SectionEvaluationSource(val sourceId: String, private val repository: WeakSectionRepository) {
    private var loaded: WeakSectionSource? = null

    /** 바탕 세션의 곡. 없거나 받지 못하면 null. */
    suspend fun song(): Song? = repository.source(sourceId)?.also { loaded = it }?.song

    /** 새 구간 녹음 전의 판본 확인. true면 그대로 녹음하고, false면 악보가 바뀌었고, null이면 확인하지 못했다. */
    suspend fun isCurrent(): Boolean? = loaded?.let { repository.isCurrent(it) }
}

/**
 * [song]에서 [section] 안의 율명 음 번호 범위(양 끝 포함, 구간 재평가 세션 PRA-01의 `startNoteIdx`·`endNoteIdx`).
 * 음 번호는 서버와 같이 악보의 율명 음만 연주 순서로 센다. 구간에 율명 음이 없으면 null.
 */
fun noteRange(song: Song, section: ScoreSection): IntRange? {
    val notes = song.timeline.filter { it.noteIndex != null && section.contains(it.column, it.row) }.mapNotNull { it.noteIndex }
    return if (notes.isEmpty()) null else notes.min()..notes.max()
}

package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection

/**
 * 연습 화면의 ‘구간 연습’(S15P31E102-776). 켜면 악보에서 시작·끝 정간을 눌러 구간을 고르고, 모범 연주·장단 연습·한 음씩이
 * 그 구간만 [repeat] 횟수만큼 되풀이한다. 연주 평가는 언제나 곡 전체다.
 *
 * @property section 고른 구간. 시작 정간만 누른 동안은 그 한 정간이다.
 * @property picking 시작 정간을 누른 뒤 끝 정간을 기다리는 중.
 * @property picked 사용자가 시작·끝을 눌러 직접 마무리했는지. 추천 구간 그대로면 false다.
 * @property recommended 연주 결과·연습 기록에서 들어올 때 받은 추천 구간(EVA-03). ‘취약 구간으로’가 이 구간으로 되돌린다.
 * @property round 지금 반복 회차(1부터).
 */
data class PracticeSectionState(
    val section: ScoreSection? = null,
    val picking: Boolean = false,
    val picked: Boolean = false,
    val recommended: ScoreSection? = null,
    val repeat: SectionRepeat = SectionRepeat.Five,
    val round: Int = 1,
) {
    /** 안내 문구 단계. 추천 구간이 잡혀 있어도 직접 고르기 전에는 시작 정간부터 안내한다. */
    val step: SectionSelectStep
        get() = when {
            picking -> SectionSelectStep.End
            picked && section != null -> SectionSelectStep.Ready
            else -> SectionSelectStep.Start
        }

    /** 구간이 정해져 재생·한 음씩을 구간 안으로 묶는지. 끝 정간을 기다리는 동안은 묶지 않는다. */
    val ready: Boolean get() = section != null && !picking
}

/** 구간 연습이 켜져 있고 구간이 정해졌으면 그 구간 안의 첫·마지막 연주 순서. 아니면 null. */
val PracticeUiState.sectionEvents: IntRange?
    get() {
        val state = sectionPractice?.takeIf { it.ready } ?: return null
        val section = state.section ?: return null
        val events = song?.timeline?.filter { section.contains(it.column, it.row) }.orEmpty()
        if (events.isEmpty()) return null
        return events.first().index..events.last().index
    }

/** 준비 상태에서 구간이 정해졌으면 구간 첫 음. 악보가 그 자리로 넘어가고 지금 자리로 칠한다. 그 밖에는 null. */
val PracticeUiState.sectionReadyStart: Int?
    get() = sectionEvents?.first?.takeIf { mode == PracticeMode.Ready || mode == PracticeMode.Model && !playing || mode == PracticeMode.Rhythm && !playing }

/** 구간 연습의 시작 자리(첫 음의 연주 순서). 구간이 없으면 곡 처음(0). */
val PracticeUiState.sectionStartIndex: Int get() = sectionEvents?.first ?: 0

/** 한 음씩이 기다릴 음들. 구간 연습 중이면 구간 안의 음만이다. */
fun PracticeUiState.stepTargetsInSection(): List<Int> {
    val targets = stepTargets(song?.timeline.orEmpty())
    val range = sectionEvents ?: return targets
    return targets.filter { it in range }.ifEmpty { targets }
}

/** 지금 자리를 [index] 음으로 옮겨 고른다. 준비 상태의 재생 시작점과 운지법이 이 음을 가리킨다. */
private fun PracticeUiState.selectEvent(index: Int): PracticeUiState {
    val cell = cellForEvent(index)
    return copy(selected = index, selectedCell = cell, position = if (mode == PracticeMode.Ready) index else position)
}

/** 구간이 정해지면 시작 자리를 구간 첫 음으로 옮기고 회차를 처음으로 되돌린다. */
private fun PracticeUiState.alignToSection(): PracticeUiState {
    val sectionState = sectionPractice ?: return this
    val updated = copy(sectionPractice = sectionState.copy(round = 1))
    val first = updated.sectionEvents?.first ?: return updated
    return updated.selectEvent(first).copy(step = updated.step.copy(target = first, phase = StepPhase.Idle))
}

/**
 * ‘구간 연습’ 버튼. 켜면 [recommended](없으면 지금 고른 정간 하나)를 구간으로 잡고, 끄면 곡 전체 연습으로 돌아간다.
 * 재생·한 음씩 중지는 ViewModel이 한다.
 */
fun PracticeUiState.toggleSectionPractice(recommended: ScoreSection? = sectionPractice?.recommended): PracticeUiState {
    if (sectionPractice != null) return copy(sectionPractice = null, step = step.copy(phase = StepPhase.Idle))
    return copy(sectionPractice = PracticeSectionState(section = recommended, recommended = recommended), panel = null).alignToSection()
}

/** 연주 결과·연습 기록에서 들어와 구간 연습을 [section](추천 구간)으로 켠다. 이미 켜져 있으면 그대로 둔다. */
fun PracticeUiState.openSectionPractice(section: ScoreSection?, recommended: ScoreSection?): PracticeUiState {
    if (sectionPractice != null) return this
    return copy(sectionPractice = PracticeSectionState(section = section ?: recommended, recommended = recommended)).alignToSection()
}

/**
 * 구간 연습 중 정간 [position]을 눌렀을 때. 처음 누르면 그 정간 하나를 잡고 끝 정간을 기다리고,
 * 두 번째로 누르면 첫 정간과 이어 구간을 마무리한다(앞뒤 순서와 관계없이).
 */
fun PracticeUiState.tapSection(position: ScorePosition): PracticeUiState {
    val sectionState = sectionPractice ?: return this
    val start = sectionState.section?.start
    return if (!sectionState.picking || start == null) {
        copy(sectionPractice = sectionState.copy(section = ScoreSection(position, position), picking = true, picked = false, round = 1))
    } else {
        copy(sectionPractice = sectionState.copy(section = ScoreSection.between(start, position), picking = false, picked = true))
            .alignToSection()
    }
}

/** ‘취약 구간으로’: 추천 구간으로 되돌린다. */
fun PracticeUiState.resetSectionToRecommended(): PracticeUiState {
    val sectionState = sectionPractice ?: return this
    return copy(sectionPractice = sectionState.copy(section = sectionState.recommended, picking = false, picked = false)).alignToSection()
}

fun PracticeUiState.selectSectionRepeat(repeat: SectionRepeat): PracticeUiState {
    val sectionState = sectionPractice ?: return this
    return copy(sectionPractice = sectionState.copy(repeat = repeat, round = 1))
}

/** 재생이 구간 끝을 지났는지. */
val PracticeUiState.passedSectionEnd: Boolean
    get() {
        val range = sectionEvents ?: return false
        return position > range.last
    }

/**
 * 구간 끝에 닿았을 때: 남은 회차가 있으면 다음 회차로 구간 처음에 돌아가고(true), 정한 횟수를 다 했으면 구간 처음에 멈춘다(false).
 * ‘계속’이면 언제나 되풀이한다.
 */
fun PracticeUiState.loopSection(): Pair<PracticeUiState, Boolean> {
    val sectionState = sectionPractice ?: return this to false
    val first = sectionEvents?.first ?: return this to false
    val total = sectionState.repeat.count
    val again = total == null || sectionState.round < total
    val next = copy(
        sectionPractice = sectionState.copy(round = if (again) sectionState.round + 1 else sectionState.round),
        position = first, eventElapsedMs = 0.0, pausedFraction = 0f, eventStartedAt = null, follow = true,
    )
    return (if (again) next else next.copy(playing = false)) to again
}

/** 구간 연습 중 정한 횟수를 다 들었으면 다시 누를 때 첫 회차부터. */
fun PracticeUiState.restartSectionRounds(): PracticeUiState {
    val sectionState = sectionPractice ?: return this
    return copy(sectionPractice = sectionState.copy(round = 1))
}

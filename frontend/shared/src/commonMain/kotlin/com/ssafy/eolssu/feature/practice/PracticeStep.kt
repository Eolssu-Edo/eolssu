package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.feature.practice.component.fingeringFor

/*
 * 2차 PRACTICE-01 '한 음씩 따라 불기'(와이어프레임 Practice-Ext의 STEP_JS·STEP_UI).
 * 혼자 연습에서만 켤 수 있고, 켜면 악보가 시간대로 흐르지 않고 맞는 음을 불어야 다음 음으로 넘어간다.
 * 판정은 기기 안에서만 하며 녹음을 서버에 보내거나 분석하지 않는다. 점수도 매기지 않는다.
 * 판정 기준은 서버의 한 음씩 연습 기준(PRA-06)이고, 실제 판정기는 `PracticeStepPitch.kt`에 있다.
 */

/**
 * 한 음씩 따라 불기의 진행 단계.
 * [Idle]은 아직 시작하지 않음, [Listening]은 '내 연주'를 듣는 중(녹음 중), [Paused]는 멈춤, [Finished]는 마지막 음까지 따라 붊.
 * 멈춘 자리에서 다시 누르면 카운트다운 없이 바로 듣는다(S15P31E102-645).
 */
enum class StepPhase { Idle, Listening, Paused, Finished }

/**
 * 한 음씩 따라 불기 상태.
 *
 * @property on 설정 옆 '한 음씩' 토글이 켜졌는지.
 * @property target 지금 기다리는 음의 연주 순서([ScoreEvent.index]). 이음(ㅡ)·쉼(△)·빈 자리는 고르지 않는다.
 * @property manualConfirm 판정기가 스스로 판정하지 못해 화면에 임시 '맞게 분 것으로 넘기기' 버튼을 보이는지([StepNoteJudge.manualConfirm]).
 * @property available 한 음씩을 내놓는지. 서버에 이 악기의 판정 기준이 없으면(404) false라 토글을 숨긴다.
 */
data class PracticeStep(
    val available: Boolean = true,
    val on: Boolean = false,
    val phase: StepPhase = StepPhase.Idle,
    val target: Int = 0,
    val manualConfirm: Boolean = true,
)

/** 한 음씩 따라 불기에서 기다리는 음. [index]는 연주 순서, [note]는 그 자리의 율명, [sigimsae]는 시김새가 붙었는지다. */
data class StepTarget(val index: Int, val note: ScoreNote, val sigimsae: Boolean = false)

/**
 * 한 음씩 따라 불기의 '맞는 음을 불었다' 판정 자리. 판정은 기기 안에서만 하고 서버에 보내지 않는다.
 * 실제 기기 안 음높이 판정은 [PitchStepNoteJudge]이고, 그 기준값은 서버가 준다. 이 화면은 기준값을 갖지 않는다.
 */
interface StepNoteJudge {
    /** true면 스스로 판정하지 못하는 대역이라 화면이 임시 '맞게 분 것으로 넘기기' 버튼을 보인다. */
    val manualConfirm: Boolean

    /**
     * [target] 음을 기다린다. 맞게 불면 [onMatched]에 그 음의 연주 순서를 넘긴다(어느 스레드에서 불러도 된다).
     * 이미 듣는 중이면 기다리는 음만 바꾼다.
     */
    fun listen(target: StepTarget, onMatched: (index: Int) -> Unit)

    /** 듣기를 멈추고 마이크 같은 자원을 놓는다. 멈춘 뒤에 들어온 판정은 화면이 버린다. */
    fun stop()
}

/**
 * 기기에서 판정할 수 없을 때의 대역. 아무것도 듣지 않고, 화면의 임시 버튼으로만 다음 음으로 넘긴다.
 * 음높이 관측이 없는 플랫폼(현재 iOS·Web), 판정 기준을 받지 못했을 때, 높이를 셀 수 없는 악보에서 쓴다.
 */
class ManualStepNoteJudge : StepNoteJudge {
    override val manualConfirm: Boolean = true
    override fun listen(target: StepTarget, onMatched: (index: Int) -> Unit) = Unit
    override fun stop() = Unit
}

/** 소리를 내야 하는 음인지. 이음(ㅡ)·쉼(△)·빈 자리는 건너뛴다. */
val ScoreNote.isStepTarget: Boolean get() = this is ScoreNote.Pitch || this is ScoreNote.SourcePitch

/** 한 음씩 따라 불기가 기다리는 음들의 연주 순서. 음이 있는 자리만 고른다. */
fun stepTargets(timeline: List<ScoreEvent>): List<Int> = timeline.filter { it.note.isStepTarget }.map { it.index }

/** 율명의 한글 읽기(예: 黃 → 황). 율명이 아니면 null. */
val ScoreNote.reading: String?
    get() = when (this) {
        is ScoreNote.Pitch -> yul.hangul
        is ScoreNote.SourcePitch -> hangul
        else -> null
    }

/** 토글이 켜져 진행 바 대신 두 칸(지금 음·내 연주)을 보이는 상태. 혼자 연습에서만. */
val PracticeUiState.stepUi: Boolean get() = solo && step.on

/** 한 음씩이 진행 중이라 악보 고르기·운지법 열기를 막는 단계(듣는 중). */
val PracticeUiState.stepBusy: Boolean get() = stepUi && step.phase == StepPhase.Listening

/**
 * 악보에 연두 테두리로 보일 지금 음. 시작 전·끝난 뒤에는 없다. 테두리는 정간에만 두고 곁줄에는 두지 않는다.
 * 운지법과 함께 보기도 이 음이 든 줄에 같은 테두리를 두고 그 페이지로 넘긴다.
 */
val PracticeUiState.stepCurrentIndex: Int?
    get() = if (stepUi && (step.phase == StepPhase.Listening || step.phase == StepPhase.Paused)) step.target else null

/** '현재 정간' 칸을 누르면 낼 운지 예시음(S15P31E102-717). 한 음씩이 꺼져 있거나 운지표에 없는 음이면 없다. */
val PracticeUiState.stepNoteToPlay: FingeringNote?
    get() {
        if (!stepUi) return null
        // 운지법 팝업과 같은 규칙으로, 공통 악보의 율명·옥타브까지 운지표의 같은 음을 찾는다.
        val note = stepShownIndex?.let { song?.timeline?.getOrNull(it) }?.note ?: return null
        return instrument.fingeringFor(note)
    }

/** '현재 정간' 칸에 보일 음. 시작 전이면 첫 음. */
val PracticeUiState.stepShownIndex: Int?
    get() {
        val targets = stepTargetsInSection()
        if (targets.isEmpty()) return null
        return if (step.phase == StepPhase.Idle) targets.first() else step.target
    }

/** 지금 음이 몇 번째인지(1부터)와 전체 음 수. */
val PracticeUiState.stepProgress: Pair<Int, Int>
    get() {
        val targets = stepTargetsInSection()
        val shown = stepShownIndex ?: return 0 to 0
        val k = targets.indexOfFirst { it >= shown }.let { if (it < 0) targets.lastIndex else it }
        return (k + 1) to targets.size
    }

/**
 * '한 음씩' 토글. 혼자 연습의 준비·모범 연주 상태에서만 바뀐다.
 * 끄면 진행을 멈춘 채 두고(다시 켜고 ‘내 연주’를 누르면 그 자리부터 이어서), 모범 연주 중에 켜면 모범 연주를 멈추고 듣던 자리의 음부터 이어서 시작하도록 멈춤으로 둔다.
 * 모범 연주 음원 정지는 ViewModel이 한다.
 */
fun PracticeUiState.toggleStep(): PracticeUiState {
    if (!solo || !step.available || (mode != PracticeMode.Ready && mode != PracticeMode.Model)) return this
    if (step.on) {
        val phase = if (step.phase == StepPhase.Listening) StepPhase.Paused else step.phase
        return copy(step = step.copy(on = false, phase = phase))
    }
    if (mode == PracticeMode.Model) {
        val targets = stepTargetsInSection()
        val target = targets.firstOrNull { it >= position } ?: targets.lastOrNull() ?: 0
        return stopModel().copy(modelAudioStatus = ModelAudioStatus.Idle, step = step.copy(on = true, phase = StepPhase.Paused, target = target))
    }
    return copy(step = step.copy(on = true))
}

/**
 * 오른쪽 '내 연주' 칸을 누름. 듣는 중이면 멈추고, 멈춰 있으면 카운트다운 없이 그 자리부터 바로 이어 가며,
 * 시작 전·끝난 뒤면 첫 음부터 바로 듣는다(와이어프레임 `recToggle`).
 */
fun PracticeUiState.tapStepPlaying(): PracticeUiState {
    if (!stepUi || mode != PracticeMode.Ready) return this
    val targets = stepTargetsInSection()
    if (targets.isEmpty()) return this
    return when (step.phase) {
        StepPhase.Listening -> copy(step = step.copy(phase = StepPhase.Paused))
        StepPhase.Paused -> copy(panel = null, listening = null, follow = true, step = step.copy(phase = StepPhase.Listening))
        StepPhase.Idle, StepPhase.Finished ->
            copy(panel = null, listening = null, follow = true, step = step.copy(phase = StepPhase.Listening, target = targets.first()))
    }
}

/**
 * 기다리던 음 [index]를 맞게 불었음. 다음 음(이음·쉼은 건너뜀)으로 넘어가고, 마지막 음이면 끝낸다.
 * 듣는 중이 아니거나 이미 다른 음을 기다리면(늦게 도착한 판정) 무시한다.
 */
fun PracticeUiState.matchStepNote(index: Int): PracticeUiState {
    if (!stepUi || step.phase != StepPhase.Listening || index != step.target) return this
    val targets = stepTargetsInSection()
    val next = targets.firstOrNull { it > index }
    if (next != null) return copy(follow = true, step = step.copy(target = next))
    // 구간 연습도 구간 마지막 음을 불면 끝낸다(S15P31E102-794).
    return copy(step = step.copy(phase = StepPhase.Finished))
}

/** 화면을 벗어나거나 다른 동작으로 멈춤. 듣는 중이면 그 자리에서 멈춘다. */
fun PracticeUiState.pauseStep(): PracticeUiState =
    if (step.phase == StepPhase.Listening) copy(step = step.copy(phase = StepPhase.Paused)) else this

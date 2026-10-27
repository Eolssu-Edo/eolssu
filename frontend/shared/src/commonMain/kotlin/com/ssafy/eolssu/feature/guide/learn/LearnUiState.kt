package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.feature.guide.GuideInstrument

/**
 * LEARN-02 상태. 단계 목록과 단계 상세가 함께 쓴다.
 *
 * @property done 악기별 완료 표시한 단계 번호. 잠금 없이 아무 단계나 표시할 수 있고, 시연용이라 앱을 다시 켜면 지워진다.
 * @property playing '음 듣기'를 누른 율명. 음원이 없어 버튼만 잠시 강조한다.
 * @property songIds 8단계 곡 후보 제목 → 같은 제목의 평가 가능한 곡 식별자.
 */
data class LearnUiState(
    val instrument: GuideInstrument = GuideInstrument.Default,
    val isDropdownOpen: Boolean = false,
    val done: Map<GuideInstrument, Set<Int>> = emptyMap(),
    val playing: String? = null,
    val songIds: Map<String, String> = emptyMap(),
) {
    /** 지금 악기에서 완료 표시한 단계. */
    val doneStages: Set<Int> get() = done[instrument].orEmpty()

    /** 진행률에 세는 필수 단계 중 완료한 수. */
    val requiredDoneCount: Int get() = RequiredLearnStages.count { it.number in doneStages }

    val progress: Float get() = requiredDoneCount.toFloat() / RequiredLearnStages.size

    /** 아직 완료하지 않은 첫 단계(선택 단계 포함). 모두 마쳤으면 null. */
    val nextStage: LearnStage? get() = LearnStages.firstOrNull { it.number !in doneStages }

    fun isDone(stage: Int): Boolean = stage in doneStages
}

internal fun LearnUiState.toggleDropdown() = copy(isDropdownOpen = !isDropdownOpen)

internal fun LearnUiState.dismissDropdown() = copy(isDropdownOpen = false)

/** 악기를 바꿔도 악기별 완료 표시는 그대로 둔다. */
internal fun LearnUiState.selectInstrument(instrument: GuideInstrument) = copy(instrument = instrument, isDropdownOpen = false)

internal fun LearnUiState.resetDone() = copy(done = done + (instrument to emptySet()))

internal fun LearnUiState.toggleDone(stage: Int): LearnUiState {
    val stages = doneStages
    return copy(done = done + (instrument to if (stage in stages) stages - stage else stages + stage))
}

/** '확인했어요 · 다음': 완료로 표시한다. 이어서 갈 단계는 [stageAfterConfirm]. */
internal fun LearnUiState.confirm(stage: Int) = copy(done = done + (instrument to doneStages + stage))

/** [stage]를 확인한 뒤 갈 단계. 마지막 단계면 null(단계 목록). */
fun stageAfterConfirm(stage: Int): Int? = if (stage < LearnStages.size) stage + 1 else null

internal fun LearnUiState.listen(hanja: String) = copy(playing = hanja)

internal fun LearnUiState.finishListening(hanja: String) = if (playing == hanja) copy(playing = null) else this

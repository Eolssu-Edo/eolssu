package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument

/** 악기(트랙) 하나의 단계 목록 불러오기 상태(LRN-01). */
sealed interface LearnTrackLoad {
    data object Loading : LearnTrackLoad

    data object Failed : LearnTrackLoad

    data class Loaded(val stages: List<LearnStage>) : LearnTrackLoad
}

/** 단계 상세 내용 불러오기 상태(LRN-02). */
sealed interface LearnDetailLoad {
    data object Loading : LearnDetailLoad

    data object Failed : LearnDetailLoad

    data class Loaded(val detail: LearnStageDetail) : LearnDetailLoad
}

/** 완료 표시를 서버에 남기지 못했을 때 알리는 안내. */
enum class LearnSaveError { Mark, Reset }

/**
 * LEARN-02 상태. 단계 목록과 단계 상세가 함께 쓴다.
 *
 * @property tracks 악기별 단계 목록. 악기를 처음 고를 때 받는다.
 * @property done 악기별 완료 표시한 단계 번호(서버 진도). 잠금 없이 아무 단계나 표시할 수 있다. 표시를 바꾸면 먼저 보이고 서버에 남긴다.
 * @property details 악기·단계별 상세 내용. 단계 상세를 처음 열 때 받는다.
 * @property saveError 서버에 남기지 못해 표시를 되돌렸다. 화면이 안내를 띄운 뒤 지운다.
 * @property playing '음 듣기'를 누른 율명. 음원이 없어 버튼만 잠시 강조한다.
 * @property songIds 곡 제목 → 같은 제목의 평가 가능한 곡 식별자(8단계 곡 후보).
 * @property selectedStage 태블릿 가로 배치(단계 목록 + 단계 상세 한 화면)에서 고른 단계. 고르지 않았으면 null.
 * @property viewedCard 휴대폰·태블릿 세로 단계 목록에서 넘겨 보던 카드. 넘기거나 상세를 열면 남고, 없으면 [cardStage]가 다음에 할 단계를 보여 준다.
 */
data class LearnUiState(
    val instrument: GuideInstrument = GuideInstrument.Default,
    val isDropdownOpen: Boolean = false,
    val tracks: Map<GuideInstrument, LearnTrackLoad> = emptyMap(),
    val done: Map<GuideInstrument, Set<Int>> = emptyMap(),
    val details: Map<GuideInstrument, Map<Int, LearnDetailLoad>> = emptyMap(),
    val saveError: LearnSaveError? = null,
    val playing: String? = null,
    val songIds: Map<String, String> = emptyMap(),
    val selectedStage: Int? = null,
    val viewedCard: Int? = null,
    val songsLoading: Boolean = true,
    val songsFailed: Boolean = false,
) {
    /** 지금 악기의 단계 목록 상태. 아직 요청하지 않았으면 받는 중으로 본다. */
    val track: LearnTrackLoad get() = tracks[instrument] ?: LearnTrackLoad.Loading

    /** 지금 악기의 단계(순서대로). 받기 전이면 비어 있다. */
    val stages: List<LearnStage> get() = (track as? LearnTrackLoad.Loaded)?.stages.orEmpty()

    private val requiredStages: List<LearnStage> get() = stages.filterNot { it.optional }

    /** 진행률에 세는 필수 단계 수. */
    val requiredCount: Int get() = requiredStages.size

    fun stageOf(number: Int): LearnStage? = stages.firstOrNull { it.number == number }

    /** 지금 악기에서 완료 표시한 단계. */
    val doneStages: Set<Int> get() = done[instrument].orEmpty()

    /** 진행률에 세는 필수 단계 중 완료한 수. */
    val requiredDoneCount: Int get() = requiredStages.count { it.number in doneStages }

    val progress: Float get() = if (requiredCount == 0) 0f else requiredDoneCount.toFloat() / requiredCount

    /** 아직 완료하지 않은 첫 단계(선택 단계 포함). 모두 마쳤거나 받기 전이면 null. */
    val nextStage: LearnStage? get() = stages.firstOrNull { it.number !in doneStages }

    fun isDone(stage: Int): Boolean = stage in doneStages

    /** 지금 악기의 [stage] 상세 내용 상태. 아직 요청하지 않았으면 받는 중으로 본다. */
    fun detail(stage: Int): LearnDetailLoad = details[instrument]?.get(stage) ?: LearnDetailLoad.Loading

    /** 태블릿 가로 배치 오른쪽 칸의 단계. 고른 단계가 없으면 다음에 할 단계, 모두 마쳤으면 1단계(와이어프레임과 같다). */
    val shownStage: Int get() = selectedStage ?: nextStage?.number ?: FirstStage

    /** 단계 목록 카드에 펼칠 단계. 처음에는 다음에 할 단계(모두 마쳤으면 1단계), 넘겨 보거나 상세를 연 뒤에는 그 단계다. */
    val cardStage: Int get() = viewedCard ?: nextStage?.number ?: FirstStage

    /** [stage]를 확인한 뒤 갈 단계. 마지막 단계면 null(단계 목록). */
    fun stageAfterConfirm(stage: Int): Int? = stages.firstOrNull { it.number > stage }?.number
}

private const val FirstStage = 1

internal fun LearnUiState.toggleDropdown() = copy(isDropdownOpen = !isDropdownOpen)

internal fun LearnUiState.dismissDropdown() = copy(isDropdownOpen = false)

/** 악기를 바꿔도 악기별 완료 표시는 그대로 둔다. 카드는 그 악기의 다음에 할 단계로 돌아간다. */
internal fun LearnUiState.selectInstrument(instrument: GuideInstrument) = copy(instrument = instrument, isDropdownOpen = false, viewedCard = null)

/** [instrument]의 단계 목록을 받기 시작한다. */
internal fun LearnUiState.trackLoading(instrument: GuideInstrument) = copy(tracks = tracks + (instrument to LearnTrackLoad.Loading))

/** [instrument]의 단계 목록과 서버 완료 표시를 받았다. [stages]가 null이면 받지 못했다. */
internal fun LearnUiState.trackLoaded(instrument: GuideInstrument, stages: List<LearnStage>?, doneStages: Set<Int>) =
    if (stages == null) copy(tracks = tracks + (instrument to LearnTrackLoad.Failed))
    else copy(tracks = tracks + (instrument to LearnTrackLoad.Loaded(stages)), done = done + (instrument to doneStages))

/** 지금 악기의 [stage] 상세 상태를 바꾼다. */
internal fun LearnUiState.withDetail(instrument: GuideInstrument, stage: Int, load: LearnDetailLoad) =
    copy(details = details + (instrument to details[instrument].orEmpty() + (stage to load)))

/** 완료 표시를 모두 지우면 카드도 첫 단계(다음에 할 단계)로 돌아간다. */
internal fun LearnUiState.resetDone() = copy(done = done + (instrument to emptySet()), viewedCard = null)

/** 단계 카드를 [stage]로 넘긴다. 상세에서 보던 단계 · '확인했어요' 뒤 이어서 연 단계도 여기에 남아 목록에 돌아오면 그 카드다. 없는 단계 번호는 무시한다. */
internal fun LearnUiState.viewCard(stage: Int) = if (stageOf(stage) != null) copy(viewedCard = stage) else this

internal fun LearnUiState.toggleDone(stage: Int): LearnUiState {
    val stages = doneStages
    return copy(done = done + (instrument to if (stage in stages) stages - stage else stages + stage))
}

/** '확인했어요 · 다음': 완료로 표시한다. 이어서 갈 단계는 [LearnUiState.stageAfterConfirm]. */
internal fun LearnUiState.confirm(stage: Int) = copy(done = done + (instrument to doneStages + stage))

/** [instrument]의 [stage] 완료 표시를 [completed]로 되돌린다(서버에 남기지 못함). */
internal fun LearnUiState.revertDone(instrument: GuideInstrument, stage: Int, completed: Boolean, error: LearnSaveError): LearnUiState {
    val stages = done[instrument].orEmpty()
    return copy(done = done + (instrument to if (completed) stages + stage else stages - stage), saveError = error)
}

internal fun LearnUiState.listen(hanja: String) = copy(playing = hanja)

internal fun LearnUiState.finishListening(hanja: String) = if (playing == hanja) copy(playing = null) else this

/** 가로 배치에서 [stage]를 오른쪽 칸에 연다. 없는 단계 번호나 null이면 고른 단계를 지운다. */
internal fun LearnUiState.selectStage(stage: Int?) =
    copy(selectedStage = stage?.takeIf { stageOf(it) != null }, isDropdownOpen = false)

/** 가로 배치에서 보이는 단계의 완료 표시를 바꾼다. 다음에 할 단계가 바뀌어도 보던 단계에 머물도록 그 단계를 고른다. */
internal fun LearnUiState.toggleShownDone(stage: Int) = selectStage(stage).toggleDone(stage)

/** 단계 상세 아래 주 버튼(`.lbar`): 설명 단계는 '확인했어요 · 다음', 듣기 단계는 '모범 연주 듣기', 판정 단계는 '연습 시작'. */
internal enum class LearnStageAction { Confirm, ListenModel, Practice }

internal val LearnStage.primaryAction: LearnStageAction
    get() = when (judge) {
        LearnJudge.Read -> LearnStageAction.Confirm
        LearnJudge.Listen -> LearnStageAction.ListenModel
        LearnJudge.Pitch, LearnJudge.Beat -> LearnStageAction.Practice
    }

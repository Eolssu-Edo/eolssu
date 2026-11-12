package com.ssafy.eolssu.feature.practice

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuPageIconButton
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelSheet
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_settings
import org.jetbrains.compose.resources.stringResource

/**
 * 구간 연습·구간 재평가가 연습 화면에서 가져온 설정·보기 동작(S15P31E102-776).
 * 설정 시트는 연습 화면과 같은 [PracticePanelSheet]를 쓰고, 값은 연습 화면과 같은 설정 저장소에 바로 저장한다.
 */
class SectionToolActions(
    val onOpenSettings: () -> Unit = {},
    val onOpen: (PracticePanel) -> Unit = {},
    val onClose: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDraft: ((PracticeDraft) -> PracticeDraft) -> Unit = {},
    val onToggleBigScore: () -> Unit = {},
    val onTogglePreview: () -> Unit = {},
    val onSetInstrument: (String) -> Unit = {},
    val onToggleFingeringView: () -> Unit = {},
    val onToggleNotation: () -> Unit = {},
)

/** 연습 화면 머리 오른쪽과 같은 톱니(연습 설정). */
@Composable
internal fun SectionSettingsButton(onClick: () -> Unit) {
    EolssuPageIconButton(EolssuIcons.Cog, stringResource(Res.string.practice_settings), onClick)
}

/**
 * 연습 화면과 같은 설정 시트. 구간 속도는 구간 고르기에서 정했으므로 속도 줄은 잠겨 보인다.
 * [evaluation]이면 연주 평가처럼 쉬운 표기·운지법은 제목 옆 스위치에 있으므로 시트에서 뺀다.
 */
@Composable
internal fun SectionPanelSheet(state: SectionLoopUiState, evaluation: Boolean, actions: SectionToolActions, tablet: Boolean = false) {
    if (state.panel == null) return
    PracticePanelSheet(
        state = state.asPracticeState(evaluation),
        actions = actions.asPanelActions(),
        tablet = tablet,
        // 태블릿은 연습 화면처럼 제목 옆 스위치가 없어 쉬운 표기를 시트에 둔다.
        titleToggles = evaluation && !tablet,
    )
}

/** 연습 화면의 설정 시트·태블릿 상태 패널이 부르는 동작으로 옮긴다. 악보 음 누르기·운지 소리는 없다. */
internal fun SectionToolActions.asPanelActions() = PracticePanelActions(
    onClose = onClose,
    onDismiss = onDismiss,
    onOpen = onOpen,
    onDraft = onDraft,
    onToggleBigScore = onToggleBigScore,
    onMoveFingering = {},
    fingeringAudio = { _, _ -> },
    onTogglePreview = onTogglePreview,
    onSetInstrument = onSetInstrument,
    onToggleFingeringView = onToggleFingeringView,
)

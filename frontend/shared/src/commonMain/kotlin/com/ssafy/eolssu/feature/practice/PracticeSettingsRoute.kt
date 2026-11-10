package com.ssafy.eolssu.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelSheet
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 곡 정보에서 여는 연주 평가 전 '연습 설정' 시트(S15P31E102-705). 연주 평가 화면의 톱니와 같은 시트(쉬운 표기·장단·속도·운지법과 함께 보기)이고,
 * 바꾼 값은 공유 연습 설정(`PracticeSettingsStore`)에 바로 들어가 곡 정보에서 시작한 연주 평가가 그대로 쓴다.
 * 시트를 닫으면(배경 누르기·뒤로 가기) [onClose]를 부른다. 장단 미리듣기는 닫을 때 멈춘다.
 */
@Composable
fun PracticeSettingsRoute(
    songId: String,
    onClose: () -> Unit,
    viewModel: PracticeViewModel = koinViewModel(key = "settings-$songId") { parametersOf(songId, false, false, false, null, false) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) { viewModel.openPanel(PracticePanel.Settings) }
    val dismiss = {
        viewModel.dismissPanels()
        close()
    }
    PracticePanelSheet(
        state,
        PracticePanelActions(
            // 하위 설정(쉬운 표기·장단·속도)의 뒤로 가기는 연습 설정으로, 연습 설정에서 닫으면 시트를 닫는다.
            onClose = { if (state.panel == PracticePanel.Settings) dismiss() else viewModel.closePanel() },
            onDismiss = dismiss,
            onOpen = viewModel::openPanel,
            onDraft = viewModel::updateDraft,
            onToggleBigScore = viewModel::toggleBigScore,
            onMoveFingering = viewModel::moveFingering,
            // 연습 설정에서는 운지법 패널을 열지 않는다.
            fingeringAudio = { _, _ -> },
            onTogglePreview = viewModel::togglePreview,
            onSetInstrument = viewModel::setInstrument,
            onToggleFingeringView = viewModel::toggleFingeringView,
        ),
        tablet = currentWindowLayout().widthClass != WindowWidthClass.Compact,
    )
}

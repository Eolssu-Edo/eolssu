package com.ssafy.eolssu.feature.guide

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.guide.component.ReferenceAudioControls
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.Blow

/**
 * GUIDE-01 연결. 기초부터 배우기에서 오면 [instrument]·[blow]로 시작하고, 뒤로 가기는 들어온 화면으로 돌아간다.
 * 각 운지 카드에서 정확한 라벨의 녹음 옵션을 고르고, 화면이 공유하는 플레이어로 재생한다.
 */
@Composable
fun FingeringRoute(
    onBack: () -> Unit,
    instrument: GuideInstrument = GuideInstrument.Default,
    blow: Blow = Blow.Mid,
    viewModel: FingeringViewModel = koinViewModel { parametersOf(instrument, blow) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val audio = rememberReferenceAudioController(state.instrument, state.shownBlow)

    FingeringScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleDropdown = viewModel::toggleDropdown,
        onDismissDropdown = viewModel::dismissDropdown,
        onSelectInstrument = viewModel::selectInstrument,
        onSelectBlow = viewModel::selectBlow,
        onRetryInstruments = viewModel::retryCatalog,
        noteAudioContent = { note ->
            ReferenceAudioControls(
                ReferenceAudioSelection.forNote(state.instrument, note, state.shownBlow),
                audio,
                showLoadingMessage = false,
            )
        },
    )
}

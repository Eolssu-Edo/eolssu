package com.ssafy.eolssu.feature.guide

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.guide.component.FingeringCatalogDialog
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * GUIDE-01 연결. 기초부터 배우기에서 오면 [instrument]·[blow]로 시작하고, 뒤로 가기는 들어온 화면으로 돌아간다.
 * 음 듣기는 정확한 라벨의 녹음 목록을 연다. 여러 주법·구간 중 선택한 파일을 외부 재생기로 연다.
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
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var audioSelection by remember(state.instrument, state.shownBlow) { mutableStateOf<ReferenceAudioSelection?>(null) }

    FingeringScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleDropdown = viewModel::toggleDropdown,
        onDismissDropdown = viewModel::dismissDropdown,
        onSelectInstrument = viewModel::selectInstrument,
        onSelectBlow = viewModel::selectBlow,
        onOpenCatalog = { catalogOpen = true },
        onListen = { note ->
            audioSelection = ReferenceAudioSelection.forNote(state.instrument, note, state.shownBlow)
        },
    )
    if (catalogOpen) FingeringCatalogDialog(state, viewModel::retryCatalog, onClose = { catalogOpen = false })
    audioSelection?.let { ReferenceAudioRoute(it, onClose = { audioSelection = null }) }
}

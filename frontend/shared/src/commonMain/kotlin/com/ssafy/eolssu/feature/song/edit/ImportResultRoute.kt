package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * EDIT-06 연결. '편집 시작'(오선보는 '오선보와 함께 편집')은 [onEdit]에 아직 확인하지 않은 정간 수를 넘겨 편집기(EDIT-02·03)에 간다.
 */
@Composable
fun ImportResultRoute(
    kind: ImportKind,
    hwang: HwangPitch,
    onBack: () -> Unit,
    onEdit: (pendingChecks: Int) -> Unit,
    viewModel: ImportResultViewModel = koinViewModel(key = "import-result-${kind.routeId}-${hwang.name}") { parametersOf(kind, hwang) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImportResultScreen(
        state = state,
        onBack = onBack,
        onSelectCell = viewModel::select,
        onChoose = viewModel::choose,
        onTogglePlay = viewModel::togglePlay,
        onEdit = { onEdit(state.openChecks.size) },
    )
}

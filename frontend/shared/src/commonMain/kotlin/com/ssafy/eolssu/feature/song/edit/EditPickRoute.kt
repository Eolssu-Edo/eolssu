package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** 불러올 악보 고르기 연결. 악기를 고른 뒤 악보를 고르고 확인하면 기존 또는 복사한 개인 악보 ID로 [onOpenSheet]를 호출한다. 뒤로 가기는 악보 추가(EDIT-01)로 간다. */
@Composable
fun EditPickRoute(
    onBack: () -> Unit,
    onOpenSheet: (String) -> Unit,
    viewModel: EditPickViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EditPickScreen(
        state = state,
        onBack = onBack,
        onSelectInstrument = viewModel::selectInstrument,
        onRetry = viewModel::retry,
        onSelectTab = viewModel::selectTab,
        onSearch = viewModel::search,
        onToggleSort = viewModel::toggleSort,
        onToggle = viewModel::toggle,
        onConfirm = { viewModel.confirmSelection(onOpenSheet) },
    )
}

package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** 불러올 악보 고르기 연결. 악기를 고른 뒤 악보를 고르고 '선택'을 누르면 [onSelected]로 편집(내 악보) · 복사본 편집(제공 곡)에 간다. 뒤로 가기는 악보 추가(EDIT-01)로 간다. */
@Composable
fun EditPickRoute(
    onBack: () -> Unit,
    onSelected: (EditPickTarget) -> Unit,
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
        onConfirm = { state.picked?.takeIf { state.canSelect }?.let(onSelected) },
    )
}

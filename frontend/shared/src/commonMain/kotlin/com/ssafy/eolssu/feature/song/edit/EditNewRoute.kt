package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * EDIT-01 연결. 정간보·오선보는 [onImport]로 실제 파일·업로드 준비 화면에 가고,
 * '내 악보·제공 곡 불러오기'는 [onLoadExisting]으로 목록(EditPick)에 간다. 뒤로 가기는 내 악보로 돌아간다.
 */
@Composable
fun EditNewRoute(
    onBack: () -> Unit,
    onImport: (ImportKind, HwangPitch) -> Unit,
    onLoadExisting: () -> Unit,
    viewModel: EditNewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EditNewScreen(
        state = state,
        onBack = onBack,
        onSelectKind = viewModel::selectKind,
        onSelectHwang = viewModel::selectHwang,
        onLoadExisting = onLoadExisting,
        onOpenDevicePick = { onImport(state.kind, state.hwang) },
    )
}

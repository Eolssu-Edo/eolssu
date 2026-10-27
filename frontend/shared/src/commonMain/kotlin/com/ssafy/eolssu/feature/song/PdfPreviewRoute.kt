package com.ssafy.eolssu.feature.song

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * UPLOAD-02 연결. 저장을 마치면 [onSaved]로 내 악보 목록에 돌아간다.
 * 저장 뒤 PDF 악보 보기(VIEWER-01)는 2차 확장 화면이라 목록의 저장 안내에서 '준비 중'을 함께 알린다.
 */
@Composable
fun PdfPreviewRoute(
    uploadId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PdfPreviewViewModel = koinViewModel(key = uploadId) { parametersOf(uploadId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedSheetId) {
        if (state.savedSheetId != null) onSaved()
    }
    PdfPreviewScreen(
        state = state,
        actions = PdfPreviewActions(
            onBack = onBack,
            onSelectPage = viewModel::selectPage,
            onTitleChange = viewModel::changeTitle,
            onSelectJangdan = viewModel::selectJangdan,
            onSave = viewModel::save,
        ),
    )
}

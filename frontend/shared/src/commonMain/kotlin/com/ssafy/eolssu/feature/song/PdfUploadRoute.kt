package com.ssafy.eolssu.feature.song

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel

/** UPLOAD-01 연결. 다 올리면 '다음 · 미리보기'가 [onNext]로 올린 파일의 미리보기(UPLOAD-02)에 간다. */
@Composable
fun PdfUploadRoute(
    onBack: () -> Unit,
    onNext: (uploadId: String) -> Unit,
    viewModel: PdfUploadViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PdfUploadScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onPickFile = viewModel::pickFile,
        onRemoveFile = viewModel::removeFile,
        onNext = { state.uploaded?.let { onNext(it.id) } },
    )
}

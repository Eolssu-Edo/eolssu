package com.ssafy.eolssu.feature.song.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_msg_audio
import eolssu.shared.generated.resources.sheetedit_msg_audio_needs_save
import eolssu.shared.generated.resources.sheetedit_msg_draft
import eolssu.shared.generated.resources.sheetedit_msg_saved
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-04 연결. 편집기와 같은 [viewModel]을 받는다. 뒤로 가기와 경고 창의 '빈 정간 채우기'는 [onBack]으로 편집기에 돌아간다.
 * 저장·임시저장·음원 받기는 저장 API를 붙이기 전이라 안내만 띄운다.
 */
@Composable
fun EditPreviewRoute(viewModel: SheetEditViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = mapOf(
        SheetEditMessage.Draft to stringResource(Res.string.sheetedit_msg_draft),
        SheetEditMessage.Saved to stringResource(Res.string.sheetedit_msg_saved),
        SheetEditMessage.Audio to stringResource(Res.string.sheetedit_msg_audio),
        SheetEditMessage.AudioNeedsSave to stringResource(Res.string.sheetedit_msg_audio_needs_save),
    )
    LaunchedEffect(state.message) {
        val text = state.message?.let(messages::get) ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
    }
    // 미리듣기는 이 화면에서만 재생한다.
    DisposableEffect(Unit) { onDispose { viewModel.stopPreview() } }
    EditPreviewScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTogglePlay = viewModel::togglePreview,
        onToggleJangdan = viewModel::toggleWithJangdan,
        onGetAudio = viewModel::requestAudio,
        onSaveDraft = viewModel::saveDraft,
        onSave = viewModel::requestSave,
        onFillEmpty = {
            viewModel.fillEmpty()
            onBack()
        },
        onCloseWarning = viewModel::closeWarning,
    )
}

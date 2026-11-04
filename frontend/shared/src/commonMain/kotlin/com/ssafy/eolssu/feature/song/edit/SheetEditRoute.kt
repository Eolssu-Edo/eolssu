package com.ssafy.eolssu.feature.song.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.feature.song.edit.component.EditInputActions
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.sheetedit_msg_audio_failed
import eolssu.shared.generated.resources.sheetedit_msg_audio_ready
import eolssu.shared.generated.resources.sheetedit_msg_audio_unknown
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-02 · 03 연결. [viewModel]은 편집 화면 경로에 묶인 것을 받아 미리보기(EDIT-04)와 같은 악보를 쓴다.
 * 닫기는 [onClose]로 내 악보에, '미리보기'는 [onPreview]로, 인식 결과에서 왔으면 '확인 정간 보기'가 [onOpenChecks]로 인식 결과(EDIT-06)에 간다.
 */
@Composable
fun SheetEditRoute(
    viewModel: SheetEditViewModel,
    onClose: () -> Unit,
    onPreview: () -> Unit,
    onOpenChecks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = mapOf(
        SheetEditMessage.ComingSoon to stringResource(Res.string.common_coming_soon),
        SheetEditMessage.AudioReady to stringResource(Res.string.sheetedit_msg_audio_ready),
        SheetEditMessage.AudioFailed to stringResource(Res.string.sheetedit_msg_audio_failed),
        SheetEditMessage.AudioStatusUnknown to stringResource(Res.string.sheetedit_msg_audio_unknown),
    )
    // 미리보기의 저장 안내는 미리보기 화면이 띄운다. 편집기는 편집기에서 생긴 안내와, 뒤에서 만들던 음원의 완료·실패 알림만 띄운다.
    LaunchedEffect(state.message) {
        val text = state.message?.takeIf { it.shownInEditor() }?.let(messages::get) ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
    }
    SheetEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = SheetEditActions(
            onClose = onClose,
            onPreview = onPreview,
            onOpenChecks = onOpenChecks,
            onSelectView = viewModel::selectView,
            onSelectCell = viewModel::selectCell,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onSelectStaffNote = viewModel::selectStaffNote,
            onShiftStaffNote = viewModel::shiftStaffNote,
            input = EditInputActions(
                onSelectTab = viewModel::selectTab,
                onPut = viewModel::put,
                onPrevious = viewModel::previousCell,
                onNext = viewModel::nextCell,
                onErase = viewModel::erase,
                onSigimsae = viewModel::putSigimsae,
                onToggleSigimsae = viewModel::toggleSigimsae,
                onClearSigimsae = viewModel::clearSigimsae,
                onSplit = viewModel::split,
                onMerge = viewModel::merge,
                onAddColumn = viewModel::addColumn,
                onDeleteColumn = viewModel::deleteColumn,
                onChangeJangdan = viewModel::changeJangdan,
            ),
        ),
    )
}

package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.data.song.SheetConversionFailure
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 악보 변환 대기 화면 연결. 화면이 보이는 동안만 상태를 묻고, 완료되면 [onDone]으로 편집 화면에 보낸다.
 * 뒤로 가기와 '내 악보 목록에서 기다리기'는 [onList], 실패의 다시 불러오기는 원인과 함께 [onReimport]다.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetConversionRoute(
    sheetId: String,
    onList: () -> Unit,
    onDone: (String) -> Unit,
    onReimport: (SheetConversionFailure?) -> Unit,
    viewModel: SheetConversionViewModel = koinViewModel(key = "sheet-conversion-$sheetId") { parametersOf(sheetId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.watch()
        onPauseOrDispose { viewModel.stop() }
    }
    LaunchedEffect(state.openEditor) {
        if (state.openEditor) {
            viewModel.editorOpened()
            onDone(sheetId)
        }
    }
    BackHandler(onBack = onList)
    SheetConversionScreen(state, onList = onList, onReimport = { onReimport(state.conversion?.failure) })
}

package com.ssafy.eolssu.feature.song.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.ssafy.eolssu.core.ui.component.ScreenBackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 악보 변환 대기 화면 연결. 화면이 보이는 동안만 상태를 묻고, 완료되면 [onDone]으로 편집 화면에 보낸다.
 * 뒤로 가기와 '내 악보 목록에서 기다리기'·실패의 '내 악보 목록으로'는 [onList]다.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SheetConversionRoute(
    sheetId: String,
    onList: () -> Unit,
    onDone: (String) -> Unit,
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
    // Web 브라우저 뒤로 가기도 앱 뒤로 가기처럼 목록으로 간다(S15P31E102-1265).
    ScreenBackHandler(onBack = onList)
    SheetConversionScreen(state, onList = onList, onOpenEditor = { onDone(sheetId) })
}

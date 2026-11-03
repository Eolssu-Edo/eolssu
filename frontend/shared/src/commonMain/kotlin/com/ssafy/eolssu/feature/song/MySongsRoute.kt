package com.ssafy.eolssu.feature.song

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.MySheetKind
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.song.component.AddSheetOption
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.mysongs_saved
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * SONG-03 연결. 제공 곡과 정간보 내 악보는 [onPractice]로 평가 없는 연습 모드에 가고, 'PDF 악보 올리기'는 [onUploadPdf]로 간다.
 * PDF 올리기에서 저장하지 않고 돌아오면 악보 추가 시트가 열린 채로 보인다. 홈 '악보 추가·편집하기'로 들어오면([startOnSheets]) 내 악보 탭부터 보인다.
 * PDF 보기·MIDI 변환 결과·편집기·음원 변환은 2차 확장 화면이라 '준비 중' 안내를 띄운다.
 */
@Composable
fun MySongsRoute(
    onBack: () -> Unit,
    onPractice: (songId: String) -> Unit,
    onUploadPdf: () -> Unit,
    startOnSheets: Boolean = false,
    viewModel: MySongsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    val savedMessage = stringResource(Res.string.mysongs_saved)
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // 다른 화면에서 돌아오면 이 화면을 처음부터 다시 그리므로, 그때 PDF 올리기에서 돌아왔는지 본다.
    LaunchedEffect(Unit) {
        if (startOnSheets) viewModel.showSheetsFirst()
        viewModel.returnToScreen()
    }
    LaunchedEffect(state.savedSheet) {
        if (state.savedSheet != null) {
            showMessage(savedMessage)
            viewModel.consumeSavedNotice()
        }
    }

    MySongsScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = MySongsActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onSelectTab = viewModel::selectTab,
            onSelectFilter = viewModel::selectFilter,
            onOpenSong = onPractice,
            onOpenSheet = { sheet ->
                val songId = sheet.songId
                if (sheet.kind == MySheetKind.Jeongganbo && songId != null) onPractice(songId) else showMessage(comingSoon)
            },
            onEditSheet = { showMessage(comingSoon) },
            onOpenAddSheet = viewModel::openAddSheet,
            onCloseAddSheet = viewModel::closeAddSheet,
            onSelectAddOption = { option ->
                if (option == AddSheetOption.Pdf) {
                    viewModel.leaveForPdfUpload()
                    onUploadPdf()
                } else {
                    viewModel.closeAddSheet()
                    showMessage(comingSoon)
                }
            },
        ),
    )
}

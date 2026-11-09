package com.ssafy.eolssu.feature.song

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.song.component.SongInstrumentPicker
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.mysongs_conversion_reimport_needed
import eolssu.shared.generated.resources.mysongs_conversion_retry_failed
import eolssu.shared.generated.resources.sheet_delete_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.ssafy.eolssu.data.song.SheetConversionFailure

/**
 * SONG-03 연결. 제공 곡은 [onPractice]로 평가 없는 연습 모드에, 내 악보는 [onPracticeSheet]로 내 악보 연습(평가 버튼 없음)에 간다.
 * 내 악보 위 '악보 추가'는 시트 없이 바로 [onAddSheet]로, 변환 실패 악보의 '다시 불러오기'는 실패 원인과 함께 [onReimportSheet]로 악보 추가(EDIT-01)에, 악보마다 '편집'은 [onEditSheet]로 내 악보 편집(EDIT-02)에 가고,
 * 휴지통은 묻는 창을 거쳐 지운다(2차 결합본 결정: 악보 추가 시트와 보기 전용 PDF 화면을 뺐다).
 * 상단 바의 연습 악기 드롭다운으로 악기를 바꾸면 제공 곡 목록이 그 악기로 바뀌고, 세션이 끝났으면 [onSessionExpired]로 로그인에 간다.
 * [startOnSheets]면 내 악보 탭부터 보인다. 연습할 곡이 이어지지 않은 악보는 '준비 중' 안내를 띄운다.
 */
@Composable
fun MySongsRoute(
    onBack: () -> Unit,
    onPractice: (songId: String) -> Unit,
    onAddSheet: () -> Unit,
    onEditSheet: (MySheet) -> Unit,
    onReimportSheet: (SheetConversionFailure?) -> Unit = { onAddSheet() },
    startOnSheets: Boolean = false,
    onPracticeSheet: (songId: String, title: String) -> Unit = { songId, _ -> onPractice(songId) },
    onSessionExpired: () -> Unit = {},
    viewModel: MySongsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        if (startOnSheets) viewModel.showSheetsFirst()
    }
    // 화면이 보이는 동안에만 변환 중인 악보의 상태를 묻는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSheets()
        viewModel.watchConversions()
        onPauseOrDispose { viewModel.stopWatchingConversions() }
    }
    val reimportNeeded = stringResource(Res.string.mysongs_conversion_reimport_needed)
    val retryFailed = stringResource(Res.string.mysongs_conversion_retry_failed)
    LaunchedEffect(state.conversionNotice) {
        val notice = state.conversionNotice ?: return@LaunchedEffect
        viewModel.consumeConversionNotice()
        snackbarHostState.showSnackbar(if (notice == ConversionNotice.Reimport) reimportNeeded else retryFailed)
    }
    val deleteFailure = stringResource(Res.string.sheet_delete_failed)
    LaunchedEffect(state.deleteFailed) {
        if (state.deleteFailed) {
            viewModel.consumeDeleteFailure()
            snackbarHostState.showSnackbar(deleteFailure)
        }
    }

    MySongsScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = MySongsActions(
            onBack = onBack,
            onRetry = { if (state.tab == MySongsTab.Sheets) viewModel.refreshSheets() else viewModel.retry() },
            onSelectTab = viewModel::selectTab,
            onSelectFilter = viewModel::selectFilter,
            onOpenSong = onPractice,
            onOpenSheet = { sheet ->
                val songId = sheet.songId
                if (songId != null) onPracticeSheet(songId, sheet.title) else showMessage(comingSoon)
            },
            onEditSheet = onEditSheet,
            onAddSheet = onAddSheet,
            onDeleteSheet = viewModel::askDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            onRetryConversion = viewModel::retryConversion,
            onReimportSheet = { sheet -> onReimportSheet(state.conversionOf(sheet)?.failure) },
        ),
        instrumentPicker = { SongInstrumentPicker(viewModel.instrumentSelection, snackbarHostState, onSessionExpired) },
    )
}

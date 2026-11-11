package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.song.component.SongInstrumentPicker
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SONG-01 연결. 곡을 고르면 [onOpenSong]으로 곡 상세로 간다.
 * 태블릿 가로·넓은 웹 창에서는 화면을 옮기지 않고 오른쪽 칸에 고른 곡의 정보를 보이며(곡 상세와 같은 [SongDetailViewModel]),
 * 그 칸의 '연주 평가 시작'이 [onStartPractice]로 연주 평가 화면에 간다. 고른 곡은 목록 ViewModel이 들고 있어 창 크기가 바뀌어도 남는다.
 * 가로에서 곡을 직접 고른 채 세로로 바뀌면 [onOpenSong]으로 그 곡의 상세를 연다. 처음에 자동으로 고른 첫 곡이면 목록 그대로 둔다.
 * 상단 바의 연습 악기 드롭다운으로 악기를 바꾸면 그 악기의 곡으로 목록이 바뀌고, 세션이 끝났으면 [onSessionExpired]로 로그인에 간다.
 * [pickSongId]는 곡 상세가 가로로 바뀌어 이 화면으로 넘어올 때(배우기의 곡 열기 등) 직접 고른 것으로 둘 곡이고, 고른 뒤 [onPickTaken]으로 비운다.
 */
@Composable
fun SongListRoute(
    onBack: () -> Unit,
    onOpenSong: (songId: String) -> Unit,
    onStartPractice: (songId: String) -> Unit,
    pickSongId: String? = null,
    onPickTaken: () -> Unit = {},
    onSessionExpired: () -> Unit = {},
    viewModel: SongListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(pickSongId) {
        if (pickSongId != null) {
            viewModel.pickSong(pickSongId)
            onPickTaken()
        }
    }
    val widthClass = currentWindowLayout().widthClass
    val twoPane = widthClass == WindowWidthClass.Expanded
    val pickedId = state.pickedSong?.id
    LaunchedEffect(twoPane, pickedId) {
        if (!twoPane && pickedId != null) {
            viewModel.openPickedSong()
            onOpenSong(pickedId)
        }
    }
    val selectedId = state.selectedSong?.id
    val detailBinding: Pair<SongDetailUiState, () -> Unit>? = if (twoPane && selectedId != null) {
        key(selectedId) {
            val detailViewModel: SongDetailViewModel = koinViewModel(key = "song-list-detail-$selectedId") { parametersOf(selectedId) }
            val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
            detailState to detailViewModel::retry
        }
    } else null

    SongListScreen(
        state = state,
        widthClass = widthClass,
        detail = detailBinding?.first,
        actions = SongListActions(
            onBack = onBack,
            onQueryChange = viewModel::changeQuery,
            onClearQuery = viewModel::clearQuery,
            onSelectFilter = viewModel::selectFilter,
            onToggleSort = viewModel::toggleSort,
            onResetFilters = viewModel::resetFilters,
            onOpenSong = { songId ->
                if (twoPane) {
                    viewModel.pickSong(songId)
                } else {
                    viewModel.selectSong(songId)
                    onOpenSong(songId)
                }
            },
            onStartPractice = { selectedId?.let(onStartPractice) },
            onRetry = viewModel::retry,
            onRetryDetail = { detailBinding?.second?.invoke() },
        ),
        snackbarHostState = snackbarHostState,
        // 페이지형 상단 바는 오른쪽 여백이 6dp라, 왼쪽 뒤로 가기 원처럼 화면 끝에서 20dp에 오도록 14dp를 더 띄운다.
        instrumentPicker = {
            SongInstrumentPicker(viewModel.instrumentSelection, snackbarHostState, onSessionExpired, Modifier.padding(end = 14.dp))
        },
    )
}

package com.ssafy.eolssu.feature.song

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * SONG-01 연결. 곡을 고르면 [onOpenSong]으로 곡 상세로 간다.
 * 태블릿 가로·넓은 웹 창에서는 화면을 옮기지 않고 오른쪽 칸에 고른 곡의 정보를 보이며(곡 상세와 같은 [SongDetailViewModel]),
 * 그 칸의 '이 곡 연습하기'가 [onStartPractice]로 연습 화면에 간다. 고른 곡은 목록 ViewModel이 들고 있어 창 크기가 바뀌어도 남는다.
 */
@Composable
fun SongListRoute(
    onBack: () -> Unit,
    onOpenSong: (songId: String) -> Unit,
    onStartPractice: (songId: String) -> Unit,
    viewModel: SongListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val widthClass = currentWindowLayout().widthClass
    val twoPane = widthClass == WindowWidthClass.Expanded
    val selectedId = state.selectedSong?.id
    val detail = if (twoPane && selectedId != null) songDetailState(selectedId) else null

    SongListScreen(
        state = state,
        widthClass = widthClass,
        detail = detail,
        actions = SongListActions(
            onBack = onBack,
            onQueryChange = viewModel::changeQuery,
            onClearQuery = viewModel::clearQuery,
            onSelectFilter = viewModel::selectFilter,
            onResetFilters = viewModel::resetFilters,
            onOpenSong = { songId ->
                viewModel.selectSong(songId)
                if (!twoPane) onOpenSong(songId)
            },
            onStartPractice = { selectedId?.let(onStartPractice) },
        ),
    )
}

/** 두 칸 배치 오른쪽 칸의 곡 정보. 곡 상세 화면과 같은 ViewModel을 곡마다 이 화면 범위에 둔다. */
@Composable
private fun songDetailState(songId: String): SongDetailUiState = key(songId) {
    val viewModel: SongDetailViewModel = koinViewModel(key = "song-list-detail-$songId") { parametersOf(songId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state
}

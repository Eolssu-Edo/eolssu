package com.ssafy.eolssu.feature.song

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** SONG-01 연결. 곡을 고르면 [onOpenSong]으로 곡 상세로 간다. */
@Composable
fun SongListRoute(
    onBack: () -> Unit,
    onOpenSong: (songId: String) -> Unit,
    viewModel: SongListViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SongListScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::changeQuery,
        onClearQuery = viewModel::clearQuery,
        onSelectFilter = viewModel::selectFilter,
        onResetFilters = viewModel::resetFilters,
        onOpenSong = onOpenSong,
    )
}

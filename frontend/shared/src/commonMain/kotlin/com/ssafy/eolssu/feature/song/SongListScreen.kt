package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.component.SongCard
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.component.SongEmptyResult
import com.ssafy.eolssu.feature.song.component.SongLevelChips
import com.ssafy.eolssu.feature.song.component.SongSearchField
import com.ssafy.eolssu.feature.song.layout.SongListExpandedLayout
import com.ssafy.eolssu.feature.song.layout.SongListMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_count
import eolssu.shared.generated.resources.song_list_title
import org.jetbrains.compose.resources.stringResource

/** SONG-01 화면의 사용자 행동. [onOpenSong]은 휴대폰·태블릿 세로에서 곡 상세로 가고, 태블릿 가로에서는 오른쪽 칸의 곡을 바꾼다. */
class SongListActions(
    val onBack: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onClearQuery: () -> Unit,
    val onSelectFilter: (SongLevelFilter) -> Unit,
    val onResetFilters: () -> Unit,
    val onOpenSong: (songId: String) -> Unit,
    val onStartPractice: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryDetail: () -> Unit,
)

/**
 * SONG-01 곡 목록. 검색·난이도 필터, 결과가 없으면 조건 초기화 안내.
 * 태블릿 세로([WindowWidthClass.Medium])는 카드 2열, 태블릿 가로·넓은 웹 창([WindowWidthClass.Expanded])은
 * 왼쪽 목록과 오른쪽 곡 정보([detail], 고른 곡이 없으면 null) 두 칸을 한 화면에 보인다.
 */
@Composable
fun SongListScreen(
    state: SongListUiState,
    widthClass: WindowWidthClass,
    detail: SongDetailUiState?,
    actions: SongListActions,
) {
    when (widthClass) {
        WindowWidthClass.Compact -> SongListCompact(state, actions)
        WindowWidthClass.Medium -> SongListMediumLayout(state, actions)
        WindowWidthClass.Expanded -> SongListExpandedLayout(state, detail, actions)
    }
}

@Composable
private fun SongListCompact(state: SongListUiState, actions: SongListActions) {
    EolssuScaffold(topBar = { EolssuTopBar(stringResource(Res.string.song_list_title), onBack = actions.onBack) }) {
        val songs = state.visibleSongs
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    SongSearchField(state.query, actions.onQueryChange, actions.onClearQuery)
                    SongLevelChips(state.filter, actions.onSelectFilter, Modifier.padding(top = 12.dp, bottom = 6.dp))
                }
            }
            if (state.loadState != SongLoadState.Ready || state.songs.isEmpty()) {
                item { SongLoadNotice(state.loadState, actions.onRetry) }
            } else if (songs.isEmpty()) {
                item { SongEmptyResult(actions.onResetFilters) }
            } else if (songs.isNotEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.song_count, songs.size),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = EolssuTheme.colors.textMuted,
                    )
                }
                items(songs, key = { it.id }) { song ->
                    SongCard(song, jangdanLabel(song.jangdanName), onClick = { actions.onOpenSong(song.id) })
                }
            }
        }
    }
}

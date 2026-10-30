package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.song.component.songListItems
import com.ssafy.eolssu.feature.song.layout.SongListExpandedLayout
import com.ssafy.eolssu.feature.song.layout.SongListMediumLayout
import eolssu.shared.generated.resources.Res
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
 * SONG-01 곡 목록(보드 `Song-List` · `-Filter` · `-Search` · `-Empty`): 페이지 머리, 검색·난이도 필터, 곡 줄, 결과가 없으면 '찾는 곡이 없어요.'.
 * 태블릿 세로([WindowWidthClass.Medium])는 같은 순서를 720dp 너비로, 태블릿 가로·넓은 웹 창([WindowWidthClass.Expanded])은
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
    EolssuScaffold(topBar = { EolssuPageTopBar(stringResource(Res.string.song_list_title), onBack = actions.onBack) }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
        ) {
            songListItems(state, actions)
        }
    }
}

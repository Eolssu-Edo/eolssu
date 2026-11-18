package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.song.SongListActions
import com.ssafy.eolssu.feature.song.SongListUiState
import com.ssafy.eolssu.feature.song.component.songListItems
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_list_title
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-01 태블릿 세로 배치. 새 디자인 보드에는 태블릿 판이 없어 휴대폰 보드(`Song-List`)의 순서·간격을
 * 720dp 내용 너비로 그대로 쓴다(보드 공통 CSS의 태블릿 `.page-body` 좌우 40px). 곡을 누르면 곡 상세로 간다.
 */
@Composable
internal fun SongListMediumLayout(
    state: SongListUiState,
    actions: SongListActions,
    snackbarHostState: SnackbarHostState,
    instrumentPicker: @Composable () -> Unit,
) {
    SongTabletScaffold(
        expanded = false,
        title = stringResource(Res.string.song_list_title),
        onBack = actions.onBack,
        pageTopBar = true,
        containerColor = EolssuTheme.colors.groupedBackground,
        snackbarHostState = snackbarHostState,
        topBarRight = { instrumentPicker() },
    ) { gutter ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 22.dp, bottom = 32.dp),
        ) {
            songListItems(state, actions)
        }
    }
}

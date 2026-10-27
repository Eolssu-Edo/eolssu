package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.SongListActions
import com.ssafy.eolssu.feature.song.SongListUiState
import com.ssafy.eolssu.feature.song.component.CardGrid
import com.ssafy.eolssu.feature.song.component.SongCard
import com.ssafy.eolssu.feature.song.component.SongEmptyResult
import com.ssafy.eolssu.feature.song.component.SongLevelChips
import com.ssafy.eolssu.feature.song.component.SongSearchField
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_count
import eolssu.shared.generated.resources.song_list_title
import eolssu.shared.generated.resources.tab_song_meta_bpm
import org.jetbrains.compose.resources.stringResource

/** SONG-01 태블릿 세로 배치(와이어프레임 `Tab-SongList-Port`): 검색 · 난이도 칩 · 곡 카드 2열(장단 · 기준 속도). 곡을 누르면 곡 상세로 간다. */
@Composable
internal fun SongListMediumLayout(state: SongListUiState, actions: SongListActions) {
    SongTabletScaffold(expanded = false, title = stringResource(Res.string.song_list_title), onBack = actions.onBack) { gutter ->
        val songs = state.visibleSongs
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(bottom = 32.dp),
        ) {
            SongSearchField(state.query, actions.onQueryChange, actions.onClearQuery, large = true)
            SongLevelChips(state.filter, actions.onSelectFilter, Modifier.padding(top = 14.dp, bottom = 20.dp))
            if (songs.isEmpty() && state.songs.isNotEmpty()) {
                SongEmptyResult(actions.onResetFilters, verticalPadding = 96.dp)
            } else if (songs.isNotEmpty()) {
                Text(
                    stringResource(Res.string.song_count, songs.size),
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                )
                CardGrid(songs, columns = 2, itemKey = { it.id }) { song, modifier ->
                    SongCard(
                        song,
                        meta = stringResource(Res.string.tab_song_meta_bpm, jangdanLabel(song.jangdan), song.baseBpm),
                        onClick = { actions.onOpenSong(song.id) },
                        modifier = modifier,
                        large = true,
                        minHeight = 96.dp,
                    )
                }
            }
        }
    }
}

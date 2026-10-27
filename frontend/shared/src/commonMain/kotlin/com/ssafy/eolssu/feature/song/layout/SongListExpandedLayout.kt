package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.feature.song.SongDetailUiState
import com.ssafy.eolssu.feature.song.SongListActions
import com.ssafy.eolssu.feature.song.SongListUiState
import com.ssafy.eolssu.feature.song.component.SongCard
import com.ssafy.eolssu.feature.song.component.SongEmptyResult
import com.ssafy.eolssu.feature.song.component.SongInfoGrid
import com.ssafy.eolssu.feature.song.component.SongLevelChips
import com.ssafy.eolssu.feature.song.component.SongPreviewHeader
import com.ssafy.eolssu.feature.song.component.SongSearchField
import com.ssafy.eolssu.feature.song.component.SongTitleRow
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_count
import eolssu.shared.generated.resources.song_list_title
import eolssu.shared.generated.resources.song_preview_hint
import eolssu.shared.generated.resources.song_start_practice
import eolssu.shared.generated.resources.tab_song_pick_body
import eolssu.shared.generated.resources.tab_song_pick_title
import org.jetbrains.compose.resources.stringResource

/** 태블릿 가로 오른쪽 칸 미리보기(와이어프레임 `Tab-Songs-Land`): 각 60dp · 정간 38dp · 앞 9각. 칸 높이를 넘는 아래쪽은 자른다. */
private val PanePreviewMetrics = JeongganboMetrics(
    columnWidth = 60.dp,
    cellHeight = 38.dp,
    yulSize = 17.sp,
    auxSize = 9.sp,
    headHeight = 20.dp,
    columnGap = 6.dp,
)

private const val PanePreviewColumns = 9

/**
 * SONG-01 + SONG-02 태블릿 가로·넓은 웹 창 배치(와이어프레임 `Tab-Songs-Land`).
 * 왼쪽 목록에서 곡을 누르면 화면을 옮기지 않고 오른쪽 칸의 곡 정보를 바꾸고, 오른쪽 '이 곡 연습하기'로 연습 화면에 간다.
 * 고른 곡이 검색·필터로 목록에서 빠지면 오른쪽에 '곡을 골라주세요'를 보인다.
 */
@Composable
internal fun SongListExpandedLayout(state: SongListUiState, detail: SongDetailUiState?, actions: SongListActions) {
    SongTabletScaffold(expanded = true, title = stringResource(Res.string.song_list_title), onBack = actions.onBack) { gutter ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = gutter)
                .padding(top = 4.dp, bottom = 24.dp),
        ) {
            // 와이어프레임은 1120dp 너비에 목록 400dp. 좁은 가로 창에서는 오른쪽 정보 칸 4열이 들어가도록 목록을 줄인다.
            val listWidth = (maxWidth * 0.36f).coerceIn(320.dp, 400.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SongListPane(state, actions, Modifier.width(listWidth).fillMaxHeight())
                SongDetailPane(detail, actions.onStartPractice, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SongListPane(state: SongListUiState, actions: SongListActions, modifier: Modifier) {
    val songs = state.visibleSongs
    val selectedId = state.selectedSong?.id
    Column(modifier) {
        SongSearchField(state.query, actions.onQueryChange, actions.onClearQuery)
        SongLevelChips(state.filter, actions.onSelectFilter, Modifier.padding(top = 12.dp, bottom = 14.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (songs.isEmpty() && state.songs.isNotEmpty()) {
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
                    SongCard(
                        song,
                        jangdanLabel(song.jangdan),
                        onClick = { actions.onOpenSong(song.id) },
                        selected = song.id == selectedId,
                    )
                }
            }
        }
    }
}

/** 오른쪽 곡 정보 칸. [detail]이 null이면 곡을 고르라는 안내, 곡을 불러오는 중이면 빈 칸이다. */
@Composable
private fun SongDetailPane(detail: SongDetailUiState?, onStartPractice: () -> Unit, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (detail == null) {
            PickSongHint(Modifier.fillMaxSize())
            return@Column
        }
        val song = detail.song ?: return@Column
        SongTitleRow(song)
        SongInfoGrid(song, columns = 4)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SongPreviewHeader()
            JeongganboView(
                song = song,
                modifier = Modifier.weight(1f),
                metrics = PanePreviewMetrics,
                scrollState = null,
                maxColumns = PanePreviewColumns,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.song_preview_hint),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textMuted,
            )
            EolssuButton(
                stringResource(Res.string.song_start_practice),
                onStartPractice,
                modifier = Modifier.width(240.dp).height(52.dp),
            )
        }
    }
}

@Composable
private fun PickSongHint(modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(Res.string.tab_song_pick_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.tab_song_pick_body),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = EolssuTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

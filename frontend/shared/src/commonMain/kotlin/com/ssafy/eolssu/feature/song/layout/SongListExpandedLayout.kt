package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.song.SongDetailUiState
import com.ssafy.eolssu.feature.song.SongListActions
import com.ssafy.eolssu.feature.song.SongListUiState
import com.ssafy.eolssu.feature.song.component.SongDetailBody
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.component.songListItems
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_list_title
import eolssu.shared.generated.resources.tab_song_pick_body
import eolssu.shared.generated.resources.tab_song_pick_title
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-01 + SONG-02 태블릿 가로·넓은 웹 창 배치. 새 디자인 보드에는 태블릿 판이 없어,
 * 왼쪽에 휴대폰 `Song-List`의 본문, 오른쪽 칸에 `Song-Detail`의 본문(표지 6각 · 5정간 · 정보 4열)을 둔다.
 * 왼쪽 목록에서 곡을 누르면 화면을 옮기지 않고 오른쪽 칸의 곡 정보를 바꾸고, 오른쪽 '이 곡 연습하기 →'로 연습 화면에 간다.
 * 고른 곡이 검색·필터로 목록에서 빠지면 오른쪽에 '곡을 골라주세요'를 보인다.
 */
@Composable
internal fun SongListExpandedLayout(
    state: SongListUiState,
    detail: SongDetailUiState?,
    actions: SongListActions,
    snackbarHostState: SnackbarHostState,
    instrumentPicker: @Composable () -> Unit,
) {
    SongTabletScaffold(
        expanded = true,
        title = stringResource(Res.string.song_list_title),
        onBack = actions.onBack,
        pageTopBar = true,
        snackbarHostState = snackbarHostState,
        topBarRight = { instrumentPicker() },
    ) { gutter ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = gutter)) {
            // 1120dp 너비에서 목록 400dp. 좁은 가로 창에서는 오른쪽 정보 칸이 들어가도록 목록을 줄인다.
            val listWidth = (maxWidth * 0.36f).coerceIn(320.dp, 400.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LazyColumn(
                    Modifier.width(listWidth).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
                ) {
                    songListItems(state, actions, twoPane = true)
                }
                SongDetailPane(
                    detail,
                    actions.onStartPractice,
                    actions.onRetryDetail,
                    Modifier.weight(1f).fillMaxHeight().padding(top = 22.dp, bottom = 24.dp),
                )
            }
        }
    }
}

/** 오른쪽 곡 정보 칸. [detail]이 null이면 곡을 고르라는 안내, 곡을 불러오는 중·실패면 [SongLoadNotice]다. */
@Composable
private fun SongDetailPane(detail: SongDetailUiState?, onStartPractice: () -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    val colors = EolssuTheme.colors
    Column(modifier.border(1.dp, colors.lineSoft, RoundedCornerShape(20.dp))) {
        val song = detail?.song
        when {
            detail == null -> PickSongHint(Modifier.fillMaxSize().padding(24.dp))
            song == null -> SongLoadNotice(detail.loadState, onRetry)
            else -> SongDetailBody(
                song,
                onStartPractice,
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                coverColumns = 6,
                coverRows = 5,
                infoColumns = 4,
                large = true,
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

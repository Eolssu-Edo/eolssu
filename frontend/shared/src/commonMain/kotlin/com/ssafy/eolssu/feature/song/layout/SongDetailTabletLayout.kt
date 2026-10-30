package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.InfoHint
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.feature.song.SongDetailUiState
import com.ssafy.eolssu.feature.song.component.SongInfoGrid
import com.ssafy.eolssu.feature.song.component.SongPreviewHeader
import com.ssafy.eolssu.feature.song.component.SongTitleRow
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_detail_title
import eolssu.shared.generated.resources.song_preview_hint
import eolssu.shared.generated.resources.song_start_practice
import org.jetbrains.compose.resources.stringResource

/** 태블릿 세로 곡 정보 미리보기(와이어프레임 `Tab-SongDetail-Port`): 각 64dp · 정간 46dp · 앞 8각. */
private val PortraitPreviewMetrics = JeongganboMetrics(
    columnWidth = 64.dp,
    cellHeight = 46.dp,
    yulSize = 19.sp,
    auxSize = 9.5.sp,
    headHeight = 22.dp,
    columnGap = 8.dp,
)

private const val PortraitPreviewColumns = 8

/**
 * SONG-02 태블릿 배치(와이어프레임 `Tab-SongDetail-Port`). 제목 36sp, 정보 칸 4열, 테두리 칸 안의 앞 8각 미리보기, 하단 640dp 버튼.
 * 가로 창에서 이 화면에 바로 오면(배우기 단계의 곡 열기, 상세를 연 채 회전) 같은 배치를 720dp 너비로 가운데에 둔다.
 */
@Composable
internal fun SongDetailTabletLayout(
    state: SongDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartPractice: () -> Unit,
    onRetry: () -> Unit,
) {
    SongTabletScaffold(
        expanded = false,
        title = stringResource(Res.string.song_detail_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        bottomBar = {
            EolssuButton(
                stringResource(Res.string.song_start_practice),
                onStartPractice,
                modifier = Modifier.height(TabletButtonHeight),
                enabled = state.song != null,
            )
        },
    ) { gutter ->
        val song = state.song
        if (song == null) {
            SongLoadNotice(state.loadState, onRetry)
            return@SongTabletScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            SongTitleRow(song, titleSize = 36.sp, large = true)
            SongInfoGrid(song, Modifier.padding(top = 20.dp), columns = 4, large = true)
            SongPreviewHeader(Modifier.padding(top = 28.dp, bottom = 10.dp), large = true, sample = song.score.source == null)
            JeongganboView(
                song = song,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EolssuTheme.colors.line, RoundedCornerShape(16.dp))
                    .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 4.dp),
                metrics = PortraitPreviewMetrics,
                scrollState = null,
                maxColumns = PortraitPreviewColumns,
            )
            InfoHint(stringResource(Res.string.song_preview_hint), Modifier.padding(top = 16.dp))
        }
    }
}

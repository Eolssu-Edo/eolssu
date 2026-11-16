package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.song.SongDetailUiState
import com.ssafy.eolssu.feature.song.component.SongDetailBody
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_detail_title
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-02 태블릿 배치. 새 디자인 보드에는 태블릿 판이 없어 휴대폰 `Song-Detail`의 순서를 720dp 너비로 쓰고,
 * 표지는 앞 6각 · 8정간, 정보 격자는 4열 한 줄로 넓힌다.
 * 가로 창([expanded])에서는 곡 상세 경로가 곡 목록 두 칸 화면으로 바로 바뀌므로(S15P31E102-234) 이 배치는 바뀌기 전 잠깐만 보인다.
 * 그동안에도 1280×800 창에서 '연주 평가 시작'이 스크롤 없이 보이도록, 표지를 두 칸 화면 오른쪽 칸과 같은 5정간으로 줄인다(S15P31E102-976).
 */
@Composable
internal fun SongDetailTabletLayout(
    state: SongDetailUiState,
    expanded: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartPractice: () -> Unit,
    onRetry: () -> Unit,
) {
    SongTabletScaffold(
        expanded = false,
        title = stringResource(Res.string.song_detail_title),
        onBack = onBack,
        pageTopBar = true,
        snackbarHostState = snackbarHostState,
    ) { gutter ->
        val song = state.song
        if (song == null) {
            SongLoadNotice(state.loadState, onRetry)
            return@SongTabletScaffold
        }
        SongDetailBody(
            song,
            onStartPractice,
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(top = 22.dp, bottom = 32.dp),
            coverColumns = 6,
            coverRows = if (expanded) ExpandedCoverRows else 8,
            infoColumns = 4,
            large = true,
        )
    }
}

/** 가로 창의 표지 정간 수. 두 칸 화면 오른쪽 칸([SongListExpandedLayout])과 같다. */
private const val ExpandedCoverRows = 5

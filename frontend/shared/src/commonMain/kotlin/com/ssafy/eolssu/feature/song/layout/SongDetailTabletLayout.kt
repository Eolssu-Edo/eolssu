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
 * 가로 창에서 이 화면에 바로 오면(배우기 단계의 곡 열기, 상세를 연 채 회전) 같은 배치를 720dp 너비로 가운데에 둔다.
 */
@Composable
internal fun SongDetailTabletLayout(
    state: SongDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartPractice: () -> Unit,
    onOpenSettings: () -> Unit,
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
            onOpenSettings,
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(top = 22.dp, bottom = 32.dp),
            coverColumns = 6,
            coverRows = 8,
            infoColumns = 4,
            large = true,
        )
    }
}

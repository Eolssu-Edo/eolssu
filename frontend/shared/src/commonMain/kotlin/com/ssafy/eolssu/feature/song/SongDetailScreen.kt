package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.song.component.SongDetailBody
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.layout.SongDetailTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_detail_title
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-02 곡 정보(보드 `Song-Detail`): 악보 표지(앞 2각 미니 정간보 · 세로 제목 · '얼쑤' 도장), 제목과 난이도, 곡 소개,
 * 정보 격자, 본문 안의 '연주 평가 시작'.
 * 태블릿([WindowWidthClass.Medium] · [WindowWidthClass.Expanded])은 같은 순서를 720dp 너비로 보인다.
 * 태블릿 가로에서는 곡 상세 경로가 곡 목록 두 칸 화면으로 바로 바뀌어(S15P31E102-234) 목록 오른쪽 칸에 같은 정보를 보이고, 바뀌기 전 잠깐은 표지만 줄인 같은 배치를 쓴다.
 */
@Composable
fun SongDetailScreen(
    state: SongDetailUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartPractice: () -> Unit,
    onRetry: () -> Unit,
) {
    if (widthClass != WindowWidthClass.Compact) {
        SongDetailTabletLayout(state, widthClass == WindowWidthClass.Expanded, snackbarHostState, onBack, onStartPractice, onRetry)
        return
    }
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { EolssuPageTopBar(stringResource(Res.string.song_detail_title), onBack = onBack) },
    ) {
        val song = state.song
        if (song == null) {
            SongLoadNotice(state.loadState, onRetry)
            return@EolssuScaffold
        }
        SongDetailBody(
            song,
            onStartPractice,
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
        )
    }
}

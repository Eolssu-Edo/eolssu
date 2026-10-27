package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.InfoHint
import com.ssafy.eolssu.core.ui.component.fadeOutBottom
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.feature.song.component.SongInfoGrid
import com.ssafy.eolssu.feature.song.component.SongPreviewHeader
import com.ssafy.eolssu.feature.song.component.SongTitleRow
import com.ssafy.eolssu.feature.song.layout.SongDetailTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_detail_title
import eolssu.shared.generated.resources.song_preview_hint
import eolssu.shared.generated.resources.song_start_practice
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-02 곡 정보와 정간보 앞 3각 미리보기.
 * 태블릿([WindowWidthClass.Medium] · [WindowWidthClass.Expanded])은 정보 칸 4열과 앞 8각 미리보기를 720dp 너비로 보인다.
 * 태블릿 가로의 곡 목록에서는 이 화면 대신 목록 오른쪽 칸에 같은 정보를 보이므로, 가로로 이 화면에 오는 경우(배우기 단계의 곡 열기 등)도 세로 배치를 쓴다.
 */
@Composable
fun SongDetailScreen(
    state: SongDetailUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartPractice: () -> Unit,
) {
    if (widthClass != WindowWidthClass.Compact) {
        SongDetailTabletLayout(state, snackbarHostState, onBack, onStartPractice)
        return
    }
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { EolssuTopBar(stringResource(Res.string.song_detail_title), onBack = onBack) },
        bottomBar = { EolssuButton(stringResource(Res.string.song_start_practice), onStartPractice, enabled = state.song != null) },
    ) {
        val song = state.song ?: return@EolssuScaffold
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            SongTitleRow(song)
            SongInfoGrid(song, Modifier.padding(top = 18.dp))
            SongPreviewHeader(Modifier.padding(top = 20.dp, bottom = 8.dp))
            JeongganboView(
                song = song,
                modifier = Modifier.heightIn(max = 250.dp).fadeOutBottom(fadeStart = 0.78f),
                scrollState = null,
                maxColumns = 3,
            )
            InfoHint(stringResource(Res.string.song_preview_hint), Modifier.padding(top = 14.dp))
        }
    }
}

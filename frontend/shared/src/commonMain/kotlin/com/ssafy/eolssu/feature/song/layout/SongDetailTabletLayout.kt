package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.song.SongDetailUiState
import com.ssafy.eolssu.feature.song.component.LargeCoverCellHeight
import com.ssafy.eolssu.feature.song.component.SongDetailBody
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_detail_title
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-02 태블릿 배치. 새 디자인 보드에는 태블릿 판이 없어 휴대폰 `Song-Detail`의 순서를 720dp 너비로 쓰고,
 * 표지는 앞 6각, 정보 격자는 4열 한 줄로 넓힌다.
 * 태블릿 세로는 표지를 8정간에서 시작해, 본문 아래가 비지 않도록 남는 높이만큼 정간을 늘리고(최대 [PortraitMaxCoverRows]) 그래도 남으면 본문을 세로 가운데에 둔다(S15P31E102-1031).
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 그린 본문 높이(px)와 그때의 표지 정간 수. 정간 몫을 뺀 나머지 높이로 화면에 들어가는 정간 수를 다시 센다.
            var measured by remember(song.id) { mutableStateOf<Pair<Int, Int>?>(null) }
            val density = LocalDensity.current
            val last = measured
            val rows = when {
                expanded -> ExpandedCoverRows
                last == null || !constraints.hasBoundedHeight -> PortraitMinCoverRows
                else -> with(density) {
                    val cell = LargeCoverCellHeight.roundToPx()
                    val (height, usedRows) = last
                    ((constraints.maxHeight - (height - cell * usedRows)) / cell).coerceIn(PortraitMinCoverRows, PortraitMaxCoverRows)
                }
            }
            // 각이 짧아 정간을 다 늘려도 높이가 남으면 본문을 세로 가운데에 둔다.
            Box(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .then(if (expanded) Modifier else Modifier.heightIn(min = maxHeight)),
                contentAlignment = Alignment.Center,
            ) {
                SongDetailBody(
                    song,
                    onStartPractice,
                    Modifier
                        .onSizeChanged { if (!expanded) measured = it.height to rows }
                        .padding(horizontal = gutter)
                        .padding(top = 22.dp, bottom = 32.dp),
                    coverColumns = 6,
                    coverRows = rows,
                    infoColumns = 4,
                    large = true,
                )
            }
        }
    }
}

/** 가로 창의 표지 정간 수. 두 칸 화면 오른쪽 칸([SongListExpandedLayout])과 같다. */
private const val ExpandedCoverRows = 5

/** 태블릿 세로 표지의 기본 정간 수. 남는 높이가 없으면 이대로 두고 스크롤한다. */
private const val PortraitMinCoverRows = 8

/** 태블릿 세로 표지를 늘릴 수 있는 최대 정간 수. 한 각(대개 12~16정간)을 넘겨 늘리지 않는다. */
private const val PortraitMaxCoverRows = 16

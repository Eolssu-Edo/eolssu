package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.song.MySongsActions
import com.ssafy.eolssu.feature.song.MySongsBody
import com.ssafy.eolssu.feature.song.MySongsUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_title
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-03 태블릿 배치. 새 디자인 보드에는 태블릿 판이 없어 휴대폰 보드(`Song-Practice`)의 순서·간격을 그대로 쓴다.
 * 세로는 720dp 내용 너비에 한 열, 가로·넓은 웹 창([expanded])은 1120dp 내용 너비에 줄을 두 열(간격 12dp)로 놓는다.
 */
@Composable
internal fun MySongsTabletLayout(
    state: MySongsUiState,
    expanded: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: MySongsActions,
    instrumentPicker: @Composable () -> Unit,
) {
    SongTabletScaffold(
        expanded = expanded,
        title = stringResource(Res.string.mysongs_title),
        onBack = actions.onBack,
        snackbarHostState = snackbarHostState,
        topBarRight = { instrumentPicker() },
    ) { gutter ->
        MySongsBody(
            state,
            actions,
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = gutter, end = gutter, bottom = 32.dp),
            columns = if (expanded) 2 else 1,
            rowGap = if (expanded) 12.dp else 8.dp,
        )
    }
}

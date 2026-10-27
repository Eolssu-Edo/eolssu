package com.ssafy.eolssu.feature.result.layout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.result.MetricGrid
import com.ssafy.eolssu.feature.result.ResultActions
import com.ssafy.eolssu.feature.result.ResultTopBar
import com.ssafy.eolssu.feature.result.ResultUiState
import com.ssafy.eolssu.feature.result.SmallOutlineButton
import com.ssafy.eolssu.feature.result.TotalScore
import com.ssafy.eolssu.feature.result.component.ErrorFilterChips
import com.ssafy.eolssu.feature.result.component.ErrorScore
import com.ssafy.eolssu.feature.result.component.ErrorSummary
import com.ssafy.eolssu.feature.result.component.SelectedCellCard
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_home
import eolssu.shared.generated.resources.result_other_song
import eolssu.shared.generated.resources.result_practice_again
import eolssu.shared.generated.resources.result_save
import eolssu.shared.generated.resources.result_score_hint
import org.jetbrains.compose.resources.stringResource

/**
 * 태블릿 가로·넓은 웹 창 배치(와이어프레임 `Tab-Result-Land`). 왼쪽 380dp에 점수·오류 요약·고른 칸 설명·다음 행동,
 * 오른쪽에 오류를 표시한 정간보(한 줄, 가로 스크롤)를 둔다. 칸을 누르면 왼쪽 설명이 바뀐다.
 * 내용은 최대 [WindowBreakpoints.ContentMaxWidth]로 가운데에 두고, 양옆은 40dp 이상 띄운다.
 */
@Composable
internal fun ResultExpandedLayout(state: ResultUiState, snackbarHostState: SnackbarHostState, actions: ResultActions) {
    val result = state.result
    val song = state.song
    val colors = EolssuTheme.colors
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = WindowBreakpoints.ContentMaxWidth + SideGutter * 2,
        topBar = {
            // 상단 바 안쪽 여백(20dp)과 합쳐 내용 양옆이 40dp 이상, 내용 너비는 최대 1120dp가 되게 한다.
            ResultTopBar(song, actions, Modifier.padding(horizontal = SideGutter / 2), maxWidth = WindowBreakpoints.ContentMaxWidth + SideGutter)
        },
    ) {
        if (result == null || song == null) return@EolssuScaffold
        Row(
            Modifier.fillMaxSize().padding(start = SideGutter, end = SideGutter, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.width(SideWidth).fillMaxHeight()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TotalScore(result, alignStart = true, scoreSize = 56.sp)
                    MetricGrid(result, Modifier.fillMaxWidth(), large = true)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ErrorSummary(result)
                        ErrorFilterChips(result, state.errorFilter, actions.onSelectFilter)
                    }
                    state.shownCell?.let { cell ->
                        SelectedCellCard(
                            song, result, cell,
                            Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                            markFallback = colors.surface,
                        )
                    }
                }
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallOutlineButton(
                        stringResource(Res.string.result_save), actions.onSave, Modifier.fillMaxWidth(),
                        filled = true, icon = EolssuIcons.Bookmark,
                    )
                    EolssuButton(stringResource(Res.string.result_practice_again), actions.onPracticeAgain, icon = EolssuIcons.Restart)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallOutlineButton(stringResource(Res.string.result_other_song), actions.onOtherSong, Modifier.weight(1f))
                        SmallOutlineButton(stringResource(Res.string.result_home), actions.onHome, Modifier.weight(1f))
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(Res.string.result_score_hint),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
                ErrorScore(
                    song, result, state.errorFilter, state.shownCell, actions.onSelectCell, wrap = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

private val SideGutter = 40.dp

/** 왼쪽 점수·요약 칸 너비. */
private val SideWidth = 380.dp

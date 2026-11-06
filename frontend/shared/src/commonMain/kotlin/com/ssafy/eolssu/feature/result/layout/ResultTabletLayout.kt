package com.ssafy.eolssu.feature.result.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.ssafy.eolssu.feature.result.component.ErrorLocationCard
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_home
import eolssu.shared.generated.resources.result_other_song
import eolssu.shared.generated.resources.result_practice_again
import eolssu.shared.generated.resources.result_save_short
import org.jetbrains.compose.resources.stringResource

/**
 * 태블릿 세로·가로 배치(와이어프레임 `Tab-Result-Port` 바탕). 위에 점수와 세부 점수, 그 아래에 오류 위치 요약 카드(틀린 정간 수 · ‘오류 위치 상세 보기’)를 둔다.
 * 점수 중심 화면이라 오류 정간보는 상세 화면(RESULT-03)에서 본다. 내용은 가운데 720dp 너비이고, 다음 행동 버튼은 아래 막대에 한 줄로 둔다.
 */
@Composable
internal fun ResultTabletLayout(state: ResultUiState, snackbarHostState: SnackbarHostState, actions: ResultActions) {
    val result = state.result
    val song = state.song
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = WindowBreakpoints.ContentMaxWidth,
        topBar = { ResultTopBar(song, actions, Modifier.padding(horizontal = 12.dp), maxWidth = WindowBreakpoints.ContentMaxWidth) },
        bottomBar = {
            Row(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = ContentWidth).fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EolssuButton(
                    stringResource(Res.string.result_practice_again), actions.onPracticeAgain, Modifier.weight(1.4f),
                    icon = EolssuIcons.Restart, compact = true,
                )
                SmallOutlineButton(stringResource(Res.string.result_other_song), actions.onOtherSong, Modifier.weight(1f), height = 52.dp)
                SmallOutlineButton(
                    stringResource(Res.string.result_save_short), actions.onSave, Modifier.weight(1f), height = 52.dp,
                    filled = true, icon = EolssuIcons.Bookmark,
                )
                SmallOutlineButton(stringResource(Res.string.result_home), actions.onHome, Modifier.weight(1f), height = 52.dp)
            }
        },
    ) {
        if (result == null || song == null) return@EolssuScaffold
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 40.dp, top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TotalScore(result, Modifier.width(260.dp), alignStart = true)
                    MetricGrid(result, Modifier.weight(1f), large = true)
                }
                ErrorLocationCard(result, song, actions.onViewAllErrors)
            }
        }
    }
}

/** 태블릿 내용 너비. */
private val ContentWidth = 720.dp

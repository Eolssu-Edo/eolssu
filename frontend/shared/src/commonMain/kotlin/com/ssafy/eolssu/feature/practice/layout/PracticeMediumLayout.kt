package com.ssafy.eolssu.feature.practice.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.practice.PracticeScreenActions
import com.ssafy.eolssu.feature.practice.component.PracticeTopBar
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.component.PracticeBottomContent
import com.ssafy.eolssu.feature.practice.component.PracticeScoreArea
import com.ssafy.eolssu.feature.practice.component.PracticeScoreStyle

/**
 * 태블릿 세로 배치(와이어프레임 `Tab-Practice-Port`). 악보는 각 72dp로 크게, 들어가면 위·아래 두 단으로 놓는다.
 * 상태별 버튼은 휴대폰처럼 아래에 고정하되 가운데 640dp 너비로 모은다. 보조 패널은 640dp 너비 하단 시트로 연다.
 */
@Composable
internal fun PracticeMediumLayout(state: PracticeUiState, snackbarHostState: SnackbarHostState, actions: PracticeScreenActions) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = WindowBreakpoints.ContentMaxWidth,
        topBar = {
            PracticeTopBar(state, actions.onBack, actions.onOpenSettings, Modifier.padding(horizontal = 12.dp), maxWidth = WindowBreakpoints.ContentMaxWidth)
        },
        bottomBar = {
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = BottomContentMaxWidth).fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PracticeBottomContent(state, actions.bottom)
            }
        },
    ) {
        if (state.song != null) {
            PracticeScoreArea(state, PracticeScoreStyle.TabletPortrait, actions.onSelectEvent, actions.onUserScroll, actions.onFollowNow)
        }
    }
}

/** 태블릿 세로 하단 상태 영역의 내용 너비. 와이어프레임 `barStyle`. */
private val BottomContentMaxWidth = 640.dp

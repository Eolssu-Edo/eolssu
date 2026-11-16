package com.ssafy.eolssu.feature.practice.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.feature.practice.LivePitchCell
import com.ssafy.eolssu.feature.practice.PracticeScreenActions
import com.ssafy.eolssu.feature.practice.component.PracticeTopBar
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.component.PracticeFingeringPage
import com.ssafy.eolssu.feature.practice.component.PracticeScoreArea
import com.ssafy.eolssu.feature.practice.component.PracticeScoreStyle
import com.ssafy.eolssu.feature.practice.component.PracticeSideDock

/**
 * 태블릿 가로·넓은 웹 창 배치(와이어프레임 `Tab-Practice-Land`). 왼쪽에 악보(한 단, 가로 스크롤), 오른쪽 380dp에 상태 패널을 둔다.
 * 상단 설정 버튼 대신 상태 패널에 설정 바로가기가 있다. 보조 패널은 다른 크기와 같이 배경을 눌러 닫는 시트로 열린다(S15P31E102-518).
 * 내용은 최대 [WindowBreakpoints.ContentMaxWidth]로 가운데에 두고, 양옆은 40dp 이상 띄운다.
 */
@Composable
internal fun PracticeExpandedLayout(
    state: PracticeUiState,
    snackbarHostState: SnackbarHostState,
    actions: PracticeScreenActions,
    livePitch: Map<ScoreUnitKey, LivePitchCell> = emptyMap(),
) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.groupedBackground,
        contentMaxWidth = WindowBreakpoints.ContentMaxWidth + SideGutter * 2,
        topBar = {
            // 상단 바 안쪽 여백(20dp)과 합쳐 내용 양옆이 40dp 이상, 내용 너비는 최대 1120dp가 되게 한다.
            PracticeTopBar(
                state, actions.onBack, actions.onOpenSettings, Modifier.padding(horizontal = SideGutter / 2),
                maxWidth = WindowBreakpoints.ContentMaxWidth + SideGutter, showSettings = false, onToggleStep = actions.bottom.onToggleStep,
            )
        },
    ) {
        if (state.song == null) return@EolssuScaffold
        Row(
            Modifier.fillMaxSize().padding(start = SideGutter, end = SideGutter, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // '운지법과 함께 보기'는 휴대폰과 같은 운지 페이지를 악보 자리에 둔다(S15P31E102-1011).
            if (state.fingeringView) {
                PracticeFingeringPage(state, actions.onSelectUnit, Modifier.weight(1f).fillMaxHeight(), livePitch = livePitch)
            } else {
                PracticeScoreArea(
                    state, PracticeScoreStyle.TabletLandscape, actions.onSelectUnit, actions.onUserScroll, actions.onFollowNow,
                    Modifier.weight(1f), livePitch = livePitch,
                )
            }
            PracticeSideDock(state, actions.panel, actions.bottom, Modifier.width(DockWidth).fillMaxHeight())
        }
    }
}

private val SideGutter = 40.dp

/** 오른쪽 상태 패널 너비. 와이어프레임 `dockStyle`. */
private val DockWidth = 380.dp

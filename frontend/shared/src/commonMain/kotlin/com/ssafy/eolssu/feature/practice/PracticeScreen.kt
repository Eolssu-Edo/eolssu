package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.practice.component.IdleReminderDialog
import com.ssafy.eolssu.feature.practice.component.PracticeBoardScore
import com.ssafy.eolssu.feature.practice.component.PracticeBottomActions
import com.ssafy.eolssu.feature.practice.component.PracticeCallToAction
import com.ssafy.eolssu.core.ui.component.BeatCountdownOverlay
import com.ssafy.eolssu.feature.practice.component.PracticeDialog
import com.ssafy.eolssu.feature.practice.component.PracticeDialogActions
import com.ssafy.eolssu.feature.practice.component.PracticeFingeringPage
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelSheet
import com.ssafy.eolssu.feature.practice.component.PracticePlayerCard
import com.ssafy.eolssu.feature.practice.component.PracticeRecordingBar
import com.ssafy.eolssu.feature.practice.component.PracticeStepCountdownOverlay
import com.ssafy.eolssu.feature.practice.component.PracticeStepPanels
import com.ssafy.eolssu.feature.practice.component.PracticeTitleRow
import com.ssafy.eolssu.feature.practice.component.PracticeTopBar
import com.ssafy.eolssu.feature.practice.component.reportUserActivity
import com.ssafy.eolssu.feature.practice.layout.PracticeExpandedLayout
import com.ssafy.eolssu.feature.practice.layout.PracticeMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_countdown_soon
import eolssu.shared.generated.resources.practice_score_failed
import eolssu.shared.generated.resources.practice_score_loading
import eolssu.shared.generated.resources.practice_score_missing
import eolssu.shared.generated.resources.practice_score_retry
import com.ssafy.eolssu.feature.practice.component.practicePageTitle
import org.jetbrains.compose.resources.stringResource

/** PRACTICE-01 화면에서 부르는 동작. */
class PracticeScreenActions(
    val onBack: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSelectUnit: (ScoreDisplayUnit) -> Unit,
    val onUserScroll: () -> Unit,
    val onFollowNow: () -> Unit,
    val onCancelCountdown: () -> Unit,
    val onUserActivity: () -> Unit,
    val onKeepPracticing: () -> Unit,
    val bottom: PracticeBottomActions,
    val panel: PracticePanelActions,
    val dialog: PracticeDialogActions,
    val onRetrySong: () -> Unit = {},
)

/**
 * PRACTICE-01 연습(연두 보드). 같은 상태를 창 너비에 따라 휴대폰([WindowWidthClass.Compact]), 태블릿 세로([WindowWidthClass.Medium]),
 * 태블릿 가로·넓은 웹 창([WindowWidthClass.Expanded]) 배치로 보여준다. 확인 창은 배치와 관계없이 같다.
 * 평가 시작 카운트다운은 배치와 관계없이 정간보 화면 위에 겹쳐 띄운다([CountdownOverlay]).
 * 화면과 보조 패널 시트의 입력은 연습 시간의 활동으로 알린다.
 * [livePitch]는 평가 연주에서 음마다 모은 실시간 음높이 안내([livePitchMarks])로, 정간보 악보의 그 음 자리에 칠한다.
 */
@Composable
fun PracticeScreen(
    state: PracticeUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: PracticeScreenActions,
    livePitch: Map<ScoreUnitKey, LivePitchMark> = emptyMap(),
) {
    if (state.song == null) {
        PracticeSongLoadScreen(state, actions, snackbarHostState)
        return
    }
    Box(Modifier.fillMaxSize().reportUserActivity(actions.onUserActivity)) {
        when (widthClass) {
            WindowWidthClass.Compact -> PracticeCompactLayout(state, snackbarHostState, actions, livePitch)
            WindowWidthClass.Medium -> PracticeMediumLayout(state, snackbarHostState, actions, livePitch)
            WindowWidthClass.Expanded -> PracticeExpandedLayout(state, snackbarHostState, actions, livePitch)
        }
        // 평가 시작 3·2·1은 리듬 게임처럼 정간보 화면 위에 겹쳐 띄운다(배치와 관계없이, 아래 악보가 비쳐 보인다).
        if (state.mode == PracticeMode.Countdown) CountdownOverlay(state, actions.onCancelCountdown)
        // 한 음씩 다시 시작의 3·2·1은 배치와 관계없이 화면 전체를 옅게 덮는다(누름은 막지 않는다).
        PracticeStepCountdownOverlay(state)
    }
    // 모든 화면 크기에서 배경을 눌러 닫을 수 있는 설정 시트를 사용한다.
    if (state.panel != null) {
        PracticePanelSheet(
            state,
            actions.panel,
            tablet = widthClass != WindowWidthClass.Compact,
            modifier = Modifier.reportUserActivity(actions.onUserActivity),
        )
    }
    state.popup?.takeUnless { it == PracticePopup.SettingsPaused && state.panel != null }
        ?.let { PracticeDialog(it, actions.dialog, forStep = state.solo) }
    if (state.idleReminder) IdleReminderDialog(actions.onKeepPracticing)
}

/** 곡 상세를 거치지 않고 연습으로 들어와도 로딩·404·통신 실패에서 빠져나오고 재시도할 수 있다. */
@Composable
private fun PracticeSongLoadScreen(state: PracticeUiState, actions: PracticeScreenActions, snackbarHostState: SnackbarHostState) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { EolssuPageTopBar(title = practicePageTitle(state), onBack = actions.onBack) },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            if (state.isSongLoading) CircularProgressIndicator(color = EolssuTheme.colors.accentText)
            Text(stringResource(when (state.songError) {
                PracticeSongError.NotFound -> Res.string.practice_score_missing
                PracticeSongError.Failed -> Res.string.practice_score_failed
                null -> Res.string.practice_score_loading
            }), style = MaterialTheme.typography.bodyLarge)
            if (!state.isSongLoading && state.songError != null) {
                EolssuButton(stringResource(Res.string.practice_score_retry), actions.onRetrySong)
            }
        }
    }
}

/**
 * 휴대폰 배치(보드 PRACTICE-01): 페이지 머리 ‘연습’ → 본문(곡 제목·오른쪽 상태 문구, 악보 또는 운지법과 함께 보기, 플레이어 카드,
 * 행동 버튼·안내)을 세로 스크롤 없이 한 화면에 두고, 연주 중에만 하단 바가 고정된다.
 * 연주 중에는 손으로 스크롤하기 어려우므로 악보는 플레이어·버튼을 뺀 남은 높이를 쓴다. 운지법을 끈 악보는 좌우로만 넘기고,
 * 운지법과 함께 보기는 남은 높이에 들어가는 운지만 한 페이지에 담아 넘긴다.
 */
@Composable
private fun PracticeCompactLayout(
    state: PracticeUiState,
    snackbarHostState: SnackbarHostState,
    actions: PracticeScreenActions,
    livePitch: Map<ScoreUnitKey, LivePitchMark>,
) {
    val recordingBar = state.mode == PracticeMode.Recording || state.mode == PracticeMode.Interrupted || state.resuming
    val showPlayer = state.mode in setOf(PracticeMode.Ready, PracticeMode.Model, PracticeMode.Rhythm)
    val fingering = state.fingeringView
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { PracticeTopBar(state, actions.onBack, actions.onOpenSettings, onToggleStep = actions.bottom.onToggleStep) },
        bottomBar = if (recordingBar) { { PracticeRecordingBar(state, actions.bottom) } } else null,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = if (fingering || recordingBar) 8.dp else 18.dp, bottom = 12.dp),
            ) {
                PracticeTitleRow(state, statusAtEnd = true)
                Spacer(Modifier.height(if (fingering || recordingBar) 8.dp else 12.dp))
                if (fingering) {
                    PracticeFingeringPage(state, actions.onSelectUnit, modifier = Modifier.weight(1f))
                } else {
                    PracticeBoardScore(
                        state, actions.onSelectUnit, actions.onUserScroll, actions.onFollowNow,
                        modifier = Modifier.weight(1f), livePitch = livePitch,
                    )
                }
                if (showPlayer) {
                    if (state.stepUi) {
                        PracticeStepPanels(state, actions.bottom, Modifier.padding(top = 8.dp, bottom = 14.dp))
                    } else {
                        PracticePlayerCard(state, actions.bottom, Modifier.padding(top = 8.dp, bottom = 8.dp), compact = true)
                    }
                    PracticeCallToAction(state, actions.bottom, hideNotice = fingering)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 평가 시작 카운트다운(결합본 3·2·1 원형): 정간보 화면 전체를 옅은 막(74%)으로 덮어 악보가 비쳐 보이게 하고,
 * 가운데 3·2·1 원과 ‘숨을 고르고 준비해 주세요.’, 그 아래 ‘시작 취소’를 둔다. 구간 반복의 다시 시작 3·2·1과 같은 모양이다.
 */
@Composable
private fun CountdownOverlay(state: PracticeUiState, onCancel: () -> Unit) {
    BeatCountdownOverlay(
        count = state.count,
        message = stringResource(Res.string.practice_countdown_soon),
        action = {
            EolssuButton(
                stringResource(Res.string.practice_countdown_cancel),
                onCancel,
                style = EolssuButtonStyle.Secondary,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        },
    )
}

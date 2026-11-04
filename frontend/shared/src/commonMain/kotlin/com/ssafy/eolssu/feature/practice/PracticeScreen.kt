package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreEvent
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
import com.ssafy.eolssu.feature.practice.component.PracticeCountdown
import com.ssafy.eolssu.feature.practice.component.PracticeDialog
import com.ssafy.eolssu.feature.practice.component.PracticeDialogActions
import com.ssafy.eolssu.feature.practice.component.PracticeFingeringPage
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelSheet
import com.ssafy.eolssu.feature.practice.component.PracticePlayerCard
import com.ssafy.eolssu.feature.practice.component.PracticeRecordingBar
import com.ssafy.eolssu.feature.practice.component.PracticeStatusLabel
import com.ssafy.eolssu.feature.practice.component.PracticeTitleRow
import com.ssafy.eolssu.feature.practice.component.PracticeTopBar
import com.ssafy.eolssu.feature.practice.component.reportUserActivity
import com.ssafy.eolssu.feature.practice.component.supportsPracticeColumnPacking
import com.ssafy.eolssu.feature.practice.layout.PracticeExpandedLayout
import com.ssafy.eolssu.feature.practice.layout.PracticeMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_countdown_soon
import eolssu.shared.generated.resources.practice_score_failed
import eolssu.shared.generated.resources.practice_score_loading
import eolssu.shared.generated.resources.practice_score_missing
import eolssu.shared.generated.resources.practice_score_retry
import eolssu.shared.generated.resources.practice_title
import org.jetbrains.compose.resources.stringResource

/** PRACTICE-01 화면에서 부르는 동작. */
class PracticeScreenActions(
    val onBack: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSelectEvent: (ScoreEvent) -> Unit,
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
 * 휴대폰은 보드대로 카운트다운을 본문 안에 두고, 태블릿은 보드가 없어 덮는 카운트다운을 유지한다.
 * 화면과 보조 패널 시트의 입력은 연습 시간의 활동으로 알린다.
 */
@Composable
fun PracticeScreen(
    state: PracticeUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: PracticeScreenActions,
) {
    if (state.song == null) {
        PracticeSongLoadScreen(state, actions, snackbarHostState)
        return
    }
    Box(Modifier.fillMaxSize().reportUserActivity(actions.onUserActivity)) {
        when (widthClass) {
            WindowWidthClass.Compact -> PracticeCompactLayout(state, snackbarHostState, actions)
            WindowWidthClass.Medium -> PracticeMediumLayout(state, snackbarHostState, actions)
            WindowWidthClass.Expanded -> PracticeExpandedLayout(state, snackbarHostState, actions)
        }
        if (state.mode == PracticeMode.Countdown && widthClass != WindowWidthClass.Compact) CountdownOverlay(state, actions.onCancelCountdown)
    }
    // 태블릿 가로는 보조 패널을 오른쪽 상태 패널 자리에 연다(PracticeExpandedLayout).
    if (state.panel != null && widthClass != WindowWidthClass.Expanded) {
        PracticePanelSheet(
            state,
            actions.panel,
            tablet = widthClass == WindowWidthClass.Medium,
            modifier = Modifier.reportUserActivity(actions.onUserActivity),
        )
    }
    state.popup?.let { PracticeDialog(it, actions.dialog) }
    if (state.idleReminder) IdleReminderDialog(actions.onKeepPracticing)
}

/** 곡 상세를 거치지 않고 연습으로 들어와도 로딩·404·통신 실패에서 빠져나오고 재시도할 수 있다. */
@Composable
private fun PracticeSongLoadScreen(state: PracticeUiState, actions: PracticeScreenActions, snackbarHostState: SnackbarHostState) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { EolssuPageTopBar(title = stringResource(Res.string.practice_title), onBack = actions.onBack) },
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
 * 휴대폰 배치(보드 PRACTICE-01): 페이지 머리 ‘연습’ → 본문(곡 제목·pill, 상태 문구, 악보 또는 운지법과 함께 보기, 플레이어 카드,
 * 행동 버튼·안내, 카운트다운)이 세로로 스크롤되고, 연주 중에만 하단 바가 고정된다.
 */
@Composable
private fun PracticeCompactLayout(state: PracticeUiState, snackbarHostState: SnackbarHostState, actions: PracticeScreenActions) {
    val recordingBar = state.mode == PracticeMode.Recording || state.mode == PracticeMode.Interrupted || state.resuming
    val showPlayer = state.mode == PracticeMode.Ready || state.mode == PracticeMode.Model
    val fingering = state.fingeringView
    val packScore = remember(state.song, fingering) {
        !fingering && state.song?.let { it.score.supportsPracticeColumnPacking(it.jangdan) } == true
    }
    val fontScale = LocalDensity.current.fontScale
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { PracticeTopBar(state, actions.onBack, actions.onOpenSettings) },
        bottomBar = if (recordingBar) { { PracticeRecordingBar(state, actions.bottom) } } else null,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 낮은 창·확대 글꼴에서는 고정 컨트롤 때문에 악보가 사라지지 않도록 페이지 스크롤을 유지한다.
            val fitScore = packScore && maxHeight >= 560.dp && fontScale <= 1f
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (fitScore) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .padding(start = 20.dp, end = 20.dp, top = if (fingering) 8.dp else 18.dp, bottom = 12.dp),
            ) {
                PracticeTitleRow(state)
                Spacer(Modifier.height(if (fingering) 7.dp else 10.dp))
                PracticeStatusLabel(state)
                Spacer(Modifier.height(if (fingering) 8.dp else 14.dp))
                if (fingering) {
                    PracticeFingeringPage(state, actions.onSelectEvent)
                } else {
                    PracticeBoardScore(
                        state, actions.onSelectEvent, actions.onUserScroll, actions.onFollowNow,
                        modifier = if (fitScore) Modifier.weight(1f) else Modifier,
                    )
                }
                if (showPlayer) {
                    PracticePlayerCard(state, actions.bottom, Modifier.padding(top = if (fingering) 10.dp else 18.dp, bottom = if (fingering) 8.dp else 18.dp), compact = fingering)
                    PracticeCallToAction(state, actions.bottom, hideNotice = fingering)
                }
                if (state.mode == PracticeMode.Countdown) {
                    PracticeCountdown(state, actions.onCancelCountdown, Modifier.padding(top = 18.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 태블릿의 카운트다운: 화면을 흰색으로 덮고 큰 숫자를 가운데 둔다(휴대폰 보드의 본문 카운트다운과 같은 문구). */
@Composable
private fun CountdownOverlay(state: PracticeUiState, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(Res.string.practice_countdown_soon),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = EolssuTheme.colors.textSub,
        )
        Text("${state.count}", style = EolssuTheme.serif.brand.copy(fontSize = 140.sp, lineHeight = 140.sp, fontWeight = FontWeight.Normal))
        EolssuButton(
            stringResource(Res.string.practice_countdown_cancel),
            onCancel,
            style = EolssuButtonStyle.Secondary,
            modifier = Modifier.padding(horizontal = 0.dp),
            small = false,
        )
    }
}

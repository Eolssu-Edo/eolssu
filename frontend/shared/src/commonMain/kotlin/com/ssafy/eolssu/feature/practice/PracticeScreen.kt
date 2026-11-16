package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuBottomBar
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.practice.component.PracticeBoardScore
import com.ssafy.eolssu.feature.practice.component.PracticeBottomActions
import com.ssafy.eolssu.core.ui.component.BeatCountdownOverlay
import com.ssafy.eolssu.feature.practice.component.PracticeDialog
import com.ssafy.eolssu.feature.practice.component.PracticeDialogActions
import com.ssafy.eolssu.feature.practice.component.PracticeFingeringPage
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelSheet
import com.ssafy.eolssu.feature.practice.component.PracticePlayer
import com.ssafy.eolssu.feature.practice.component.PracticeRecordingBar
import com.ssafy.eolssu.feature.practice.component.PracticePlaybackLoadingOverlay
import com.ssafy.eolssu.feature.practice.component.PracticeTitleRow
import com.ssafy.eolssu.feature.practice.component.PracticeTopBar
import com.ssafy.eolssu.feature.practice.component.PracticeOptionButtonRow
import com.ssafy.eolssu.feature.practice.component.reportUserActivity
import com.ssafy.eolssu.feature.practice.layout.PracticeExpandedLayout
import com.ssafy.eolssu.feature.practice.layout.PracticeMediumLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_countdown_soon
import eolssu.shared.generated.resources.practice_score_failed
import eolssu.shared.generated.resources.practice_score_loading
import eolssu.shared.generated.resources.practice_score_missing
import eolssu.shared.generated.resources.practice_sheet_needs_review
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
    livePitch: Map<ScoreUnitKey, LivePitchCell> = emptyMap(),
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
        // 장단·모범 연주를 불러오는 동안에는 연주 재개 3·2·1과 같은 자리·크기로 화면 가운데에 띄운다.
        PracticePlaybackLoadingOverlay(state)
    }
    // 모든 화면 크기에서 배경을 눌러 닫을 수 있는 설정 시트를 사용한다.
    if (state.panel != null) {
        PracticePanelSheet(
            state,
            actions.panel,
            tablet = widthClass != WindowWidthClass.Compact,
            modifier = Modifier.reportUserActivity(actions.onUserActivity),
            expanded = widthClass == WindowWidthClass.Expanded,
        )
    }
    state.popup?.takeUnless { it == PracticePopup.SettingsPaused && state.panel != null }
        ?.let { PracticeDialog(it, actions.dialog, forStep = state.solo, evaluation = state.direct) }
}

/** 곡 상세를 거치지 않고 연습으로 들어와도 로딩·404·통신 실패에서 빠져나오고 재시도할 수 있다. */
@Composable
private fun PracticeSongLoadScreen(state: PracticeUiState, actions: PracticeScreenActions, snackbarHostState: SnackbarHostState) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.groupedBackground,
        topBar = { EolssuPageTopBar(title = practicePageTitle(state), onBack = actions.onBack, divider = false) },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            if (state.isSongLoading) CircularProgressIndicator(color = EolssuTheme.colors.accentText)
            Text(stringResource(when (state.songError) {
                PracticeSongError.NotFound -> Res.string.practice_score_missing
                PracticeSongError.NeedsReview -> Res.string.practice_sheet_needs_review
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
 * 휴대폰 배치(보드 PRACTICE-01): 페이지 머리 ‘연습’ → 본문(곡 제목·오른쪽 상태 문구, 악보 또는 운지법과 함께 보기,
 * 플레이어([PracticePlayer]))를 세로 스크롤 없이 한 화면에 두고, 연주 중에만 하단 바가 고정된다.
 * 연주 중에는 손으로 스크롤하기 어려우므로 악보는 플레이어·버튼을 뺀 남은 높이를 쓴다. 운지법을 끈 악보는 좌우로만 넘기고,
 * 운지법과 함께 보기는 남은 높이에 들어가는 운지만 한 페이지에 담아 넘긴다.
 *
 * 연습·연주 평가·연주 중은 같은 화면이므로 악보 자리 높이를 하나로 둔다(S15P31E102-1027). 그러지 않으면 화면이 작은 기기에서
 * 남은 높이로 정간 크기를 다시 계산해, 페이지를 옮기거나 연주를 시작할 때 정간 크기와 위치가 바뀐다.
 * 위 여백·제목 줄 높이·제목 아래 간격은 상태와 관계없이 같고, 하단은 플레이어와 평가·녹음 막대 중 높은 쪽만큼 늘 비워 둔다
 * ([PracticeCompactBottom]). 운지법과 함께 보기만 자리를 아끼려고 위 여백을 줄인다.
 */
@Composable
private fun PracticeCompactLayout(
    state: PracticeUiState,
    snackbarHostState: SnackbarHostState,
    actions: PracticeScreenActions,
    livePitch: Map<ScoreUnitKey, LivePitchCell>,
) {
    val recordingBar = state.recordingLayout
    // 연주 평가는 준비 상태의 플레이어·행동 버튼 없이 하단의 평가 대기 막대(‘평가 시작’)를 둔다.
    val showPlayer = !state.direct && state.mode in setOf(PracticeMode.Ready, PracticeMode.Model, PracticeMode.Rhythm)
    val fingering = state.fingeringView
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.groupedBackground,
        topBar = {
            // 휴대폰은 모드 버튼(‘한 음씩’·‘퍼펙트 스코어’)만 톱니 옆에 둬 제목 줄 폭을 넓힌다(S15P31E102-1037).
            PracticeTopBar(
                state, actions.onBack, actions.onOpenSettings,
                options = { PracticeOptionButtonRow(state, actions.panel, actions.bottom.onToggleStep, include = { it.modeButton }) },
            )
        },
        bottomBar = if (recordingBar) { { PracticeRecordingBar(state, actions.bottom) } } else null,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = if (fingering) 8.dp else 18.dp, bottom = 12.dp),
            ) {
                PracticeTitleRow(
                    state,
                    // 보기 버튼이 없는 경우에도 제목 줄 높이를 버튼의 누르는 높이(44dp)와 같게 둔다.
                    modifier = Modifier.heightIn(min = PracticeCompactTitleRowHeight),
                    showPill = false,
                    // 휴대폰 제목 줄 오른쪽에는 ‘운지법’·‘한글 표기’만 둔다. 모드 버튼은 상단 바 톱니 옆에 있다.
                    // 연습·구간 연습·연주 평가가 같은 자리·같은 모양이다(S15P31E102-816·1037).
                    trailing = { PracticeOptionButtonRow(state, actions.panel, actions.bottom.onToggleStep, include = { !it.modeButton }) },
                )
                Spacer(Modifier.height(if (fingering) 8.dp else 12.dp))
                if (fingering) {
                    PracticeFingeringPage(state, actions.onSelectUnit, modifier = Modifier.weight(1f), livePitch = livePitch)
                } else {
                    PracticeBoardScore(
                        state, actions.onSelectUnit, actions.onUserScroll, actions.onFollowNow,
                        modifier = Modifier.weight(1f), livePitch = livePitch,
                    )
                }
                PracticeCompactBottom(state, actions.bottom, showPlayer = showPlayer, barShown = recordingBar)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 휴대폰 연습 화면 제목 줄 높이. 오른쪽 보기 버튼의 누르는 높이와 같다. */
private val PracticeCompactTitleRowHeight = 44.dp

/** 악보와 플레이어 사이 간격. */
private val PracticeCompactPlayerGap = 8.dp

/** 휴대폰 본문 좌우 여백. 하단 막대는 이 여백 없이 화면 너비를 쓴다. */
private val PracticeCompactSidePadding = 20.dp

private enum class CompactBottomSlot { Player, Bar }

/**
 * 휴대폰 본문 아래 칸. 플레이어(간격 포함)와 화면 하단에 고정되는 평가·녹음 막대([PracticeRecordingBar]) 높이를 둘 다 재서
 * 높은 쪽([reserve])만큼 늘 자리를 잡는다(S15P31E102-1027).
 * - 플레이어를 보일 때: [reserve] 높이 안의 아래쪽에 플레이어를 둔다.
 * - 막대가 떠 있을 때: 막대는 화면 틀(하단 고정 영역)이 이미 비켜 두므로 [reserve]에서 막대 높이를 뺀 만큼만 비운다.
 * - 둘 다 없을 때도 [reserve]를 비워 둔다.
 * 본문은 내비게이션 바 영역을 이미 뺀 상태라, 여기서 잰 막대 높이에는 그 영역이 들어가지 않는다(화면 틀과 같은 기준).
 * 재기만 하는 사본은 배치하지 않아 보이지도, 화면 읽기에 읽히지도 않는다.
 */
@Composable
private fun PracticeCompactBottom(state: PracticeUiState, actions: PracticeBottomActions, showPlayer: Boolean, barShown: Boolean) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val gap = PracticeCompactPlayerGap.roundToPx()
        val players = subcompose(CompactBottomSlot.Player) { PracticePlayer(state, actions) }.map { it.measure(loose) }
        val playerHeight = players.maxOfOrNull { it.height } ?: 0
        // 하단 막대는 본문 좌우 여백 바깥까지 화면 너비를 쓰므로 같은 너비로 잰다.
        val barWidth = constraints.maxWidth + (PracticeCompactSidePadding * 2).roundToPx()
        val barHeight = subcompose(CompactBottomSlot.Bar) { EolssuBottomBar { PracticeRecordingBar(state, actions) } }
            .maxOfOrNull { it.measure(Constraints(minWidth = barWidth, maxWidth = barWidth)).height } ?: 0
        val reserve = maxOf(gap + playerHeight, barHeight)
        val height = (if (barShown) reserve - barHeight else reserve).coerceIn(0, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            if (showPlayer && !barShown) players.forEach { it.place(0, height - it.height) }
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

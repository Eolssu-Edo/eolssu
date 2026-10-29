package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.practice.component.IdleReminderDialog
import com.ssafy.eolssu.feature.practice.component.PracticeBottomActions
import com.ssafy.eolssu.feature.practice.component.PracticeBottomContent
import com.ssafy.eolssu.feature.practice.component.PracticeDialog
import com.ssafy.eolssu.feature.practice.component.PracticeDialogActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelSheet
import com.ssafy.eolssu.feature.practice.component.PracticeScoreArea
import com.ssafy.eolssu.feature.practice.component.PracticeScoreStyle
import com.ssafy.eolssu.feature.practice.component.reportUserActivity
import com.ssafy.eolssu.feature.practice.layout.PracticeExpandedLayout
import com.ssafy.eolssu.feature.practice.layout.PracticeMediumLayout
import eolssu.shared.generated.resources.Res
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_countdown_cancel_resume
import eolssu.shared.generated.resources.practice_countdown_resume
import eolssu.shared.generated.resources.practice_countdown_soon
import eolssu.shared.generated.resources.practice_countdown_with_jangdan
import eolssu.shared.generated.resources.practice_mode_countdown
import eolssu.shared.generated.resources.practice_mode_interrupted
import eolssu.shared.generated.resources.practice_mode_model
import eolssu.shared.generated.resources.practice_mode_ready
import eolssu.shared.generated.resources.practice_mode_recording
import eolssu.shared.generated.resources.practice_mode_solo
import eolssu.shared.generated.resources.practice_settings
import eolssu.shared.generated.resources.practice_settings_locked
import eolssu.shared.generated.resources.practice_subtitle
import eolssu.shared.generated.resources.practice_subtitle_solo
import eolssu.shared.generated.resources.practice_score_title
import eolssu.shared.generated.resources.practice_score_loading
import eolssu.shared.generated.resources.practice_score_missing
import eolssu.shared.generated.resources.practice_score_failed
import eolssu.shared.generated.resources.practice_score_retry
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
 * PRACTICE-01 연습. 같은 상태를 창 너비에 따라 휴대폰([WindowWidthClass.Compact]), 태블릿 세로([WindowWidthClass.Medium]),
 * 태블릿 가로·넓은 웹 창([WindowWidthClass.Expanded]) 배치로 보여준다. 카운트다운·확인 창은 배치와 관계없이 같다.
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
        if (state.mode == PracticeMode.Countdown) CountdownOverlay(state, actions.onCancelCountdown)
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
        topBar = { EolssuTopBar(title = stringResource(Res.string.practice_score_title), onBack = actions.onBack) },
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

/** 휴대폰 배치: 악보가 화면 대부분을 차지하고, 상태별 버튼은 아래에 고정한다. */
@Composable
private fun PracticeCompactLayout(state: PracticeUiState, snackbarHostState: SnackbarHostState, actions: PracticeScreenActions) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { PracticeTopBar(state, actions) },
        bottomBar = { PracticeBottomContent(state, actions.bottom) },
    ) {
        if (state.song != null) {
            PracticeScoreArea(state, PracticeScoreStyle.Phone, actions.onSelectEvent, actions.onUserScroll, actions.onFollowNow)
        }
    }
}

/**
 * 상단 바: 뒤로 · 곡 제목 · 상태 배지 · 연습 설정. [showSettings]가 아니면 설정 버튼을 두지 않는다(태블릿 가로는 상태 패널에 바로가기가 있다).
 */
@Composable
internal fun PracticeTopBar(
    state: PracticeUiState,
    actions: PracticeScreenActions,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    showSettings: Boolean = true,
) {
    val song = state.song ?: return
    val subtitle = stringResource(
        if (state.solo) Res.string.practice_subtitle_solo else Res.string.practice_subtitle,
        songInstrumentLabel(song),
        songLevelLabel(song.level),
    )
    EolssuTopBar(
        title = song.title,
        onBack = actions.onBack,
        modifier = modifier,
        subtitle = subtitle,
        below = { ModeBadge(state) },
        maxWidth = maxWidth,
        right = {
            if (showSettings) {
                EolssuIconButton(
                    if (state.settingsLocked) EolssuIcons.Lock else EolssuIcons.Settings,
                    stringResource(if (state.settingsLocked) Res.string.practice_settings_locked else Res.string.practice_settings),
                    actions.onOpenSettings,
                    enabled = !state.settingsLocked,
                )
            }
        },
    )
}

@Composable
private fun ModeBadge(state: PracticeUiState) {
    val (text, style) = when (state.mode) {
        PracticeMode.Ready -> stringResource(if (state.solo) Res.string.practice_mode_solo else Res.string.practice_mode_ready) to EolssuBadgeStyle.Soft
        PracticeMode.Model -> stringResource(Res.string.practice_mode_model) to EolssuBadgeStyle.Play
        PracticeMode.Countdown -> stringResource(Res.string.practice_mode_countdown) to EolssuBadgeStyle.Soft
        PracticeMode.Recording -> stringResource(Res.string.practice_mode_recording) to EolssuBadgeStyle.Recording
        PracticeMode.Interrupted -> stringResource(Res.string.practice_mode_interrupted) to EolssuBadgeStyle.Recording
    }
    EolssuBadge(text, style = style)
}

@Composable
private fun CountdownOverlay(state: PracticeUiState, onCancel: () -> Unit) {
    val message = stringResource(if (state.resume) Res.string.practice_countdown_resume else Res.string.practice_countdown_soon)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (state.settings.jangdanSound) stringResource(Res.string.practice_countdown_with_jangdan, message) else message,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = EolssuTheme.colors.textMuted,
        )
        Text("${state.count}", style = EolssuTheme.serif.brand.copy(fontSize = 140.sp, lineHeight = 140.sp, fontWeight = FontWeight.Bold))
        EolssuButton(
            stringResource(if (state.resume) Res.string.practice_countdown_cancel_resume else Res.string.practice_countdown_cancel),
            onCancel,
            style = EolssuButtonStyle.Secondary,
            modifier = Modifier.padding(horizontal = 0.dp),
            small = false,
        )
    }
}

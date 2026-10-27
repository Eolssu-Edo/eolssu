package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.score.ScoreHighlight
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.core.ui.score.ScorePlayback
import com.ssafy.eolssu.feature.practice.component.PracticeBottomActions
import com.ssafy.eolssu.feature.practice.component.PracticeBottomContent
import com.ssafy.eolssu.feature.practice.component.PracticeDialog
import com.ssafy.eolssu.feature.practice.component.PracticeDialogActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelSheet
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.instrument_daegeum
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_countdown_cancel_resume
import eolssu.shared.generated.resources.practice_countdown_resume
import eolssu.shared.generated.resources.practice_countdown_soon
import eolssu.shared.generated.resources.practice_countdown_with_jangdan
import eolssu.shared.generated.resources.practice_follow_now
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
import org.jetbrains.compose.resources.stringResource

/** PRACTICE-01 화면에서 부르는 동작. */
class PracticeScreenActions(
    val onBack: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSelectEvent: (ScoreEvent) -> Unit,
    val onUserScroll: () -> Unit,
    val onFollowNow: () -> Unit,
    val onCancelCountdown: () -> Unit,
    val bottom: PracticeBottomActions,
    val panel: PracticePanelActions,
    val dialog: PracticeDialogActions,
)

/**
 * PRACTICE-01 연습. 악보가 화면 대부분을 차지하고, 한 줄의 정간이 세로 스크롤 없이 들어가도록 정간 높이를 맞춘다.
 * 준비 상태에서는 음을 눌러 운지법을 보고, 모범 연주·연주 중에는 현재 위치를 따라간다.
 */
@Composable
fun PracticeScreen(state: PracticeUiState, snackbarHostState: SnackbarHostState, actions: PracticeScreenActions) {
    val song = state.song
    Box(Modifier.fillMaxSize()) {
        EolssuScaffold(
            snackbarHostState = snackbarHostState,
            topBar = { PracticeTopBar(state, actions) },
            bottomBar = { PracticeBottomContent(state, actions.bottom) },
        ) {
            if (song != null) ScoreArea(state, actions)
        }
        if (state.mode == PracticeMode.Countdown) CountdownOverlay(state, actions.onCancelCountdown)
    }
    if (state.panel != null) PracticePanelSheet(state, actions.panel)
    state.popup?.let { PracticeDialog(it, actions.dialog) }
}

@Composable
private fun PracticeTopBar(state: PracticeUiState, actions: PracticeScreenActions) {
    val song = state.song ?: return
    val subtitle = stringResource(
        if (state.solo) Res.string.practice_subtitle_solo else Res.string.practice_subtitle,
        stringResource(Res.string.instrument_daegeum),
        songLevelLabel(song.level),
    )
    EolssuTopBar(
        title = song.title,
        onBack = actions.onBack,
        subtitle = subtitle,
        below = { ModeBadge(state) },
        right = {
            EolssuIconButton(
                if (state.settingsLocked) EolssuIcons.Lock else EolssuIcons.Settings,
                stringResource(if (state.settingsLocked) Res.string.practice_settings_locked else Res.string.practice_settings),
                actions.onOpenSettings,
                enabled = !state.settingsLocked,
            )
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
private fun ScoreArea(state: PracticeUiState, actions: PracticeScreenActions) {
    val song = state.song ?: return
    val scrollState = rememberHorizontalScoreScroll(state)
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)) {
        val metrics = JeongganboMetrics.practice(song.jangdan, maxHeight, big = state.settings.bigScore)
        val recording = state.mode == PracticeMode.Recording || state.resuming
        val playback = if (state.live) ScorePlayback(state.eventStartedAt, state.eventDurationMs(), state.pausedFraction) else null
        JeongganboView(
            song = song,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            metrics = metrics,
            notation = ScoreNotation(state.settings.showHangul, state.settings.showSolfege),
            scrollState = scrollState,
            currentIndex = if (state.live) state.position.coerceAtMost(state.eventCount - 1) else null,
            highlight = if (recording) ScoreHighlight.Recording else ScoreHighlight.Position,
            playback = playback,
            follow = state.follow,
            onUserScroll = actions.onUserScroll,
            selectedIndex = if (state.mode == PracticeMode.Ready) state.selected else null,
            onEventClick = if (state.mode == PracticeMode.Ready) actions.onSelectEvent else null,
        )
        if (state.live && !state.follow) {
            EolssuButton(
                stringResource(Res.string.practice_follow_now),
                actions.onFollowNow,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 14.dp).shadow(8.dp, MaterialTheme.shapes.extraLarge),
                style = EolssuButtonStyle.Secondary,
                icon = EolssuIcons.Restart,
                small = true,
            )
        }
    }
}

/** 처음 들어오거나 크기를 바꾸면 시작 위치(오른쪽 끝)로, 카운트다운이 시작되면 처음으로 되돌린다. */
@Composable
private fun rememberHorizontalScoreScroll(state: PracticeUiState): androidx.compose.foundation.ScrollState {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.settings.bigScore) { scrollState.scrollTo(0) }
    LaunchedEffect(state.mode == PracticeMode.Countdown && !state.resume) {
        if (state.mode == PracticeMode.Countdown && !state.resume) scrollState.animateScrollTo(0)
    }
    return scrollState
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

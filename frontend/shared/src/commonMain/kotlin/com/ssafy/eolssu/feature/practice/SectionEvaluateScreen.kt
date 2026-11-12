package com.ssafy.eolssu.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.BeatCountdownOverlay
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.MicLevelBadge
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.feature.practice.component.PracticeSideDock
import com.ssafy.eolssu.feature.practice.component.JangguBeats
import com.ssafy.eolssu.feature.practice.component.PracticeFingeringPage
import com.ssafy.eolssu.feature.practice.component.PracticeTitleRow
import com.ssafy.eolssu.feature.practice.component.PracticeTitleToggles
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_abort
import eolssu.shared.generated.resources.practice_countdown_cancel
import eolssu.shared.generated.resources.practice_countdown_ready
import eolssu.shared.generated.resources.practice_countdown_soon
import eolssu.shared.generated.resources.practice_popup_abort_body
import eolssu.shared.generated.resources.practice_popup_abort_title
import eolssu.shared.generated.resources.practice_popup_keep_playing
import eolssu.shared.generated.resources.practice_popup_to_waiting
import eolssu.shared.generated.resources.practice_rec_time_total
import eolssu.shared.generated.resources.practice_start_eval_short
import eolssu.shared.generated.resources.weak_loop_evaluate
import org.jetbrains.compose.resources.stringResource

/** 구간 재평가 화면에서 부르는 동작. */
class SectionEvaluateActions(
    val onBack: () -> Unit,
    /** ‘평가 시작’: 마이크를 확인하고 3·2·1 뒤 구간을 한 번 녹음한다. */
    val onStart: () -> Unit,
    /** 3·2·1의 ‘시작 취소’와 중단 확인의 ‘연주 대기로’: 녹음을 버리고 평가 대기로 돌아간다. */
    val onCancel: () -> Unit,
    /** 녹음 중 ‘연주 중단’: 녹음·진행을 멈추고 ‘연주를 마칠까요?’를 묻는다. */
    val onAskAbort: () -> Unit = {},
    /** 중단 확인의 ‘계속 연주’·닫기: 3·2·1 뒤 멈춘 자리부터 잇는다. */
    val onKeepRecording: () -> Unit = {},
    val tools: SectionToolActions = SectionToolActions(),
)

/**
 * 구간 재평가(S15P31E102-776 · 연주 평가 화면 틀). 페이지 머리 ‘구간 재평가’와 오른쪽 톱니(연습 설정) → 곡 제목, 그 아래 오른쪽
 * ‘운지법 보기’·‘쉬운 표기’ 스위치 → 정간보(운지법 보기를 켜면 연습 화면과 같은 운지법 페이지) → 아래 고정 평가 막대.
 * 평가 대기는 ‘0:00 / 0:08’ · 빈 막대 · ‘평가 시작’, 3·2·1은 연주 평가와 같은 원(‘숨을 고르고 준비해 주세요.’ · ‘시작 취소’)과
 * 막대 자리의 ‘준비’, 녹음 중은 붉은 현재 정간 · 경과 시간 · 입력 표시 · 장구 박 표시 · 붉은 막대 · ‘연주 중단’(확인 창 ‘연주를 마칠까요?’)이다.
 * 끝까지 연주하면 바로 분석으로 간다. 장구는 설정의 ‘현재 박 표시’가 켜졌을 때만 보인다.
 */
@Composable
fun SectionEvaluateScreen(
    state: SectionLoopUiState,
    actions: SectionEvaluateActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    widthClass: WindowWidthClass = WindowWidthClass.Compact,
) {
    Box(Modifier.fillMaxSize()) {
        when (widthClass) {
            WindowWidthClass.Compact -> SectionEvaluateCompact(state, actions, snackbarHostState)
            WindowWidthClass.Medium -> SectionEvaluateMedium(state, actions, snackbarHostState)
            WindowWidthClass.Expanded -> SectionEvaluateExpanded(state, actions, snackbarHostState)
        }
        val count = state.countdown
        if (count != null && state.recording) {
            BeatCountdownOverlay(
                count = count,
                message = stringResource(Res.string.practice_countdown_soon),
                action = {
                    EolssuButton(
                        stringResource(Res.string.practice_countdown_cancel),
                        actions.onCancel,
                        style = EolssuButtonStyle.Secondary,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                },
            )
        }
    }
    SectionPanelSheet(state, evaluation = true, actions = actions.tools, tablet = widthClass != WindowWidthClass.Compact)
    // ‘연주 중단’을 누르면 녹음·진행이 멈춘 채로 묻는다. 닫거나 ‘계속 연주’면 3·2·1 뒤 멈춘 자리부터 잇는다.
    if (state.abortAsked) {
        EolssuDialog(
            title = stringResource(Res.string.practice_popup_abort_title),
            message = stringResource(Res.string.practice_popup_abort_body),
            onDismissRequest = actions.onKeepRecording,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_to_waiting), actions.onCancel)
            EolssuButton(stringResource(Res.string.practice_popup_keep_playing), actions.onKeepRecording, style = EolssuButtonStyle.Secondary)
        }
    }
}

/** 휴대폰: 머리 · 곡 제목과 스위치 · 정간보(또는 운지법 페이지) · 아래 고정 평가 막대. */
@Composable
private fun SectionEvaluateCompact(state: SectionLoopUiState, actions: SectionEvaluateActions, snackbarHostState: SnackbarHostState) {
    val song = state.song
    val section = state.section
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            EolssuPageTopBar(
                title = stringResource(Res.string.weak_loop_evaluate),
                onBack = actions.onBack,
                right = { if (song != null && !state.recording) SectionSettingsButton(actions.tools.onOpenSettings) },
            )
        },
        bottomBar = if (song != null && section != null) {
            { EvaluateBar(state, actions.onStart, actions.onAskAbort) }
        } else {
            null
        },
    ) {
        if (song == null || section == null) {
            SectionLoading(state.loading)
            return@EolssuScaffold
        }
        val practice = state.asPracticeState(evaluation = true)
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            PracticeTitleRow(
                practice,
                showPill = false,
                toggles = PracticeTitleToggles(actions.tools.onToggleFingeringView, actions.tools.onToggleNotation),
            )
            Spacer(Modifier.height(8.dp))
            if (practice.fingeringView) {
                PracticeFingeringPage(state.asFingeringState(), onSelectUnit = {}, modifier = Modifier.weight(1f))
            } else {
                SectionBoardScore(state, song, Modifier.weight(1f), showCurrent = state.recording && state.countdown == null)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 태블릿 세로: 연주 평가의 태블릿 세로 배치([com.ssafy.eolssu.feature.practice.layout.PracticeMediumLayout])처럼 머리 오른쪽 톱니,
 * 넓은 정간보, 가운데 640dp로 모은 아래 평가 막대. 쉬운 표기는 설정 시트에서 바꾼다.
 */
@Composable
private fun SectionEvaluateMedium(state: SectionLoopUiState, actions: SectionEvaluateActions, snackbarHostState: SnackbarHostState) {
    val song = state.song
    val ready = song != null && state.section != null
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = WindowBreakpoints.ContentMaxWidth,
        topBar = {
            EolssuPageTopBar(
                title = stringResource(Res.string.weak_loop_evaluate),
                onBack = actions.onBack,
                modifier = Modifier.padding(horizontal = 12.dp),
                maxWidth = WindowBreakpoints.ContentMaxWidth,
                right = { if (song != null && !state.recording) SectionSettingsButton(actions.tools.onOpenSettings) },
            )
        },
        bottomBar = if (ready) {
            {
                Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = TabletBarMaxWidth).fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                    EvaluateBar(state, actions.onStart, actions.onAskAbort)
                }
            }
        } else {
            null
        },
    ) {
        if (song == null || !ready) {
            SectionLoading(state.loading)
            return@EolssuScaffold
        }
        SectionBoardScore(
            state, song, Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
            showCurrent = state.recording && state.countdown == null,
        )
    }
}

/**
 * 태블릿 가로·넓은 창: 연주 평가의 태블릿 가로 배치([com.ssafy.eolssu.feature.practice.layout.PracticeExpandedLayout])처럼
 * 왼쪽에 정간보, 오른쪽 380dp 상태 패널(곡 정보 · 설정 바로가기 · 평가 막대). 머리의 톱니 대신 상태 패널에서 설정을 연다.
 */
@Composable
private fun SectionEvaluateExpanded(state: SectionLoopUiState, actions: SectionEvaluateActions, snackbarHostState: SnackbarHostState) {
    val song = state.song
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        contentMaxWidth = WindowBreakpoints.ContentMaxWidth + SideGutter * 2,
        topBar = {
            EolssuPageTopBar(
                title = stringResource(Res.string.weak_loop_evaluate),
                onBack = actions.onBack,
                modifier = Modifier.padding(horizontal = SideGutter / 2),
                maxWidth = WindowBreakpoints.ContentMaxWidth + SideGutter,
            )
        },
    ) {
        if (song == null || state.section == null) {
            SectionLoading(state.loading)
            return@EolssuScaffold
        }
        Row(
            Modifier.fillMaxSize().padding(start = SideGutter, end = SideGutter, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionBoardScore(state, song, Modifier.weight(1f).fillMaxHeight(), showCurrent = state.recording && state.countdown == null)
            PracticeSideDock(
                state.asPracticeState(evaluation = true), actions.tools.asPanelActions(), Modifier.width(DockWidth).fillMaxHeight(),
            ) {
                EvaluateBar(state, actions.onStart, actions.onAskAbort)
            }
        }
    }
}

private val SideGutter = 40.dp

/** 태블릿 가로 오른쪽 상태 패널 너비. 연습 화면과 같다. */
private val DockWidth = 380.dp

/** 태블릿 세로 아래 평가 막대의 내용 너비. 연습 화면과 같다. */
private val TabletBarMaxWidth = 640.dp

/**
 * 연주 평가 하단 막대([com.ssafy.eolssu.feature.practice.component.PracticeRecordingBar])와 같은 모양: 경과 ‘0:03’과 흐린 ‘/ 0:08’ ·
 * 오른쪽 입력 표시 · 장구 박 표시 · 6dp 붉은 진행 막대 · 한 버튼(대기 ‘평가 시작’, 녹음 중 ‘연주 중단’).
 * 3·2·1 동안에는 같은 내용을 안 보이게 그려 높이를 지키고 ‘준비’만 보인다.
 */
@Composable
private fun EvaluateBar(state: SectionLoopUiState, onStart: () -> Unit, onAskAbort: () -> Unit) {
    if (state.countdown == null) {
        EvaluateBarContent(state, onStart, onAskAbort)
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(0f).clearAndSetSemantics {}) { EvaluateBarContent(state, onStart, onAskAbort) }
        Text(
            stringResource(Res.string.practice_countdown_ready),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.textSub,
        )
    }
}

@Composable
private fun EvaluateBarContent(state: SectionLoopUiState, onStart: () -> Unit, onAskAbort: () -> Unit) {
    val colors = EolssuTheme.colors
    val recording = state.recording && state.countdown == null
    val progress = if (recording) state.roundProgress.firstOrNull() ?: 0f else 0f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                buildAnnotatedString {
                    append(formatClock(if (recording) state.elapsedSeconds else 0.0))
                    append(" ")
                    withStyle(SpanStyle(fontSize = 13.sp, color = colors.textMuted, fontWeight = FontWeight.Normal)) {
                        append(stringResource(Res.string.practice_rec_time_total, formatClock(state.roundSeconds)))
                    }
                },
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            MicLevelBadge(if (recording) state.micLevel else 0f)
        }
        if (state.settings.beatVisual) state.song?.displayJangdan?.let { JangguBeats(it.daegang.size, state.beatIndex) }
        Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceStrong, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.rec, RoundedCornerShape(3.dp)))
        }
        if (state.recording) {
            EolssuButton(
                stringResource(Res.string.practice_abort), onAskAbort, Modifier.fillMaxWidth(), compact = true, small = true,
                style = EolssuButtonStyle.Danger, icon = EolssuIcons.Stop,
            )
        } else {
            EolssuButton(
                stringResource(Res.string.practice_start_eval_short), onStart, Modifier.fillMaxWidth(), compact = true, small = true,
                icon = EolssuIcons.Mic,
            )
        }
    }
}

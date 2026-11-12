package com.ssafy.eolssu.feature.practice.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.MicLevelBadge
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.recordingElapsedSeconds
import com.ssafy.eolssu.feature.practice.recordingProgress
import com.ssafy.eolssu.feature.practice.recordingTotalSeconds
import com.ssafy.eolssu.feature.practice.stepUi
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_abort
import eolssu.shared.generated.resources.practice_countdown_ready
import eolssu.shared.generated.resources.practice_no_input_hint
import eolssu.shared.generated.resources.practice_rec_time_total
import eolssu.shared.generated.resources.practice_start_eval_short
import eolssu.shared.generated.resources.practice_restart
import eolssu.shared.generated.resources.practice_beat_janggu
import eolssu.shared.generated.resources.practice_jangdan_beat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 연습 화면 하단·플레이어에서 부르는 동작. */
class PracticeBottomActions(
    val onSelectModel: () -> Unit = {},
    val onSelectRhythm: () -> Unit = {},
    val onStartModel: () -> Unit,
    val onToggleModel: () -> Unit,
    val onRestartModel: () -> Unit,
    val onStopModel: () -> Unit,
    val onStartEval: () -> Unit,
    val onSwitchToEval: () -> Unit,
    val onAskRestart: () -> Unit,
    val onAskAbort: () -> Unit,
    /** 플레이어의 −5초·+5초. 양수면 앞으로. */
    val onSeek: (deltaMs: Long) -> Unit = {},
    val onSeekToFraction: (Float) -> Unit = {},
    /** 설정 옆 ‘한 음씩’ 토글(혼자 연습). */
    val onToggleStep: () -> Unit = {},
    /** 한 음씩의 오른쪽 ‘내 연주’ 칸: 시작·멈춤·멈춘 자리부터 이어서. */
    val onTapStepPlaying: () -> Unit = {},
    /** 한 음씩의 왼쪽 ‘지금 음’ 칸: 지금 정간의 음 듣기. */
    val onListenStepNote: () -> Unit = {},
    /** 음 판정 연결 전 임시: 지금 음을 맞게 분 것으로 넘긴다. */
    val onConfirmStepNote: () -> Unit = {},
    val onStartRhythm: () -> Unit = {},
    val onToggleRhythm: () -> Unit = {},
    val onDownloadSheetAudio: () -> Unit = {},
    /** 구간 연습의 ‘구간 재평가 시작’(S15P31E102-794). */
    val onStartSectionEval: () -> Unit = {},
)

/**
 * 연주 중 하단(`.bottombar`, 상태 D·소리 입력 없음·중단): 경과 시간 ‘0:20 / 0:48’ · 마이크 뱃지 · 현재 박 장구 ·
 * 6dp 붉은 진행 막대 · 입력 없음 안내 · ‘처음부터 다시’ + ‘연주 중단’. 중단됨·이어 연주 카운트다운에서는 두 버튼이 회색으로 잠긴다.
 * 연주 평가([PracticeUiState.direct])는 ‘처음부터’ 없이 한 버튼만 너비를 채워 둔다. 시작 전 대기([PracticeUiState.awaitingStart])는
 * 0:00 · 빈 진행 막대에 ‘평가 시작’, 연주 중에는 ‘연주 중단’이다. 구간 재평가는 시간과 진행 막대를 구간 안에서 잰다(S15P31E102-824).
 * 박 표시는 진행 막대 위에 두고, 시간·마이크 줄과 박 표시가 진행 막대에 붙지 않게 사이를 띄운다.
 * [stacked]이면 두 버튼을 위아래로 쌓는다(태블릿 가로 상태 패널).
 * 평가 시작 카운트다운([PracticeUiState.preparing])에는 같은 내용을 보이지 않게 그려 높이를 지키고, 가운데에 ‘준비’만 둔다.
 * 그래서 카운트다운 뒤에 비치는 악보가 연주를 시작한 뒤와 같은 배치가 된다.
 */
@Composable
fun ColumnScope.PracticeRecordingBar(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean = false) {
    if (!state.preparing) {
        RecordingBarContent(state, actions, stacked)
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(0f).clearAndSetSemantics {}) { RecordingBarContent(state, actions, stacked) }
        Text(
            stringResource(Res.string.practice_countdown_ready),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.textSub,
        )
    }
}

@Composable
private fun RecordingBarContent(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean) {
    val song = state.song ?: return
    // 평가 시작 전 대기에는 고른 음과 관계없이 처음 자리(0:00 · 빈 막대 · 박 표시 없음)를 보인다.
    val awaiting = state.awaitingStart
    val progress = if (awaiting) 0f else state.recordingProgress
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val colors = EolssuTheme.colors
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                buildAnnotatedString {
                    append(formatClock(if (awaiting) 0.0 else state.recordingElapsedSeconds))
                    append(" ")
                    withStyle(SpanStyle(fontSize = 13.sp, color = colors.textMuted, fontWeight = FontWeight.Normal)) {
                        append(stringResource(Res.string.practice_rec_time_total, formatClock(state.recordingTotalSeconds)))
                    }
                },
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            MicLevelBadge(state.micLevel)
        }
        if (state.settings.beatVisual) song.displayJangdan?.let { JangguBeats(it.daegang.size, if (state.finished || awaiting) -1 else state.beatIndex) }
        Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceStrong, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.rec, RoundedCornerShape(3.dp)))
        }
        if (state.noInputHint) NoInputBanner()
        val locked = state.mode != PracticeMode.Recording
        if (state.direct) {
            // 연주 평가는 ‘처음부터’ 없이 한 버튼만 둔다(S15P31E102-750). 중단하면 확인 창에서 이 대기 상태로 돌아온다.
            when {
                awaiting -> EolssuButton(
                    stringResource(Res.string.practice_start_eval_short), actions.onStartEval, Modifier.fillMaxWidth(), compact = true, small = true,
                    icon = EolssuIcons.Mic, enabled = state.recordingSupported,
                )
                locked -> OffButton(stringResource(Res.string.practice_abort), Modifier.fillMaxWidth())
                else -> EolssuButton(
                    stringResource(Res.string.practice_abort), actions.onAskAbort, Modifier.fillMaxWidth(), compact = true, small = true,
                    style = EolssuButtonStyle.Danger, icon = EolssuIcons.Stop,
                )
            }
        } else if (locked) {
            PairedButtons(stacked, first = { OffButton(stringResource(Res.string.practice_restart), it) }, second = { OffButton(stringResource(Res.string.practice_abort), it) })
        } else {
            PairedButtons(
                stacked,
                first = { m ->
                    EolssuButton(stringResource(Res.string.practice_restart), actions.onAskRestart, m, compact = true, small = true, style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Restart)
                },
                second = { m ->
                    EolssuButton(stringResource(Res.string.practice_abort), actions.onAskAbort, m, compact = true, small = true, style = EolssuButtonStyle.Danger, icon = EolssuIcons.Stop)
                },
            )
        }
    }
}

/**
 * 태블릿 배치가 쓰는 상태별 하단 내용: 연주 중이면 [PracticeRecordingBar], 아니면 플레이어 카드와 행동 버튼.
 * 연주 평가([PracticeUiState.direct])는 준비 상태의 플레이어·행동 버튼 없이 평가 대기 막대를 둔다.
 * 한 음씩이 켜지면 플레이어 카드 대신 두 칸([PracticeStepPanels])을 둔다.
 * 휴대폰은 플레이어를 본문 안에 두므로 이 함수를 쓰지 않는다.
 */
@Composable
fun ColumnScope.PracticeBottomContent(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean = false) {
    if (state.recordingLayout) {
        PracticeRecordingBar(state, actions, stacked)
    } else if (!state.direct) {
        if (state.stepUi) PracticeStepPanels(state, actions) else PracticePlayerCard(state, actions)
        PracticeCallToAction(state, actions)
    }
}

/** 두 버튼을 나란히(첫 버튼 1 : 둘째 1.4) 두거나, [stacked]이면 위아래로 쌓는다. */
@Composable
internal fun PairedButtons(stacked: Boolean, first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1.4f))
        }
    }
}

/** 잠긴 버튼(`.btn.off`): 회색 바탕에 흐린 글자, 누를 수 없다. */
@Composable
private fun OffButton(text: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Box(
        modifier.heightIn(min = 48.dp).background(colors.surfaceStrong, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textDisabled, textAlign = TextAlign.Center)
    }
}

/** 연주 중 입력이 한동안 없을 때 마이크를 확인하라는 띠(붉은 연한 바탕, 경고 아이콘). */
@Composable
private fun NoInputBanner() {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.recSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(EolssuIcons.Alert, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.recText)
        Text(
            stringResource(Res.string.practice_no_input_hint),
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            color = colors.recText,
        )
    }
}

/** 사진 기반 장구. 고정된 칸 안에서 현재 박만 강조해 박이 바뀌어도 배치가 움직이지 않는다. */
@Composable
internal fun JangguBeats(count: Int, current: Int) {
    if (count <= 0) return
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.practice_jangdan_beat)
    val janggu = painterResource(Res.drawable.practice_beat_janggu)
    BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp).semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo((current + 1).coerceIn(0, count).toFloat(), 0f..count.toFloat())
    }, contentAlignment = Alignment.Center) {
        val slotWidth = minOf(44.dp, maxWidth / count)
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { i ->
                val active = i == current
                val scale by animateFloatAsState(if (active) 1f else 0.82f)
                val opacity by animateFloatAsState(when {
                    active -> 1f
                    current >= 0 && i < current -> 0.65f
                    else -> 0.3f
                })
                val shape = RoundedCornerShape(8.dp)
                Box(Modifier.width(slotWidth).height(36.dp)
                    .background(if (active) colors.recSoft else Color.Transparent, shape)
                    .border(1.dp, if (active) colors.rec else Color.Transparent, shape),
                    contentAlignment = Alignment.Center) {
                    Image(janggu, contentDescription = null, alpha = opacity,
                        modifier = Modifier.padding(3.dp).fillMaxWidth().height(28.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale })
                }
            }
        }
    }
}

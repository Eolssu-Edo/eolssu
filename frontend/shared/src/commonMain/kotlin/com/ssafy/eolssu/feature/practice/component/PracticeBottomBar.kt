package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_abort
import eolssu.shared.generated.resources.practice_mic_off
import eolssu.shared.generated.resources.practice_mic_on
import eolssu.shared.generated.resources.practice_no_input_hint
import eolssu.shared.generated.resources.practice_rec_time_detail
import eolssu.shared.generated.resources.practice_restart
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 연습 화면 하단·플레이어에서 부르는 동작. */
class PracticeBottomActions(
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
)

/**
 * 연주 중 하단(`.bottombar`, 상태 D·소리 입력 없음·중단): 경과 시간 ‘0:20 / 0:48 · 42%’ · 마이크 뱃지 · 6dp 붉은 진행 막대 ·
 * 현재 박 점 · 입력 없음 안내 · ‘처음부터 다시’ + ‘연주 중단’. 중단됨·이어 연주 카운트다운에서는 두 버튼이 회색으로 잠긴다.
 * [stacked]이면 두 버튼을 위아래로 쌓는다(태블릿 가로 상태 패널).
 */
@Composable
fun ColumnScope.PracticeRecordingBar(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean = false) {
    val song = state.song ?: return
    val colors = EolssuTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            buildAnnotatedString {
                append(formatClock(state.elapsedMs / 1000))
                append(" ")
                withStyle(SpanStyle(fontSize = 13.sp, color = colors.textMuted, fontWeight = FontWeight.Normal)) {
                    append(stringResource(Res.string.practice_rec_time_detail, formatClock(state.totalSeconds.toDouble()), "${state.progressPercent.roundToInt()}%"))
                }
            },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
        )
        MicBadge(state.micLevel)
    }
    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceStrong, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth((state.progressPercent / 100f).coerceIn(0f, 1f)).fillMaxHeight().background(colors.rec, RoundedCornerShape(3.dp)))
    }
    if (state.settings.beatVisual) BeatDots(song.jangdan.daegang.size, if (state.finished) -1 else state.beatIndex)
    if (state.noInputHint) NoInputBanner()
    val locked = state.mode != PracticeMode.Recording
    if (locked) {
        PairedButtons(stacked, first = { OffButton(stringResource(Res.string.practice_restart), it) }, second = { OffButton(stringResource(Res.string.practice_abort), it) })
    } else {
        PairedButtons(
            stacked,
            first = { m ->
                EolssuButton(stringResource(Res.string.practice_restart), actions.onAskRestart, m, compact = true, style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Restart)
            },
            second = { m ->
                EolssuButton(stringResource(Res.string.practice_abort), actions.onAskAbort, m, compact = true, style = EolssuButtonStyle.Danger, icon = EolssuIcons.Stop)
            },
        )
    }
}

/**
 * 태블릿 배치가 쓰는 상태별 하단 내용: 연주 중이면 [PracticeRecordingBar], 아니면 플레이어 카드와 행동 버튼.
 * 휴대폰은 플레이어를 본문 안에 두므로 이 함수를 쓰지 않는다.
 */
@Composable
fun ColumnScope.PracticeBottomContent(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean = false) {
    if (state.mode == PracticeMode.Recording || state.mode == PracticeMode.Interrupted || state.resuming) {
        PracticeRecordingBar(state, actions, stacked)
    } else {
        PracticePlayerCard(state, actions)
        PracticeCallToAction(state, actions)
    }
}

/** 두 버튼을 나란히(첫 버튼 1 : 둘째 1.4) 두거나, [stacked]이면 위아래로 쌓는다. */
@Composable
private fun PairedButtons(stacked: Boolean, first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
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
        modifier.heightIn(min = 52.dp).background(colors.surfaceStrong, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textDisabled, textAlign = TextAlign.Center)
    }
}

/** 마이크 뱃지(`.badge.lite`, 30dp): 마이크 아이콘 · 3dp 막대 5개 · ‘입력 없음’/‘입력 감지’. */
@Composable
private fun MicBadge(level: Float) {
    val colors = EolssuTheme.colors
    val off = level == 0f
    Row(
        Modifier
            .height(30.dp)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.lineSoft, CircleShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(EolssuIcons.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textMuted)
        Row(Modifier.height(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(0.4f, 0.8f, 1f, 0.6f, 0.3f).forEach { f ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((level * f * 14).coerceAtLeast(2f).dp)
                        .background(if (off) colors.textMuted else colors.rec, RoundedCornerShape(1.dp)),
                )
            }
        }
        Text(
            stringResource(if (off) Res.string.practice_mic_off else Res.string.practice_mic_on),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = colors.textMuted,
        )
    }
}

/** 연주 중 입력이 한동안 없을 때 마이크를 확인하라는 띠(붉은 연한 바탕, 경고 아이콘). */
@Composable
private fun NoInputBanner() {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.recSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(EolssuIcons.Alert, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.recText)
        Text(
            stringResource(Res.string.practice_no_input_hint),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            color = colors.recText,
        )
    }
}

/** 현재 박 점(12dp): 지난 박은 먹색, 현재 박은 붉은색에 연한 붉은 고리, 다음 박은 옅은 선 색. */
@Composable
private fun BeatDots(count: Int, current: Int) {
    val colors = EolssuTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            val dot = when {
                i == current -> Modifier.border(3.dp, colors.recSoft, CircleShape).padding(3.dp).background(colors.rec, CircleShape)
                current >= 0 && i < current -> Modifier.background(colors.text, CircleShape)
                else -> Modifier.background(colors.line, CircleShape)
            }
            Box(Modifier.size(if (i == current) 18.dp else 12.dp).then(dot))
        }
    }
}

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.stepUi
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_echo_off
import eolssu.shared.generated.resources.practice_abort
import eolssu.shared.generated.resources.practice_mic_off
import eolssu.shared.generated.resources.practice_mic_on
import eolssu.shared.generated.resources.practice_no_input_hint
import eolssu.shared.generated.resources.practice_rec_time_detail
import eolssu.shared.generated.resources.practice_restart
import eolssu.shared.generated.resources.practice_beat_janggu
import eolssu.shared.generated.resources.practice_jangdan_beat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

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
)

/**
 * 연주 중 하단(`.bottombar`, 상태 D·소리 입력 없음·중단): 경과 시간 ‘0:20 / 0:48 · 42%’ · 마이크 뱃지 · 현재 박 장구 ·
 * 6dp 붉은 진행 막대 · 입력 없음 안내 · ‘처음부터 다시’ + ‘연주 중단’. 중단됨·이어 연주 카운트다운에서는 두 버튼이 회색으로 잠긴다.
 * 박 표시는 진행 막대 위에 두고, 시간·마이크 줄과 박 표시가 진행 막대에 붙지 않게 사이를 띄운다.
 * [stacked]이면 두 버튼을 위아래로 쌓는다(태블릿 가로 상태 패널).
 */
@Composable
fun ColumnScope.PracticeRecordingBar(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean = false) {
    val song = state.song ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        if (state.settings.beatVisual) song.displayJangdan?.let { JangguBeats(it.daegang.size, if (state.finished) -1 else state.beatIndex) }
        Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceStrong, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth((state.progressPercent / 100f).coerceIn(0f, 1f)).fillMaxHeight().background(colors.rec, RoundedCornerShape(3.dp)))
        }
        if (state.echoCorrectionOff) Text(stringResource(Res.string.practice_echo_off),
            style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        if (state.noInputHint) NoInputBanner()
        val locked = state.mode != PracticeMode.Recording
        if (locked) {
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
 * 한 음씩이 켜지면 플레이어 카드 대신 두 칸([PracticeStepPanels])을 둔다.
 * 휴대폰은 플레이어를 본문 안에 두므로 이 함수를 쓰지 않는다.
 */
@Composable
fun ColumnScope.PracticeBottomContent(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean = false) {
    if (state.mode == PracticeMode.Recording || state.mode == PracticeMode.Interrupted || state.resuming) {
        PracticeRecordingBar(state, actions, stacked)
    } else {
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
private fun JangguBeats(count: Int, current: Int) {
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

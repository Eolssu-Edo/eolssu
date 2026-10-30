package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.ModelAudioStatus
import com.ssafy.eolssu.feature.practice.PracticeUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.model_audio_loading
import eolssu.shared.generated.resources.model_audio_unavailable
import eolssu.shared.generated.resources.model_audio_failed
import eolssu.shared.generated.resources.model_audio_unsupported
import eolssu.shared.generated.resources.model_audio_interrupted
import eolssu.shared.generated.resources.model_audio_retry
import eolssu.shared.generated.resources.practice_abort
import eolssu.shared.generated.resources.practice_from_start
import eolssu.shared.generated.resources.practice_hint_ready
import eolssu.shared.generated.resources.practice_hint_solo
import eolssu.shared.generated.resources.practice_hint_solo_only
import eolssu.shared.generated.resources.practice_listen_model
import eolssu.shared.generated.resources.practice_mic_off
import eolssu.shared.generated.resources.practice_mic_on
import eolssu.shared.generated.resources.practice_model_badge
import eolssu.shared.generated.resources.practice_no_input_hint
import eolssu.shared.generated.resources.practice_pause
import eolssu.shared.generated.resources.practice_play
import eolssu.shared.generated.resources.practice_rec_time_detail
import eolssu.shared.generated.resources.practice_restart
import eolssu.shared.generated.resources.practice_recording_unsupported
import eolssu.shared.generated.resources.practice_start_eval
import eolssu.shared.generated.resources.practice_stop_listening
import eolssu.shared.generated.resources.practice_switch_to_eval
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** 연습 화면 하단 고정 영역에서 부르는 동작. */
class PracticeBottomActions(
    val onStartModel: () -> Unit,
    val onToggleModel: () -> Unit,
    val onRestartModel: () -> Unit,
    val onStopModel: () -> Unit,
    val onStartEval: () -> Unit,
    val onSwitchToEval: () -> Unit,
    val onAskRestart: () -> Unit,
    val onAskAbort: () -> Unit,
)

/**
 * PRACTICE-01 하단: 준비(·혼자 연습) / 모범 연주 / 연주 중 모습.
 * [stacked]이면 나란히 두던 두 버튼을 위아래로 쌓는다(태블릿 가로의 오른쪽 상태 패널).
 */
@Composable
fun ColumnScope.PracticeBottomContent(state: PracticeUiState, actions: PracticeBottomActions, stacked: Boolean = false) {
    val song = state.song ?: return
    val unsupportedEvaluation = !state.recordingSupported && (!state.solo || song.evaluable)
    if (unsupportedEvaluation) Hint(stringResource(Res.string.practice_recording_unsupported))
    when {
        state.mode == PracticeMode.Model -> {
            ModelTransport(state, actions)
            ModelAudioHint(state.modelAudioStatus)
            ProgressBar(state.progressPercent, EolssuTheme.colors.play)
            if (!state.solo) {
                EolssuButton(stringResource(Res.string.practice_start_eval), actions.onStartEval, icon = EolssuIcons.Mic, enabled = state.recordingSupported)
            } else if (song.evaluable) {
                EolssuButton(stringResource(Res.string.practice_switch_to_eval), actions.onSwitchToEval, icon = EolssuIcons.Mic, enabled = state.recordingSupported)
            }
        }
        state.mode == PracticeMode.Recording || state.mode == PracticeMode.Interrupted || state.resuming -> {
            RecordingStatus(state)
            ProgressBar(state.progressPercent, EolssuTheme.colors.rec)
            if (state.settings.beatVisual) BeatBars(song.jangdan.daegang.size, if (state.finished) -1 else state.beatIndex)
            if (state.noInputHint) NoInputHint()
            val locked = state.mode != PracticeMode.Recording
            PairedButtons(
                stacked,
                first = { m ->
                    EolssuButton(
                        stringResource(Res.string.practice_restart), actions.onAskRestart, m, compact = true,
                        style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Restart, enabled = !locked,
                    )
                },
                second = { m ->
                    EolssuButton(
                        stringResource(Res.string.practice_abort), actions.onAskAbort, m, compact = true,
                        style = EolssuButtonStyle.Danger, icon = EolssuIcons.Stop, enabled = !locked,
                    )
                },
            )
        }
        state.solo -> {
            if (!unsupportedEvaluation) Hint(stringResource(if (song.evaluable) Res.string.practice_hint_solo else Res.string.practice_hint_solo_only))
            PairedButtons(
                stacked,
                secondWeight = 1.25f,
                first = { m ->
                    EolssuButton(
                        stringResource(Res.string.practice_listen_model), actions.onStartModel, m, compact = true,
                        style = if (song.evaluable) EolssuButtonStyle.Secondary else EolssuButtonStyle.Primary, icon = EolssuIcons.Ear,
                    )
                },
                second = if (song.evaluable) {
                    { m -> EolssuButton(stringResource(Res.string.practice_switch_to_eval), actions.onSwitchToEval, m, icon = EolssuIcons.Mic, compact = true, enabled = state.recordingSupported) }
                } else {
                    null
                },
            )
        }
        else -> {
            val countingDown = state.mode == PracticeMode.Countdown
            if (!unsupportedEvaluation) Hint(stringResource(Res.string.practice_hint_ready))
            PairedButtons(
                stacked,
                first = { m ->
                    EolssuButton(
                        stringResource(Res.string.practice_listen_model), actions.onStartModel, m, compact = true,
                        style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Ear, enabled = !countingDown,
                    )
                },
                second = { m ->
                    EolssuButton(
                        stringResource(Res.string.practice_start_eval), actions.onStartEval, m, compact = true,
                        icon = EolssuIcons.Mic, enabled = !countingDown && state.recordingSupported,
                    )
                },
            )
        }
    }
}

/** 두 버튼을 나란히(첫 버튼 1 : 둘째 [secondWeight]) 두거나, [stacked]이면 위아래로 쌓는다. */
@Composable
private fun PairedButtons(
    stacked: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: (@Composable (Modifier) -> Unit)?,
    secondWeight: Float = 1.4f,
) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            first(Modifier.fillMaxWidth())
            second?.invoke(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            first(Modifier.weight(1f))
            second?.invoke(Modifier.weight(secondWeight))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = EolssuTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ModelTransport(state: PracticeUiState, actions: PracticeBottomActions) {
    val colors = EolssuTheme.colors
    val needsRetry = state.modelAudioStatus in setOf(ModelAudioStatus.Failed, ModelAudioStatus.Unavailable)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundButton(
            if (state.playing) EolssuIcons.Pause else if (needsRetry) EolssuIcons.Restart else EolssuIcons.Play,
            stringResource(if (state.playing) Res.string.practice_pause else if (state.modelAudioStatus in setOf(ModelAudioStatus.Failed, ModelAudioStatus.Unavailable)) Res.string.model_audio_retry else Res.string.practice_play),
            actions.onToggleModel,
            container = colors.play,
            content = colors.onPlay,
            enabled = state.modelAudioStatus != ModelAudioStatus.Loading && state.modelAudioStatus != ModelAudioStatus.Unsupported,
        )
        RoundButton(EolssuIcons.Restart, stringResource(Res.string.practice_from_start), actions.onRestartModel,
            enabled = state.modelAudioStatus in setOf(ModelAudioStatus.Playing, ModelAudioStatus.Paused, ModelAudioStatus.Completed, ModelAudioStatus.Interrupted))
        Column(Modifier.weight(1f)) {
            EolssuBadge(stringResource(Res.string.practice_model_badge), style = EolssuBadgeStyle.Play)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) { append(formatClock(state.elapsedMs / 1000)) }
                    append(" / ${formatClock(state.totalSeconds.toDouble())}")
                },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.textMuted,
            )
        }
        EolssuButton(
            stringResource(Res.string.practice_stop_listening), actions.onStopModel,
            style = EolssuButtonStyle.Secondary, icon = EolssuIcons.Stop, small = true,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, container: Color? = null, content: Color? = null, enabled: Boolean = true) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = container ?: colors.background,
        contentColor = content ?: colors.text,
        border = BorderStroke(1.dp, container ?: colors.line),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun ModelAudioHint(status: ModelAudioStatus) {
    val message = when (status) {
        ModelAudioStatus.Loading -> Res.string.model_audio_loading
        ModelAudioStatus.Unavailable -> Res.string.model_audio_unavailable
        ModelAudioStatus.Failed -> Res.string.model_audio_failed
        ModelAudioStatus.Unsupported -> Res.string.model_audio_unsupported
        ModelAudioStatus.Interrupted -> Res.string.model_audio_interrupted
        else -> null
    }
    if (message != null) Hint(stringResource(message))
}

@Composable
private fun ProgressBar(percent: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(EolssuTheme.colors.surfaceStrong, RoundedCornerShape(2.dp)),
    ) {
        Box(Modifier.fillMaxWidth((percent / 100f).coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun RecordingStatus(state: PracticeUiState) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append(formatClock(state.elapsedMs / 1000))
                withStyle(SpanStyle(fontSize = 12.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)) {
                    append("  ")
                    append(stringResource(Res.string.practice_rec_time_detail, formatClock(state.totalSeconds.toDouble()), "${state.progressPercent.roundToInt()}%"))
                }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        MicLevel(state.micLevel)
    }
}

@Composable
private fun MicLevel(level: Float) {
    val colors = EolssuTheme.colors
    val off = level == 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(EolssuIcons.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
        Row(Modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(0.4f, 0.8f, 1f, 0.6f, 0.3f).forEach { f ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((level * f * 16).coerceAtLeast(2f).dp)
                        .background(if (off) colors.lineStrong else colors.rec, RoundedCornerShape(2.dp)),
                )
            }
        }
        Text(
            stringResource(if (off) Res.string.practice_mic_off else Res.string.practice_mic_on),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textMuted,
        )
    }
}

/** 연주 중 입력이 한동안 없을 때 마이크를 확인하라는 안내. */
@Composable
private fun NoInputHint() {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.recSoft, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(EolssuIcons.Alert, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.rec)
        Text(stringResource(Res.string.practice_no_input_hint), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.rec)
    }
}

/** 현재 박 표시: 대강마다 막대 하나, 첫 대강(강박)은 조금 두껍게. */
@Composable
private fun BeatBars(count: Int, current: Int) {
    val colors = EolssuTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(if (i == 0) 10.dp else 8.dp)
                    .background(if (i == current) colors.rec else colors.surfaceStrong, RoundedCornerShape(4.dp)),
            )
        }
    }
}

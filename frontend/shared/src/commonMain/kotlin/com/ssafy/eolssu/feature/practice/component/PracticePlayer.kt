package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.practice.ModelAudioStatus
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.model_audio_failed
import eolssu.shared.generated.resources.model_audio_interrupted
import eolssu.shared.generated.resources.model_audio_loading
import eolssu.shared.generated.resources.model_audio_unavailable
import eolssu.shared.generated.resources.model_audio_unsupported
import eolssu.shared.generated.resources.practice_hint_ready
import eolssu.shared.generated.resources.practice_hint_solo
import eolssu.shared.generated.resources.practice_hint_solo_only
import eolssu.shared.generated.resources.practice_listen_model
import eolssu.shared.generated.resources.practice_listen_model_resume
import eolssu.shared.generated.resources.practice_notice_eval
import eolssu.shared.generated.resources.practice_pause
import eolssu.shared.generated.resources.practice_player_label
import eolssu.shared.generated.resources.practice_recording_unsupported
import eolssu.shared.generated.resources.practice_seek_back
import eolssu.shared.generated.resources.practice_seek_back_desc
import eolssu.shared.generated.resources.practice_seek_forward
import eolssu.shared.generated.resources.practice_seek_forward_desc
import eolssu.shared.generated.resources.practice_start_eval
import eolssu.shared.generated.resources.practice_switch_to_eval
import eolssu.shared.generated.resources.practice_time
import org.jetbrains.compose.resources.stringResource

/** 플레이어가 한 번에 옮기는 시간(보드 −5초·+5초). */
const val PRACTICE_SEEK_MS = 5_000L

/**
 * 모범 연주 플레이어(`.player`, 준비·모범 연주 상태): 흰 카드에 ‘모범 연주 / 0:00 / 0:48’ 시간 줄 · 6dp 트랙과 흰 손잡이 ·
 * ‘−5초 · 재생(44dp 연두 원) · +5초’ · 안내 ‘모범 연주를 듣고 준비되면 시작하세요.’.
 * 음원을 불러오지 못하는 상태는 안내 자리에 그 사유를 보인다.
 *
 * @param compact ‘운지법과 함께 보기’의 한 줄 플레이어(`.paged-fingering .player`): 트랙·안내 없이 시간과 조절만.
 */
@Composable
fun PracticePlayerCard(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val playing = state.mode == PracticeMode.Model && state.playing
    val resumable = state.mode == PracticeMode.Model && !state.playing && state.modelAudioStatus in setOf(ModelAudioStatus.Paused, ModelAudioStatus.Interrupted)
    val time = stringResource(Res.string.practice_time, formatClock(state.elapsedMs / 1000), formatClock(state.totalSeconds.toDouble()))
    val playLabel = stringResource(
        when {
            playing -> Res.string.practice_pause
            resumable -> Res.string.practice_listen_model_resume
            else -> Res.string.practice_listen_model
        },
    )
    val canPlay = state.modelAudioStatus != ModelAudioStatus.Loading && state.modelAudioStatus != ModelAudioStatus.Unsupported
    val controls: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 24.dp)) {
            SeekButton(stringResource(Res.string.practice_seek_back), stringResource(Res.string.practice_seek_back_desc), compact) { actions.onSeek(-PRACTICE_SEEK_MS) }
            Surface(
                onClick = { if (playing) actions.onToggleModel() else actions.onStartModel() },
                enabled = canPlay,
                modifier = Modifier.size(44.dp).semantics { contentDescription = playLabel },
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (playing) EolssuIcons.Pause else EolssuIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            SeekButton(stringResource(Res.string.practice_seek_forward), stringResource(Res.string.practice_seek_forward_desc), compact) { actions.onSeek(PRACTICE_SEEK_MS) }
        }
    }
    if (compact) {
        Row(
            modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.lineSoft, shape).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(Res.string.practice_player_label), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.textSub)
                Text(time, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.textSub)
            }
            controls()
        }
        return
    }
    Column(
        modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.lineSoft, shape).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.practice_player_label), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp), color = colors.textSub)
            Text(time, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp), color = colors.textSub)
        }
        PlayerTrack(state.progressPercent / 100f, Modifier.padding(top = 12.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { controls() }
        val hint = when (state.modelAudioStatus) {
            ModelAudioStatus.Loading -> stringResource(Res.string.model_audio_loading)
            ModelAudioStatus.Unavailable -> stringResource(Res.string.model_audio_unavailable)
            ModelAudioStatus.Failed -> stringResource(Res.string.model_audio_failed)
            ModelAudioStatus.Unsupported -> stringResource(Res.string.model_audio_unsupported)
            ModelAudioStatus.Interrupted -> stringResource(Res.string.model_audio_interrupted)
            else -> stringResource(Res.string.practice_hint_ready)
        }
        Text(
            hint,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 18.7.sp),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** ‘−5초’·‘+5초’ 글자 버튼(`.player-controls button`: 12sp 500, 여백 8). */
@Composable
private fun SeekButton(text: String, description: String, compact: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = 44.dp)
            .padding(if (compact) 5.dp else 8.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Medium),
        color = EolssuTheme.colors.text,
    )
}

/** 6dp 트랙(`.player .track`): 연두 채움과 흰 손잡이(16dp, 2dp 먹색 테두리). */
@Composable
private fun PlayerTrack(fraction: Float, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val pct = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(colors.surfaceStrong, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(pct).height(6.dp).background(colors.accent, RoundedCornerShape(3.dp)))
        }
        Box(
            Modifier
                .offset(x = (maxWidth * pct - 8.dp).coerceIn(0.dp, maxWidth - 16.dp))
                .size(16.dp)
                .background(colors.surface, CircleShape)
                .border(2.dp, colors.text, CircleShape),
        )
    }
}

/**
 * 플레이어 아래 행동(보드 `.btn.primary` + `.notice`):
 * - 평가 연습: ‘연주 평가 시작’ + ‘평가를 시작하면 마이크로 연주를 듣고 음정·박자·음 길이를 확인해요.’
 * - 연습 모드·평가 가능 곡: ‘이 곡으로 평가하기’ + ‘점수 없이 악보를 보며 혼자 연습해요. 평가하려면 위 버튼으로 넘어가요.’
 * - 연습 모드·평가 불가 곡: 안내만 ‘이 곡은 AI 평가 데이터가 없어 연습만 할 수 있어요. …’
 * - 내 악보 연습: 평가 버튼도 안내도 없다(Practice-Mine).
 * 녹음을 지원하지 않는 환경은 안내 카드에 그 사실을 적고 버튼을 끈다.
 */
@Composable
fun PracticeCallToAction(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier, hideNotice: Boolean = false) {
    val song = state.song ?: return
    // 내 악보 연습(Practice-Mine)은 평가하지 않으므로 평가 버튼도 안내도 두지 않는다.
    if (state.mine) return
    val unsupported = !state.recordingSupported && (!state.solo || song.evaluable)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            !state.solo -> {
                EolssuButton(stringResource(Res.string.practice_start_eval), actions.onStartEval, icon = EolssuIcons.Mic, enabled = state.recordingSupported)
                // 운지법과 함께 보기에서는 평가 안내를 숨긴다(보드 `.paged-fingering .practice-demo-notice`).
                if (unsupported || !hideNotice) PracticeNotice(stringResource(if (unsupported) Res.string.practice_recording_unsupported else Res.string.practice_notice_eval))
            }
            song.evaluable -> {
                EolssuButton(stringResource(Res.string.practice_switch_to_eval), actions.onSwitchToEval, icon = EolssuIcons.Mic, enabled = state.recordingSupported)
                PracticeNotice(stringResource(if (unsupported) Res.string.practice_recording_unsupported else Res.string.practice_hint_solo))
            }
            else -> PracticeNotice(stringResource(Res.string.practice_hint_solo_only))
        }
    }
}

/** 안내 카드(`.notice`): 흰 바탕, 옅은 테두리, 반지름 10, 12sp 짙은 연두 글자. */
@Composable
fun PracticeNotice(text: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Text(
        text,
        modifier = modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.lineSoft, shape).padding(horizontal = 14.dp, vertical = 13.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 20.4.sp),
        color = colors.accentText,
    )
}

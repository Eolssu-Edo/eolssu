package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.feature.practice.ModelAudioStatus
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.PracticePlayerNotice
import com.ssafy.eolssu.feature.practice.playerNotice
import eolssu.shared.generated.resources.*
import com.ssafy.eolssu.feature.practice.JangdanStatus
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.model_audio_failed
import eolssu.shared.generated.resources.model_audio_interrupted
import eolssu.shared.generated.resources.model_audio_loading
import eolssu.shared.generated.resources.model_audio_unavailable
import eolssu.shared.generated.resources.model_audio_unsupported
import eolssu.shared.generated.resources.practice_hint_solo_only
import eolssu.shared.generated.resources.practice_listen_model
import eolssu.shared.generated.resources.practice_listen_model_resume
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
 * 연습 플레이어: 경과·전체 시간을 좌우에 배치하고 타임라인 아래 중앙에 재생 조작을 둔다.
 * ‘−5초 · 재생(48dp 연두 원) · +5초’ 아래 고정 칸에 로딩을 중앙 정렬한다.
 * 음원을 불러오지 못하는 상태는 안내 자리에 그 사유를 보인다.
 *
 * @param compact 운지 악보에서 여백과 재생 버튼을 조금 줄이며 같은 배치를 유지한다.
 */
@Composable
fun PracticePlayerCard(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val rhythm = state.mode == PracticeMode.Rhythm
    val playing = state.mode in setOf(PracticeMode.Model, PracticeMode.Rhythm) && state.playing
    val resumable = state.mode == PracticeMode.Model && !state.playing && state.modelAudioStatus in setOf(ModelAudioStatus.Paused, ModelAudioStatus.Interrupted)
    val time = stringResource(Res.string.practice_time, formatClock(state.elapsedMs / 1000), formatClock(state.totalSeconds.toDouble()))
    val playLabel = stringResource(
        when {
            playing -> Res.string.practice_pause
            rhythm -> Res.string.jangdan_practice_resume
            resumable -> Res.string.practice_listen_model_resume
            else -> Res.string.practice_listen_model
        },
    )
    val canPlay = if (rhythm) state.jangdanSupported && state.jangdanStatus != JangdanStatus.Loading
        else state.modelAudioStatus != ModelAudioStatus.Loading && state.modelAudioStatus != ModelAudioStatus.Unsupported
    val controls: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            SeekButton(stringResource(Res.string.practice_seek_back), stringResource(Res.string.practice_seek_back_desc), small = compact) { actions.onSeek(-PRACTICE_SEEK_MS) }
            Surface(
                onClick = { if (rhythm) actions.onToggleRhythm() else if (playing) actions.onToggleModel() else actions.onStartModel() },
                enabled = canPlay,
                modifier = Modifier.size(if (compact) 40.dp else 48.dp).semantics { contentDescription = playLabel },
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (playing) EolssuIcons.Pause else EolssuIcons.Play, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            SeekButton(stringResource(Res.string.practice_seek_forward), stringResource(Res.string.practice_seek_forward_desc), small = compact) { actions.onSeek(PRACTICE_SEEK_MS) }
        }
    }
    if (compact) {
        // 휴대폰 연습 화면: 시간·조작을 한 줄에 두고 막대·안내 칸을 낮춰 악보·운지 공간을 넓힌다.
        Column(
            modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.lineSoft, shape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(state.elapsedMs / 1000), modifier = Modifier.semantics { contentDescription = time },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.text)
                controls(Modifier.weight(1f))
                Text(formatClock(state.totalSeconds.toDouble()),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
            }
            SeekablePlayerTrack(state.progressPercent / 100f, actions.onSeekToFraction,
                enabled = state.eventCount > 0 && state.playerNotice != PracticePlayerNotice.Loading, height = 24.dp)
            PlayerNoticeArea(state.playerNotice, Modifier.fillMaxWidth().height(20.dp), mascot = 20.dp)
        }
        return
    }
    Column(
        modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.lineSoft, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().semantics { contentDescription = time },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(state.elapsedMs / 1000),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text)
            Text(formatClock(state.totalSeconds.toDouble()),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        }
        SeekablePlayerTrack(state.progressPercent / 100f, actions.onSeekToFraction,
            enabled = state.eventCount > 0 && state.playerNotice != PracticePlayerNotice.Loading)
        controls(Modifier.fillMaxWidth())
        PlayerNoticeArea(state.playerNotice, Modifier.fillMaxWidth().height(40.dp))
    }
}

/** 상태가 바뀌어도 이 칸의 너비·높이는 바뀌지 않는다. */
@Composable
private fun PlayerNoticeArea(notice: PracticePlayerNotice, modifier: Modifier = Modifier, mascot: Dp = 40.dp) {
    val message = when (notice) {
        PracticePlayerNotice.None -> ""
        PracticePlayerNotice.Loading -> stringResource(Res.string.practice_player_loading)
        PracticePlayerNotice.Failed -> stringResource(Res.string.practice_player_failed)
        PracticePlayerNotice.Unavailable -> stringResource(Res.string.practice_player_unavailable)
        PracticePlayerNotice.Unsupported -> stringResource(Res.string.practice_player_unsupported)
        PracticePlayerNotice.Interrupted -> stringResource(Res.string.practice_player_interrupted)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (notice == PracticePlayerNotice.Loading) PracticeLoadingMascot(Modifier.size(mascot))
            if (message.isNotEmpty()) Text(
                message, modifier = Modifier.semantics { contentDescription = message },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = EolssuTheme.colors.textMuted,
            )
        }
    }
}

/** ‘−5초’·‘+5초’ 글자 버튼(`.player-controls button`: 12sp 500, 여백 8). */
@Composable
private fun SeekButton(text: String, description: String, small: Boolean = false, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.size(if (small) 40.dp else 44.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = colors.surfaceStrong,
        contentColor = colors.text,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/** 6dp 트랙(`.player .track`): 연두 채움과 흰 손잡이(16dp, 2dp 먹색 테두리). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekablePlayerTrack(fraction: Float, onSeek: (Float) -> Unit, enabled: Boolean, height: Dp = 40.dp) {
    var draggedFraction by remember { mutableStateOf<Float?>(null) }
    // 끄는 동안에는 재생 위치 보고와 분리하고, 놓을 때 한 번만 음원을 이동한다.
    Slider(
        value = draggedFraction ?: fraction.coerceIn(0f, 1f),
        onValueChange = { draggedFraction = it },
        onValueChangeFinished = { draggedFraction?.let(onSeek); draggedFraction = null },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(height),
        thumb = {},
        track = { PlayerTrack(draggedFraction ?: fraction) },
    )
}

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
 * - 평가 준비: ‘연주 평가 시작’.
 * - 일반 연습·평가 가능 곡: ‘연주 평가 시작’으로 평가를 바로 시작한다.
 * - 연습 모드·평가 불가 곡: 안내만 ‘이 곡은 AI 평가 데이터가 없어 연습만 할 수 있어요. …’
 * - 내 악보 연습: 평가 버튼도 안내도 없다(Practice-Mine).
 * 녹음을 지원하지 않는 환경은 안내 카드에 그 사실을 적고 버튼을 끈다.
 */
@Composable
fun PracticeCallToAction(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier, hideNotice: Boolean = false) {
    val song = state.song ?: return
    val unsupported = !state.recordingSupported && (!state.solo || song.evaluable)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PracticeModeSwitch(state, actions)
        when {
            state.mine -> Unit
            !state.solo -> {
                EolssuButton(stringResource(Res.string.practice_start_eval), actions.onStartEval, icon = EolssuIcons.Mic, enabled = state.recordingSupported)
                // 운지법과 함께 보기에서는 평가 안내를 숨긴다(보드 `.paged-fingering .practice-demo-notice`).
                if (unsupported) PracticeNotice(stringResource(Res.string.practice_recording_unsupported))
            }
            song.evaluable -> {
                EolssuButton(stringResource(Res.string.practice_start_eval), actions.onSwitchToEval, icon = EolssuIcons.Mic, enabled = state.recordingSupported)
                if (unsupported) PracticeNotice(stringResource(Res.string.practice_recording_unsupported))
            }
            else -> PracticeNotice(stringResource(Res.string.practice_hint_solo_only))
        }
    }
}

/** 하나만 선택하는 두 칸. 모드 선택은 소리를 재생하지 않는다. */
@Composable
private fun PracticeModeSwitch(state: PracticeUiState, actions: PracticeBottomActions) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, colors.line, shape).selectableGroup()) {
        listOf(
            Triple(stringResource(Res.string.practice_player_label), state.mode != PracticeMode.Rhythm, actions.onSelectModel),
            Triple(stringResource(Res.string.jangdan_practice_start), state.mode == PracticeMode.Rhythm, actions.onSelectRhythm),
        ).forEachIndexed { index, (label, selected, select) ->
            val enabled = index == 0 || state.jangdanSupported
            Box(
                Modifier.weight(1f).background(if (selected) colors.primary else colors.surface)
                    .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = select)
                    .heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                    color = if (!enabled) colors.textDisabled else if (selected) colors.onPrimary else colors.text)
            }
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

/** Also shown when a saved ON preference cannot actually produce accompaniment. */
@Composable
internal fun jangdanHint(state: PracticeUiState): String = stringResource(when {
    state.song?.jangdanPlayback == null -> Res.string.jangdan_unverified
    state.jangdanStatus == JangdanStatus.Loading -> Res.string.jangdan_loading
    state.jangdanStatus == JangdanStatus.Failed -> Res.string.jangdan_failed
    state.jangdanStatus == JangdanStatus.Interrupted -> Res.string.jangdan_interrupted
    !state.jangdanSupported || state.jangdanStatus == JangdanStatus.Unsupported -> Res.string.jangdan_unsupported
    state.jangdanStatus == JangdanStatus.Playing -> Res.string.jangdan_playing
    else -> Res.string.jangdan_ready
})

package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
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
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.PlaybackLoadingOverlay
import com.ssafy.eolssu.feature.practice.ModelAudioStatus
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.PracticePlayerNotice
import com.ssafy.eolssu.feature.practice.playerNotice
import com.ssafy.eolssu.feature.practice.PracticePersonalAudio
import com.ssafy.eolssu.feature.practice.personalAudioControl
import com.ssafy.eolssu.feature.practice.stepUi
import eolssu.shared.generated.resources.*
import com.ssafy.eolssu.feature.practice.JangdanStatus
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.model_audio_failed
import eolssu.shared.generated.resources.model_audio_interrupted
import eolssu.shared.generated.resources.model_audio_loading
import eolssu.shared.generated.resources.model_audio_unavailable
import eolssu.shared.generated.resources.model_audio_unsupported
import eolssu.shared.generated.resources.practice_solo_only
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
 * 트랙 아래에 ‘−5초 · 재생 · +5초’를 둔다. 음원을 불러오지 못하는 상태는 안내 자리에 그 사유를 보이고,
 * 불러오는 중은 카드 대신 화면 가운데에 띄운다([PracticePlaybackLoadingOverlay]).
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
    val personalAudio = state.personalAudioControl
    val canSeek = personalAudio == null && state.eventCount > 0 && state.playerNotice != PracticePlayerNotice.Loading
    val controls: @Composable (Modifier) -> Unit = if (personalAudio != null) { modifier ->
        Box(modifier, contentAlignment = Alignment.Center) {
            val label = when (personalAudio) {
                PracticePersonalAudio.Checking -> Res.string.practice_sheet_audio_checking
                PracticePersonalAudio.Downloading -> Res.string.practice_sheet_audio_downloading
                PracticePersonalAudio.Failed -> Res.string.practice_sheet_audio_retry
                PracticePersonalAudio.VersionChanged -> Res.string.practice_sheet_audio_version_changed
                else -> Res.string.practice_sheet_audio_download
            }
            EolssuButton(
                stringResource(label),
                actions.onDownloadSheetAudio,
                enabled = personalAudio == PracticePersonalAudio.Missing || personalAudio == PracticePersonalAudio.Failed,
                small = true,
            )
        }
    } else { rowModifier ->
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
                enabled = canSeek, height = 24.dp)
            // 로딩은 화면 가운데에 띄우므로 트랙 아래 칸은 실패·중단 사유가 있을 때만 둔다.
            if (state.playerNotice.inCard) PlayerNoticeArea(state.playerNotice, Modifier.fillMaxWidth().height(20.dp))
        }
        return
    }
    Column(
        modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.lineSoft, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().semantics { contentDescription = time },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(state.elapsedMs / 1000),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text)
            PlayerNoticeArea(state.playerNotice, Modifier.weight(1f).height(32.dp))
            Text(formatClock(state.totalSeconds.toDouble()),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
        }
        SeekablePlayerTrack(state.progressPercent / 100f, actions.onSeekToFraction,
            enabled = canSeek)
        controls(Modifier.fillMaxWidth())
    }
}

/** 카드 안 안내 칸에 보일 상태. 불러오는 중은 화면 가운데 막([PracticePlaybackLoadingOverlay])이 맡는다. */
private val PracticePlayerNotice.inCard: Boolean
    get() = this != PracticePlayerNotice.None && this != PracticePlayerNotice.Loading

/**
 * 장단·모범 연주를 불러오는 동안 화면 가운데에 띄우는 표시. 연주 재개 3·2·1([BeatCountdownOverlay])과 같은 바탕 막·위치에
 * 같은 크기(150dp)의 장단 타기 그림과 ‘로딩 중’을 둔다. 누름은 막지 않아 뒤로 가기·모드 전환을 그대로 쓸 수 있다.
 * 플레이어 카드가 보이는 상태(시작 전·모범 연주·장단 연습, 한 음씩 제외)에서만 띄운다.
 */
@Composable
fun PracticePlaybackLoadingOverlay(state: PracticeUiState, modifier: Modifier = Modifier) {
    val visible = state.mode in setOf(PracticeMode.Ready, PracticeMode.Model, PracticeMode.Rhythm) &&
        !state.stepUi && state.playerNotice == PracticePlayerNotice.Loading
    if (visible) PlaybackLoadingOverlay(modifier)
}

/** 상태가 바뀌어도 이 칸의 너비·높이는 바뀌지 않는다. 불러오는 중은 비워 둔다. */
@Composable
private fun PlayerNoticeArea(notice: PracticePlayerNotice, modifier: Modifier = Modifier) {
    val message = when (notice) {
        PracticePlayerNotice.None, PracticePlayerNotice.Loading -> ""
        PracticePlayerNotice.Failed -> stringResource(Res.string.practice_player_failed)
        PracticePlayerNotice.Unavailable -> stringResource(Res.string.practice_player_unavailable)
        PracticePlayerNotice.PersonalAudioUnavailable -> stringResource(Res.string.practice_sheet_audio_unavailable)
        PracticePlayerNotice.PersonalDownloadFailed -> stringResource(Res.string.practice_sheet_audio_download_failed)
        PracticePlayerNotice.PersonalScoreChanged -> stringResource(Res.string.practice_sheet_audio_reopen)
        PracticePlayerNotice.EmbeddedJangdan -> stringResource(Res.string.practice_sheet_audio_with_jangdan)
        PracticePlayerNotice.Unsupported -> stringResource(Res.string.practice_player_unsupported)
        PracticePlayerNotice.Interrupted -> stringResource(Res.string.practice_player_interrupted)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

/** 연습 플레이어 트랙. 구간 연습 플레이어(S15P31E102-776)도 같은 모양을 쓴다. */
@Composable
internal fun PlayerTrack(fraction: Float, modifier: Modifier = Modifier) {
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
 * 플레이어 아래 행동(보드 `.btn.primary`):
 * - 평가 준비: ‘연주 평가 시작’.
 * - 일반 연습·평가 가능 곡: ‘연주 평가 시작’으로 평가를 바로 시작한다.
 * - 연습 모드·평가 불가 곡: 꺼진 버튼에 ‘연습만 가능해요’를 적는다.
 * - 내 악보 연습·기초부터 배우기 곡 연습: 평가 버튼도 안내도 없다(Practice-Mine).
 * 녹음을 지원하지 않는 환경은 버튼을 끄고 그 아래 회색 글자로 이유를 적는다.
 */
@Composable
fun PracticeCallToAction(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier, hideNotice: Boolean = false) {
    val song = state.song ?: return
    val unsupported = stringResource(Res.string.practice_recording_unsupported).takeIf { !state.recordingSupported }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 한 음씩을 켜면 모범 연주·장단으로 연습 고르기를 숨긴다.
        if (!state.stepUi) PracticeModeSwitch(state, actions)
        when {
            state.mine || state.learn -> Unit
            !state.solo -> PracticeEvalButton(actions.onStartEval, enabled = state.recordingSupported, caption = unsupported)
            song.evaluable -> PracticeEvalButton(actions.onSwitchToEval, enabled = state.recordingSupported, caption = unsupported)
            // 평가 가능 곡과 같은 자리에 꺼진 버튼을 두고, 버튼 글자로 이유를 적는다.
            else -> PracticeEvalButton({}, enabled = false, caption = null, label = stringResource(Res.string.practice_solo_only))
        }
    }
}

/** ‘연주 평가 시작’ 버튼. [caption]이 있으면 버튼 바로 아래 가운데에 작은 회색 글자로 적는다. */
@Composable
private fun PracticeEvalButton(
    onClick: () -> Unit,
    enabled: Boolean,
    caption: String?,
    label: String = stringResource(Res.string.practice_start_eval),
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EolssuButton(label, onClick, icon = EolssuIcons.Mic, enabled = enabled)
        if (caption != null) {
            Text(
                caption,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = EolssuTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
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

package com.ssafy.eolssu.feature.practice.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.PlaybackActionButton
import com.ssafy.eolssu.core.ui.component.PlaybackControlRow
import com.ssafy.eolssu.core.ui.component.PlaybackLoadingOverlay
import com.ssafy.eolssu.core.ui.component.PlaybackPositionSlider
import com.ssafy.eolssu.core.ui.component.PlaybackSectionSpacing
import com.ssafy.eolssu.core.ui.component.PlaybackSkipGroup
import com.ssafy.eolssu.core.ui.component.PlaybackTimeText
import com.ssafy.eolssu.core.ui.component.PlaybackTimeline
import com.ssafy.eolssu.core.ui.component.formatClock
import com.ssafy.eolssu.core.ui.component.playbackElapsedText
import com.ssafy.eolssu.feature.practice.JangdanStatus
import com.ssafy.eolssu.feature.practice.ModelAudioStatus
import com.ssafy.eolssu.feature.practice.PracticeMode
import com.ssafy.eolssu.feature.practice.PracticePersonalAudio
import com.ssafy.eolssu.feature.practice.PracticePlayerNotice
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.modelSeekBuffering
import com.ssafy.eolssu.feature.practice.personalAudioControl
import com.ssafy.eolssu.feature.practice.playerElapsedSeconds
import com.ssafy.eolssu.feature.practice.playerFraction
import com.ssafy.eolssu.feature.practice.playerNotice
import com.ssafy.eolssu.feature.practice.playerTotalSeconds
import com.ssafy.eolssu.feature.practice.stepUi
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 플레이어가 한 번에 옮기는 시간(↺5·↻5). */
const val PRACTICE_SEEK_MS = 5_000L

/**
 * 연습 플레이어(S15P31E102-873). 연주 기록 상세 하단 재생 영역과 같은 구조로, 휴대폰·태블릿이 같다.
 * - 위: 모범 연주·장단 연습 고르기([PracticeModeSwitch]).
 * - 가운데: 흰 카드에 시간 줄과 재생 위치 막대([PlaybackTimeline]). 음원을 쓸 수 없는 사유는 막대 아래에 적는다.
 * - 아래: ↺5 · 재생 · ↻5 묶음과 그 오른쪽 평가 버튼(기록 상세 ‘구간 연습’과 같은 자리·높이).
 *   평가 버튼이 없는 내 악보·기초부터 배우기는 재생 묶음만 가운데에 둔다.
 * 불러오는 중은 카드 대신 화면 가운데에 띄운다([PracticePlaybackLoadingOverlay]).
 * 한 음씩이 켜지면 플레이어 대신 두 칸([PracticeStepPanels])과 너비를 채운 평가 버튼을 둔다.
 * 두 칸은 플레이어보다 낮아서, 그대로 두면 한 음씩을 켜고 끌 때마다 악보·운지 칸이 남은 높이에 맞춰 커졌다 작아진다.
 * 그래서 한 음씩이 켜져도 플레이어 높이를 그대로 비워 두고 두 칸을 그 아래쪽에 붙인다(S15P31E102-1017).
 */
@Composable
fun PracticePlayer(state: PracticeUiState, actions: PracticeBottomActions, modifier: Modifier = Modifier) {
    val eval = practiceEvalAction(state, actions)
    if (state.stepUi) {
        ReserveHeightOf(
            reserve = { PracticePlayerContent(state, actions, eval, step = false) },
            modifier = modifier.fillMaxWidth(),
        ) {
            PracticePlayerContent(state, actions, eval, step = true)
        }
    } else {
        PracticePlayerContent(state, actions, eval, step = false, modifier = modifier)
    }
}

/**
 * [reserve]를 재어 그 높이만큼 자리를 잡고, [content]를 그 안의 아래쪽에 둔다. [reserve]는 그리지 않는다.
 * [content]가 더 높으면 그 높이를 쓴다.
 */
@Composable
private fun ReserveHeightOf(reserve: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val reserved = subcompose(ReserveSlot.Reserve, reserve).maxOfOrNull { it.measure(loose).height } ?: 0
        val placeables = subcompose(ReserveSlot.Content, content).map { it.measure(loose) }
        val height = maxOf(reserved, placeables.maxOfOrNull { it.height } ?: 0)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, height) { placeables.forEach { it.place(0, height - it.height) } }
    }
}

private enum class ReserveSlot { Reserve, Content }

@Composable
private fun PracticePlayerContent(
    state: PracticeUiState,
    actions: PracticeBottomActions,
    eval: PracticeEvalAction?,
    step: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PlaybackSectionSpacing)) {
        if (step) {
            PracticeStepPanels(state, actions, Modifier.padding(bottom = 4.dp))
            if (eval != null) EolssuButton(eval.label, eval.onClick, icon = EolssuIcons.Mic, enabled = eval.enabled, qaTarget = QaTarget.PRACTICE_PLAYER_BUTTON_01)
        } else {
            PracticeModeSwitch(state, actions)
            PracticePlayerTimeline(state, actions)
            PracticePlayerControls(state, actions, eval)
        }
        eval?.caption?.let { caption ->
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

/** 시간 줄 · 재생 위치 막대 · (있으면) 음원 사유. */
@Composable
private fun PracticePlayerTimeline(state: PracticeUiState, actions: PracticeBottomActions) {
    val time = stringResource(Res.string.practice_time, formatClock(state.playerElapsedSeconds), formatClock(state.playerTotalSeconds))
    val canSeek = state.personalAudioControl == null && state.eventCount > 0 && state.playerNotice != PracticePlayerNotice.Loading
    var draggedFraction by remember { mutableStateOf<Float?>(null) }
    PlaybackTimeline {
        PlaybackTimeText(
            playbackElapsedText(formatClock(state.playerElapsedSeconds)),
            formatClock(state.playerTotalSeconds),
            Modifier.semantics { contentDescription = time },
        )
        // 끄는 동안에는 재생 위치 보고와 분리하고, 놓을 때 한 번만 음원을 이동한다.
        PlaybackPositionSlider(
            value = draggedFraction ?: state.playerFraction,
            onValueChange = UserActionLog.change(QaTarget.PRACTICE_PLAYER_01, { draggedFraction = it }),
            onValueChangeFinished = { draggedFraction?.let(actions.onSeekToFraction); draggedFraction = null },
            enabled = canSeek,
            label = stringResource(Res.string.record_detail_seek),
        )
        // 로딩은 화면 가운데에 띄우므로 막대 아래 칸은 실패·중단 사유가 있을 때만 둔다.
        if (state.playerNotice.inCard) PlayerNoticeArea(state.playerNotice, Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }
}

/** 카드 아래 줄: 재생 묶음(내 악보 음원을 받기 전·받는 중이면 그 안내)과 오른쪽 평가 버튼. */
@Composable
private fun PracticePlayerControls(state: PracticeUiState, actions: PracticeBottomActions, eval: PracticeEvalAction?) {
    val rhythm = state.mode == PracticeMode.Rhythm
    // 재생 중 ↺5·↻5로 새 자리를 불러오는 동안에도 ‘멈춤’ 버튼을 그대로 둔다(S15P31E102-807).
    val playing = state.mode in setOf(PracticeMode.Model, PracticeMode.Rhythm) && state.playing || state.modelSeekBuffering
    val resumable = state.mode == PracticeMode.Model && !state.playing &&
        state.modelAudioStatus in setOf(ModelAudioStatus.Paused, ModelAudioStatus.Interrupted)
    val playLabel = stringResource(
        when {
            playing -> Res.string.practice_pause
            rhythm -> Res.string.jangdan_practice_resume
            resumable -> Res.string.practice_listen_model_resume
            else -> Res.string.practice_listen_model
        },
    )
    val canPlay = if (rhythm) state.jangdanSupported && state.jangdanStatus != JangdanStatus.Loading
        else (state.modelAudioStatus != ModelAudioStatus.Loading || state.modelSeekBuffering) && state.modelAudioStatus != ModelAudioStatus.Unsupported
    val personalAudio = state.personalAudioControl
    PlaybackControlRow(horizontalArrangement = if (eval == null) Arrangement.Center else Arrangement.spacedBy(12.dp)) {
        if (personalAudio != null) {
            PersonalAudioControl(personalAudio, actions.onDownloadSheetAudio, Modifier.weight(1f))
        } else {
            PlaybackSkipGroup(
                playing = playing,
                playEnabled = canPlay,
                playLabel = playLabel,
                onTogglePlay = { if (rhythm) actions.onToggleRhythm() else if (playing) actions.onToggleModel() else actions.onStartModel() },
                backLabel = stringResource(Res.string.practice_seek_back_desc),
                onBack = { actions.onSeek(-PRACTICE_SEEK_MS) },
                forwardLabel = stringResource(Res.string.practice_seek_forward_desc),
                onForward = { actions.onSeek(PRACTICE_SEEK_MS) },
                skipEnabled = state.eventCount > 0,
            )
        }
        // 기록 상세 ‘구간 연습’처럼 아이콘 없이 둔다. 360dp 휴대폰에서도 ‘구간 재평가 시작’이 44dp 높이 안에서 한 줄로 들어간다.
        if (eval != null) PlaybackActionButton(eval.label, eval.onClick, Modifier.weight(1f), enabled = eval.enabled)
    }
}

/**
 * 내 악보 음원을 아직 받지 않았거나 준비 중일 때 재생 묶음 자리(높이 52dp)에 두는 안내.
 * 받을 수 있으면(없음·실패) 같은 높이의 ‘음원 받기’ 버튼이다.
 */
@Composable
private fun PersonalAudioControl(personalAudio: PracticePersonalAudio, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (personalAudio) {
            PracticePersonalAudio.Checking -> Res.string.practice_sheet_audio_checking
            PracticePersonalAudio.Queued -> Res.string.practice_sheet_audio_queued
            PracticePersonalAudio.Preparing -> Res.string.practice_sheet_audio_preparing
            PracticePersonalAudio.Downloading -> Res.string.practice_sheet_audio_downloading
            PracticePersonalAudio.Unsupported -> Res.string.practice_player_unsupported
            PracticePersonalAudio.UnsupportedPitches -> Res.string.practice_sheet_audio_support
            PracticePersonalAudio.Failed -> Res.string.practice_sheet_audio_retry
            PracticePersonalAudio.VersionChanged -> Res.string.practice_sheet_audio_version_changed
            else -> Res.string.practice_sheet_audio_download
        },
    )
    Box(modifier.height(52.dp), contentAlignment = Alignment.Center) {
        if (personalAudio == PracticePersonalAudio.Missing || personalAudio == PracticePersonalAudio.Failed) {
            PlaybackActionButton(label, onDownload, Modifier.fillMaxWidth())
        } else if (personalAudio in PersonalAudioWorking) {
            PersonalAudioProgress(label, Modifier.fillMaxWidth())
        } else {
            Text(
                label,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = EolssuTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 확인·대기·생성·다운로드처럼 기다리는 상태. 버튼 자리에 눌린 버튼 모양으로 회전 표시와 한 줄 문구를 둔다(S15P31E102-1039). */
private val PersonalAudioWorking = setOf(
    PracticePersonalAudio.Checking, PracticePersonalAudio.Queued, PracticePersonalAudio.Preparing, PracticePersonalAudio.Downloading,
)

@Composable
private fun PersonalAudioProgress(label: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    // 받기 버튼을 누른 채로 기다리는 느낌: 옅은 연두 바탕이 위쪽 안 그림자로 꺼져 보이게 한다.
    Surface(
        modifier.fillMaxHeight()
            .innerShadow(shape, Shadow(radius = 5.dp, color = colors.accentText.copy(alpha = 0.25f), offset = DpOffset(0.dp, 2.dp)))
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = shape, color = colors.accentTintStrong,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = colors.accentText, trackColor = colors.accentTintLine, strokeWidth = 2.5.dp)
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.accentText, maxLines = 1)
        }
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

/** 막대 아래 음원 사유(12sp, 두 줄까지). */
@Composable
private fun PlayerNoticeArea(notice: PracticePlayerNotice, modifier: Modifier = Modifier) {
    val message = when (notice) {
        PracticePlayerNotice.None, PracticePlayerNotice.Loading -> ""
        PracticePlayerNotice.Failed -> stringResource(Res.string.practice_player_failed)
        PracticePlayerNotice.Unavailable -> stringResource(Res.string.practice_player_unavailable)
        PracticePlayerNotice.PersonalAudioUnavailable -> stringResource(Res.string.practice_sheet_audio_unavailable)
        PracticePlayerNotice.PersonalDownloadFailed -> stringResource(Res.string.practice_sheet_audio_download_failed)
        PracticePlayerNotice.PersonalUnsupportedPitches -> stringResource(Res.string.practice_sheet_audio_support_edit)
        PracticePlayerNotice.PersonalScoreChanged -> stringResource(Res.string.practice_sheet_audio_reopen)
        PracticePlayerNotice.PersonalAudioReady -> stringResource(Res.string.practice_sheet_audio_ready)
        PracticePlayerNotice.EmbeddedJangdan -> stringResource(Res.string.practice_sheet_audio_with_jangdan)
        PracticePlayerNotice.Unsupported -> stringResource(Res.string.practice_player_unsupported)
        PracticePlayerNotice.Interrupted -> stringResource(Res.string.practice_player_interrupted)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (message.isNotEmpty()) Text(
            message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = EolssuTheme.colors.textMuted,
        )
    }
}

/** 평가 버튼 하나의 글자·동작·사용 가능 여부와 그 아래 회색 안내. */
private class PracticeEvalAction(val label: String, val onClick: () -> Unit, val enabled: Boolean, val caption: String?)

/**
 * 플레이어 오른쪽 평가 버튼:
 * - 평가 준비: ‘연주 평가 시작’.
 * - 일반 연습·평가 가능 곡: ‘연주 평가 시작’으로 평가를 바로 시작한다.
 * - 구간 연습: ‘구간 재평가 시작’으로 같은 구간·속도의 구간 재평가에 간다(S15P31E102-794).
 * - 연습 모드·평가 불가 곡: 꺼진 버튼에 ‘연습만 가능해요’를 적는다.
 * - 내 악보 연습·기초부터 배우기 곡 연습: 평가 버튼도 안내도 없다(Practice-Mine, null).
 * 녹음을 지원하지 않는 환경은 버튼을 끄고 그 아래 회색 글자로 이유를 적는다.
 */
@Composable
private fun practiceEvalAction(state: PracticeUiState, actions: PracticeBottomActions): PracticeEvalAction? {
    val song = state.song ?: return null
    val unsupported = stringResource(Res.string.practice_recording_unsupported).takeIf { !state.recordingSupported }
    val start = stringResource(Res.string.practice_start_eval)
    val sectionStart = stringResource(Res.string.weak_loop_evaluate_start)
    val soloOnly = stringResource(Res.string.practice_solo_only)
    return when {
        state.mine || state.learn -> null
        state.sectionPractice != null -> PracticeEvalAction(sectionStart, actions.onStartSectionEval, state.recordingSupported, unsupported)
        !state.solo -> PracticeEvalAction(start, actions.onStartEval, state.recordingSupported, unsupported)
        song.evaluable -> PracticeEvalAction(start, actions.onSwitchToEval, state.recordingSupported, unsupported)
        // 평가 가능 곡과 같은 자리에 꺼진 버튼을 두고, 버튼 글자로 이유를 적는다.
        else -> PracticeEvalAction(soloOnly, {}, false, null)
    }
}

/**
 * 하나만 선택하는 두 칸. 모드 선택은 소리를 재생하지 않는다.
 * 연습곡 고르기 탭과 같은 모양이다: 회색 틀(모서리 14dp, 안쪽 4dp) 안에서 고른 칸만 흰 알약(모서리 10dp)으로 띄운다(S15P31E102-849).
 */
@Composable
private fun PracticeModeSwitch(state: PracticeUiState, actions: PracticeBottomActions) {
    val colors = EolssuTheme.colors
    val frame = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().background(colors.surfaceStrong, frame).padding(4.dp).selectableGroup()) {
        listOf(
            Triple(stringResource(Res.string.practice_player_label), state.mode != PracticeMode.Rhythm, actions.onSelectModel),
            Triple(stringResource(Res.string.jangdan_practice_start), state.mode == PracticeMode.Rhythm, actions.onSelectRhythm),
        ).forEachIndexed { index, (label, selected, select) ->
            val enabled = index == 0 || state.jangdanSupported
            Box(
                Modifier.weight(1f).clip(pill).background(if (selected) colors.surface else Color.Transparent)
                    .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = UserActionLog.click(QaTarget.PRACTICE_PLAYER_02, select))
                    .heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                    color = if (!enabled) colors.textDisabled else if (selected) colors.text else colors.textSub)
            }
        }
    }
}

/** Also shown when a saved ON preference cannot actually produce accompaniment. */
@Composable
internal fun jangdanHint(state: PracticeUiState): String = stringResource(when {
    state.song?.hasAccompaniment != true -> Res.string.jangdan_unverified
    state.jangdanStatus == JangdanStatus.Loading -> Res.string.jangdan_loading
    state.jangdanStatus == JangdanStatus.Failed -> Res.string.jangdan_failed
    state.jangdanStatus == JangdanStatus.Interrupted -> Res.string.jangdan_interrupted
    !state.jangdanSupported || state.jangdanStatus == JangdanStatus.Unsupported -> Res.string.jangdan_unsupported
    state.jangdanStatus == JangdanStatus.Playing -> Res.string.jangdan_playing
    else -> Res.string.jangdan_ready
})

package com.ssafy.eolssu.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.rememberReferenceAudioPlayer
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.guide.component.ReferenceAudioControls
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/** 연습 운지 팝업에서도 운지 가이드와 같은 기본 예시음을 재생한다. */
@Composable
fun FingeringBasicAudio(instrument: GuideInstrument, note: FingeringNote, onPlaying: (Boolean) -> Unit) {
    val selection = ReferenceAudioSelection.forNote(instrument, note)
    val audio = rememberReferenceAudioController(instrument, resetKey = selection)
    val clip = audio.state.clipFor(selection, ReferenceAudioKind.Basic)
    val playing = clip != null && audio.playback.sourceId == clip.assetId &&
        audio.playback.status == AudioPlaybackStatus.Playing
    val reportPlaying by rememberUpdatedState(onPlaying)
    LaunchedEffect(playing) { reportPlaying(playing) }
    DisposableEffect(Unit) { onDispose { reportPlaying(false) } }
    ReferenceAudioControls(selection, audio, basicOnly = true)
}

/** 기본 예시음 재생기: 누를 때 부를 [play]와 지금 재생 상태 [playback](S15P31E102-1006). */
class BasicNotePlayer(val play: (FingeringNote) -> Unit, val playback: AudioPlaybackState)

/**
 * 연습 '한 음씩'의 현재 정간 칸을 누를 때 운지 가이드와 같은 기본 예시음을 낸다(S15P31E102-717).
 * 운지 팝업([FingeringBasicAudio])은 닫힐 때 목록을 비우므로, 목록과 재생기를 따로 둔다. 같은 음을 다시 누르면 멈춘다.
 * [maxPlayMs]가 있으면 소리가 나기 시작한 뒤 그만큼만 들려주고 멈춘다. 불러오는 시간은 세지 않는다(S15P31E102-1006).
 */
@Composable
fun rememberBasicNotePlayer(instrument: GuideInstrument, maxPlayMs: Long? = null): BasicNotePlayer {
    val audio = rememberReferenceAudioController(instrument, resetKey = null, viewModel = koinViewModel(key = "step-note-audio"))
    val play by rememberUpdatedState(audio.play)
    val stop by rememberUpdatedState(audio.stop)
    val onPlay = remember(instrument) { { note: FingeringNote -> play(ReferenceAudioSelection.forNote(instrument, note), ReferenceAudioKind.Basic) } }
    val playingSource = audio.playback.sourceId.takeIf { audio.playback.status == AudioPlaybackStatus.Playing }
    LaunchedEffect(playingSource, maxPlayMs) {
        if (playingSource == null || maxPlayMs == null) return@LaunchedEffect
        delay(maxPlayMs)
        stop()
    }
    return BasicNotePlayer(onPlay, audio.playback)
}

/** 목록과 플레이어는 화면이 소유하고, Lazy 카드에는 값과 동작만 전달한다. */
class ReferenceAudioController(
    val state: ReferenceAudioUiState,
    val playback: AudioPlaybackState,
    val play: (ReferenceAudioSelection, ReferenceAudioKind) -> Unit,
    val retry: () -> Unit,
    val stop: () -> Unit = {},
)

@Composable
fun rememberReferenceAudioController(
    instrument: GuideInstrument,
    resetKey: Any?,
    viewModel: ReferenceAudioViewModel = koinViewModel(),
): ReferenceAudioController {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player = rememberReferenceAudioPlayer()
    val playback by player.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.stop() }
    LaunchedEffect(instrument, resetKey) {
        player.stop()
        viewModel.open(instrument)
    }
    DisposableEffect(viewModel, player) { onDispose { viewModel.dismiss(); player.release() } }
    return ReferenceAudioController(
        state = if (state.instrument == instrument) state else ReferenceAudioUiState(instrument),
        playback = playback,
        play = { selection, kind -> viewModel.uiState.value.clipFor(selection, kind)?.let { toggleReferenceAudio(player, it) } },
        retry = { player.stop(); viewModel.retry() },
        stop = { player.stop() },
    )
}

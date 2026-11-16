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
import com.ssafy.eolssu.data.song.ReferenceAudioClip
import com.ssafy.eolssu.feature.guide.component.ReferenceAudioControls
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

/**
 * 연습 '한 음씩'의 현재 정간 칸을 누를 때 운지 가이드와 같은 기본 예시음을 낸다(S15P31E102-717).
 * 운지 팝업([FingeringBasicAudio])은 닫힐 때 목록을 비우므로, 목록과 재생기를 따로 둔다. 같은 음을 다시 누르면 멈춘다.
 */
@Composable
fun rememberBasicNotePlayer(instrument: GuideInstrument): (FingeringNote) -> Unit {
    val audio = rememberReferenceAudioController(instrument, resetKey = null, viewModel = koinViewModel(key = "step-note-audio"))
    val play by rememberUpdatedState(audio.play)
    return remember(instrument) { { note -> play(ReferenceAudioSelection.forNote(instrument, note), ReferenceAudioKind.Basic) } }
}

/**
 * 목록과 플레이어는 화면이 소유하고, Lazy 카드에는 값과 동작만 전달한다.
 * [prefetch]는 보이는 예시음을 미리 받고, [stopClips]는 그 예시음 중 하나가 재생 중일 때만 멈춘다(S15P31E102-1005).
 */
class ReferenceAudioController(
    val state: ReferenceAudioUiState,
    val playback: AudioPlaybackState,
    val play: (ReferenceAudioSelection, ReferenceAudioKind) -> Unit,
    val retry: () -> Unit,
    val prefetch: suspend (ReferenceAudioClip) -> Unit = {},
    val stopClips: (Set<String>) -> Unit = {},
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
        play = { selection, kind ->
            viewModel.uiState.value.clipFor(selection, kind)?.let { clip ->
                val current = player.state.value
                // 받아 둔 파일로 재생에 실패했으면 이번에는 원격 주소로 다시 받는다.
                if (current.sourceId == clip.assetId && current.status == AudioPlaybackStatus.Failed) viewModel.forgetLocalFile(clip)
                val file = viewModel.localFile(clip)
                toggleReferenceAudio(player, clip, file)
                if (file == null) viewModel.keepForNext(clip)
            }
        },
        retry = { player.stop(); viewModel.retry() },
        prefetch = viewModel::prefetch,
        stopClips = { ids -> if (player.state.value.sourceId in ids) player.stop() },
    )
}

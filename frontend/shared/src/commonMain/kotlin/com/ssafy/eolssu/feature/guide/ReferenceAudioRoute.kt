package com.ssafy.eolssu.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.rememberReferenceAudioPlayer
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import org.koin.compose.viewmodel.koinViewModel

/** 목록과 플레이어는 화면이 소유하고, Lazy 카드에는 값과 동작만 전달한다. */
class ReferenceAudioController(
    val state: ReferenceAudioUiState,
    val playback: AudioPlaybackState,
    val play: (ReferenceAudioSelection, ReferenceAudioKind) -> Unit,
    val retry: () -> Unit,
    val openSource: () -> Unit,
    val sourceLinkFailed: Boolean,
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
    val uriHandler = LocalUriHandler.current
    var sourceLinkFailed by remember(instrument) { mutableStateOf(false) }
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
        openSource = {
            player.stop()
            sourceLinkFailed = false
            try { uriHandler.openUri("https://www.gugak.go.kr/digitaleum/front/monotone/list.do") }
            catch (_: Exception) { sourceLinkFailed = true }
        },
        sourceLinkFailed = sourceLinkFailed,
    )
}

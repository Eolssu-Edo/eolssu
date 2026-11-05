package com.ssafy.eolssu.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.audio.rememberReferenceAudioPlayer
import com.ssafy.eolssu.feature.guide.component.ReadingCatalogDialog
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.TimeSource

@Composable
fun ReadingRoute(onBack: () -> Unit, initialPage: ReadingPage = ReadingPage.Intro) {
    val player = koinInject<SoundPlayer>()
    val scope = rememberCoroutineScope()
    val session = remember(player, scope) {
        val origin = TimeSource.Monotonic.markNow()
        ReadingAudioSession(player, scope) { origin.elapsedNow().inWholeMilliseconds }
    }
    val audio by session.state.collectAsStateWithLifecycle()
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.stop() }
    DisposableEffect(session) { onDispose { session.stop() } }
    ReadingScreen(
        onBack = { session.stop(); onBack() },
        initialPage = initialPage,
        audio = audio,
        onPlayAudio = session::toggle,
        onStopAudio = session::stop,
        onOpenCatalog = { session.stop(); catalogOpen = true },
    )
    if (catalogOpen) ReadingCatalogRoute(onClose = { catalogOpen = false })
}

@Composable
private fun ReadingCatalogRoute(onClose: () -> Unit, viewModel: ReadingCatalogViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player = rememberReferenceAudioPlayer()
    val playback by player.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.stop() }
    DisposableEffect(player) { onDispose { player.release() } }
    ReadingCatalogDialog(
        state = state,
        playback = playback,
        playbackAvailable = player.available,
        onPlayJangdan = { id ->
            viewModel.uiState.value.detail?.takeIf { it.jangdanId == id }?.let { toggleJangdanAudio(player, it) }
        },
        onJangdansRetry = viewModel::retryJangdans,
        onSymbolsRetry = viewModel::retrySymbols,
        onSelectJangdan = { player.stop(); viewModel.selectJangdan(it) },
        onDetailRetry = { player.stop(); viewModel.retryDetail() },
        onClose = { player.stop(); onClose() },
    )
}

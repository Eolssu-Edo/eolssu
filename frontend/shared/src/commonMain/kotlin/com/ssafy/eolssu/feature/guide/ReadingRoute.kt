package com.ssafy.eolssu.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.SoundPlayer
import org.koin.compose.koinInject
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
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.stop() }
    DisposableEffect(session) { onDispose { session.stop() } }
    ReadingScreen(
        onBack = { session.stop(); onBack() },
        initialPage = initialPage,
        audio = audio,
        onPlayAudio = session::toggle,
        onStopAudio = session::stop,
    )
}

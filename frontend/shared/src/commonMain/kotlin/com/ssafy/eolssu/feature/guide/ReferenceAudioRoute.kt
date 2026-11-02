package com.ssafy.eolssu.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.feature.guide.component.ReferenceAudioDialog
import org.koin.compose.viewmodel.koinViewModel
import com.ssafy.eolssu.core.audio.*
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle

/** 사용자 클릭으로 앱 안에서 재생하며, 창 닫기·백그라운드 전환 시 해제한다. */
@Composable
fun ReferenceAudioRoute(
    selection: ReferenceAudioSelection,
    onClose: () -> Unit,
    viewModel: ReferenceAudioViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player = rememberReferenceAudioPlayer()
    val playback by player.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.stop() }
    LaunchedEffect(state.query) { player.stop() }
    LaunchedEffect(selection) { viewModel.open(selection) }
    DisposableEffect(viewModel) { onDispose { viewModel.dismiss(); player.release() } }
    ReferenceAudioDialog(
        state = if (state.selection == selection) state else ReferenceAudioUiState(selection),
        playback = playback,
        onPlay = { clip ->
            if (playback.sourceId == clip.assetId && playback.status in setOf(AudioPlaybackStatus.Playing, AudioPlaybackStatus.Loading)) player.stop()
            else player.load(AudioSource(clip.assetId, clip.soundUrl), 0, 1f)
        },
        onShowAll = viewModel::showAllNotes,
        onCategory = viewModel::selectCategory,
        onRetry = viewModel::retry,
        onClose = onClose,
    )
}

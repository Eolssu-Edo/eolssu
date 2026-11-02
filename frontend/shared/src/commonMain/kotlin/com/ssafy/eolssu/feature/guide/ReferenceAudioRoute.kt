package com.ssafy.eolssu.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
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
    val uriHandler = LocalUriHandler.current
    var sourceLinkFailed by remember(selection) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.stop() }
    LaunchedEffect(state.query) { player.stop() }
    LaunchedEffect(selection) { viewModel.open(selection) }
    DisposableEffect(viewModel) { onDispose { viewModel.dismiss(); player.release() } }
    ReferenceAudioDialog(
        state = if (state.selection == selection) state else ReferenceAudioUiState(selection),
        playback = playback,
        onPlay = { clip -> toggleReferenceAudio(player, clip) },
        onShowAll = viewModel::showAllNotes,
        onCategory = viewModel::selectCategory,
        onRetry = viewModel::retry,
        onOpenSource = {
            player.stop()
            sourceLinkFailed = false
            try {
                uriHandler.openUri("https://www.gugak.go.kr/digitaleum/front/monotone/list.do")
            } catch (_: Exception) {
                sourceLinkFailed = true
            }
        },
        sourceLinkFailed = sourceLinkFailed,
        onClose = onClose,
    )
}

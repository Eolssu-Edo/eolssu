package com.ssafy.eolssu.feature.song.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.audio.rememberReferenceAudioPlayer
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.sheetedit_msg_audio_busy
import eolssu.shared.generated.resources.sheetedit_msg_audio_failed
import eolssu.shared.generated.resources.sheetedit_msg_audio_in_progress
import eolssu.shared.generated.resources.sheetedit_msg_audio_invalid
import eolssu.shared.generated.resources.sheetedit_msg_audio_link_failed
import eolssu.shared.generated.resources.sheetedit_msg_audio_needs_save
import eolssu.shared.generated.resources.sheetedit_msg_audio_not_found
import eolssu.shared.generated.resources.sheetedit_msg_audio_not_ready
import eolssu.shared.generated.resources.sheetedit_msg_audio_ready
import eolssu.shared.generated.resources.sheetedit_preview_needs_audio
import eolssu.shared.generated.resources.sheetedit_msg_audio_requested
import eolssu.shared.generated.resources.sheetedit_msg_audio_too_long
import eolssu.shared.generated.resources.sheetedit_msg_audio_unknown
import eolssu.shared.generated.resources.sheetedit_msg_draft
import eolssu.shared.generated.resources.sheetedit_msg_saved
import eolssu.shared.generated.resources.sheet_save_failed
import eolssu.shared.generated.resources.sheet_save_conflict
import eolssu.shared.generated.resources.sheet_save_too_large
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-04 연결. 편집기와 같은 [viewModel]을 받는다. 뒤로 가기와 경고 창의 '빈 정간 채우기'는 [onBack]으로 편집기에 돌아간다.
 * 저장·임시저장은 서버 응답을 확인한 뒤 알린다. 음원(SHT-12·13)은 이 화면의 플레이어로 재생하고, 파일 저장은 서명 URL을 기기 브라우저로 연다.
 */
@Composable
fun EditPreviewRoute(viewModel: SheetEditViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.documentLoading || state.documentFailed) {
        SheetDocumentNotice(state.documentLoading, onBack, viewModel::loadDocument)
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val player = rememberReferenceAudioPlayer()
    val playback by player.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val messages = mapOf(
        SheetEditMessage.SaveFailed to stringResource(Res.string.sheet_save_failed),
        SheetEditMessage.SaveConflict to stringResource(Res.string.sheet_save_conflict),
        SheetEditMessage.SaveTooLarge to stringResource(Res.string.sheet_save_too_large),
        SheetEditMessage.Draft to stringResource(Res.string.sheetedit_msg_draft),
        SheetEditMessage.Saved to stringResource(Res.string.sheetedit_msg_saved),
        SheetEditMessage.AudioNeedsSave to stringResource(Res.string.sheetedit_msg_audio_needs_save),
        SheetEditMessage.AudioRequested to stringResource(Res.string.sheetedit_msg_audio_requested),
        SheetEditMessage.AudioReady to stringResource(Res.string.sheetedit_msg_audio_ready),
        SheetEditMessage.AudioFailed to stringResource(Res.string.sheetedit_msg_audio_failed),
        SheetEditMessage.AudioTooLong to stringResource(Res.string.sheetedit_msg_audio_too_long),
        SheetEditMessage.AudioInvalid to stringResource(Res.string.sheetedit_msg_audio_invalid),
        SheetEditMessage.AudioInProgress to stringResource(Res.string.sheetedit_msg_audio_in_progress),
        SheetEditMessage.AudioNotReady to stringResource(Res.string.sheetedit_msg_audio_not_ready),
        SheetEditMessage.AudioNotFound to stringResource(Res.string.sheetedit_msg_audio_not_found),
        SheetEditMessage.AudioBusy to stringResource(Res.string.sheetedit_msg_audio_busy),
        SheetEditMessage.AudioStatusUnknown to stringResource(Res.string.sheetedit_msg_audio_unknown),
        SheetEditMessage.AudioLinkFailed to stringResource(Res.string.sheetedit_msg_audio_link_failed),
        SheetEditMessage.PreviewNeedsAudio to stringResource(Res.string.sheetedit_preview_needs_audio),
        SheetEditMessage.ComingSoon to stringResource(Res.string.common_coming_soon),
    )
    LaunchedEffect(state.message) {
        val text = state.message?.let(messages::get) ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
    }
    // 미리듣기와 음원 재생은 이 화면에서만 한다. 음원 생성 상태 조회는 ViewModel이 이어 간다.
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    EditPreviewScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        playback = playback,
        previewPlaying = viewModel.previewPlaybackId()?.let { id ->
            playback.sourceId == id && playback.status in setOf(AudioPlaybackStatus.Playing, AudioPlaybackStatus.Loading)
        } == true,
        onBack = onBack,
        onTogglePlay = { scope.launch { toggleSheetAudio(player, viewModel, preview = true) } },
        onToggleJangdan = viewModel::toggleWithJangdan,
        audio = SheetAudioActions(
            onRequest = viewModel::requestAudio,
            onRefresh = { viewModel.refreshAudio() },
            onTogglePlayback = { scope.launch { toggleSheetAudio(player, viewModel) } },
            onSave = { kind ->
                scope.launch {
                    val file = viewModel.audioFile(kind) ?: return@launch
                    try { uriHandler.openUri(file.url) } catch (_: Exception) { viewModel.reportAudioLinkFailed() }
                }
            },
        ),
        onSaveDraft = viewModel::saveDraft,
        onSave = viewModel::requestSave,
        onFillEmpty = {
            viewModel.fillEmpty()
            onBack()
        },
        onCloseWarning = viewModel::closeWarning,
    )
}

/** 같은 파일이 재생·일시정지 중이면 그 상태만 바꾸고, 아니면 (만료됐으면 새로 받은) 서명 URL로 처음부터 튼다. */
private suspend fun toggleSheetAudio(player: AudioPlayer, viewModel: SheetEditViewModel, preview: Boolean = false) {
    val current = player.state.value
    val loadedId = if (preview) viewModel.previewPlaybackId() else viewModel.uiState.value.audio.latest?.audio?.playbackId
    if (loadedId != null && current.sourceId == loadedId) {
        when (current.status) {
            AudioPlaybackStatus.Playing -> { player.pause(); return }
            AudioPlaybackStatus.Paused -> { player.resume(1f); return }
            AudioPlaybackStatus.Loading -> { player.stop(); return }
            else -> Unit
        }
    }
    val file = (if (preview) viewModel.previewAudioFile() else viewModel.audioFile(SheetAudioKind.M4a)) ?: return
    player.load(AudioSource(file.playbackId, file.url), 0, 1f)
}

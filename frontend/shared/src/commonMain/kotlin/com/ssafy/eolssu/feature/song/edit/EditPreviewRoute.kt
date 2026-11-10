package com.ssafy.eolssu.feature.song.edit

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.referenceSeekTarget
import com.ssafy.eolssu.core.audio.rememberReferenceAudioPlayer
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.sheetedit_msg_audio_busy
import eolssu.shared.generated.resources.sheetedit_msg_audio_failed
import eolssu.shared.generated.resources.sheetedit_msg_audio_in_progress
import eolssu.shared.generated.resources.sheetedit_msg_audio_invalid
import eolssu.shared.generated.resources.sheetedit_audio_instrument_choose
import eolssu.shared.generated.resources.sheetedit_msg_audio_link_failed
import eolssu.shared.generated.resources.sheetedit_msg_audio_downloaded
import eolssu.shared.generated.resources.sheetedit_audio_expired
import eolssu.shared.generated.resources.sheetedit_msg_audio_needs_save
import eolssu.shared.generated.resources.sheetedit_msg_audio_not_found
import eolssu.shared.generated.resources.sheetedit_msg_audio_not_ready
import eolssu.shared.generated.resources.sheetedit_msg_audio_ready
import eolssu.shared.generated.resources.sheetedit_preview_needs_audio
import eolssu.shared.generated.resources.sheetedit_preview_needs_practice_audio
import eolssu.shared.generated.resources.sheetedit_msg_audio_requested
import eolssu.shared.generated.resources.sheetedit_msg_audio_too_long
import eolssu.shared.generated.resources.sheetedit_msg_audio_unknown
import eolssu.shared.generated.resources.sheetedit_msg_draft
import eolssu.shared.generated.resources.sheetedit_msg_saved
import eolssu.shared.generated.resources.sheet_save_failed
import eolssu.shared.generated.resources.sheet_save_conflict
import eolssu.shared.generated.resources.sheet_save_too_large
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

/**
 * EDIT-04 연결. 편집기와 같은 [viewModel]을 받는다. 뒤로 가기와 경고 창의 '빈 정간 채우기'는 [onBack]으로 편집기에 돌아간다.
 * 악보 저장은 서버에 반영하고, 음원은 내려받아 기기에 저장한 파일을 재생한다.
 */
@Composable
fun EditPreviewRoute(viewModel: SheetEditViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.documentLoading || state.documentFailed) {
        SheetDocumentNotice(state.documentLoading, onBack, viewModel::loadDocument, state.practiceVersionChanged)
        return
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val player = rememberReferenceAudioPlayer()
    val playback by player.state.collectAsStateWithLifecycle()
    val previewId = viewModel.previewPlaybackId()
    val showsAudioExport = viewModel.showsAudioExport
    val savedAudioId = if (showsAudioExport) state.playableDeviceAudio?.playbackId else previewId
    var previewStarted by remember { mutableStateOf(false) }
    LaunchedEffect(previewId, savedAudioId) {
        val loadedId = player.state.value.sourceId
        if (loadedId != null && (loadedId != savedAudioId || previewStarted && loadedId != previewId)) {
            previewStarted = false
            player.stop()
        }
    }
    val pendingDownload = (state.audio as? SheetAudioUi.Ready)?.audio?.audio
    LaunchedEffect(pendingDownload) {
        val file = pendingDownload ?: return@LaunchedEffect
        delay((file.urlExpiresAt - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0))
        viewModel.expireAudioDownload(file)
    }
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
        SheetEditMessage.AudioInstrumentRequired to stringResource(Res.string.sheetedit_audio_instrument_choose),
        SheetEditMessage.AudioInProgress to stringResource(Res.string.sheetedit_msg_audio_in_progress),
        SheetEditMessage.AudioNotReady to stringResource(Res.string.sheetedit_msg_audio_not_ready),
        SheetEditMessage.AudioNotFound to stringResource(Res.string.sheetedit_msg_audio_not_found),
        SheetEditMessage.AudioBusy to stringResource(Res.string.sheetedit_msg_audio_busy),
        SheetEditMessage.AudioStatusUnknown to stringResource(Res.string.sheetedit_msg_audio_unknown),
        SheetEditMessage.AudioLinkFailed to stringResource(Res.string.sheetedit_msg_audio_link_failed),
        SheetEditMessage.AudioDownloaded to stringResource(Res.string.sheetedit_msg_audio_downloaded),
        SheetEditMessage.AudioExpired to stringResource(Res.string.sheetedit_audio_expired),
        SheetEditMessage.PreviewNeedsAudio to stringResource(
            if (showsAudioExport) Res.string.sheetedit_preview_needs_audio else Res.string.sheetedit_preview_needs_practice_audio,
        ),
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
        previewProgress = playback.previewProgress(previewId, previewStarted),
        previewPlaying = previewId?.let { id ->
            playback.sourceId == id && playback.status in setOf(AudioPlaybackStatus.Playing, AudioPlaybackStatus.Loading)
        } == true,
        onBack = onBack,
        onTogglePlay = {
            previewStarted = viewModel.previewPlaybackId() != null
            toggleSheetAudio(player, viewModel, preview = true)
        },
        onSeekPreview = { position ->
            if (player.state.value.previewProgress(viewModel.previewPlaybackId(), previewStarted) != null) {
                player.seekTo(position)
            }
        },
        onToggleJangdan = viewModel::toggleWithJangdan,
        audio = if (showsAudioExport) SheetAudioActions(
            onSelectInstrument = viewModel::selectAudioInstrument,
            onRetryInstruments = viewModel::loadAudioInstrumentOptions,
            onRequest = viewModel::requestAudio,
            onRefresh = { viewModel.refreshAudio() },
            onTogglePlayback = {
                previewStarted = false
                toggleSheetAudio(player, viewModel)
            },
            onSave = {
                if (viewModel.canSaveAudioOnDevice) {
                    viewModel.saveAudioOnDevice()
                } else {
                    viewModel.downloadableAudio()?.let { file ->
                        try { uriHandler.openUri(file.url) } catch (_: Exception) { viewModel.reportAudioLinkFailed() }
                    }
                }
            },
        ) else null,
        onSaveDraft = viewModel::saveDraft,
        onSave = viewModel::requestSave,
        onFillEmpty = {
            viewModel.fillEmpty()
            onBack()
        },
        onCloseWarning = viewModel::closeWarning,
    )
}

private fun AudioPlaybackState.previewProgress(id: String?, started: Boolean): AudioPlaybackState? =
    takeIf { started && id != null && sourceId == id && referenceSeekTarget(positionMs) != null }

private fun toggleSheetAudio(player: AudioPlayer, viewModel: SheetEditViewModel, preview: Boolean = false) {
    val source = viewModel.devicePlayback(preview) ?: return
    val current = player.state.value
    if (current.sourceId == source.id) {
        when (current.status) {
            AudioPlaybackStatus.Playing -> { player.pause(); return }
            AudioPlaybackStatus.Paused -> { player.resume(1f); return }
            AudioPlaybackStatus.Loading -> { player.stop(); return }
            else -> Unit
        }
    }
    player.load(source, 0, 1f)
}

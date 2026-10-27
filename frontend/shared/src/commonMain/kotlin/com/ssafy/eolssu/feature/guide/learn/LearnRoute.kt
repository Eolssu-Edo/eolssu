package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.LISTEN_HIGHLIGHT_MILLIS
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_listen_pending
import eolssu.shared.generated.resources.learn_listen_model_pending
import eolssu.shared.generated.resources.learn_practice_pending
import eolssu.shared.generated.resources.learn_reset_message
import eolssu.shared.generated.resources.learn_song_missing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/*
 * LEARN-02 연결. 단계 목록과 단계 상세는 화면 이동 그래프 `learn`에 묶인 하나의 [LearnViewModel]을 받는다.
 */

/** 단계 목록. 단계를 고르면 [onOpenStage]로 단계 상세에 간다. */
@Composable
fun LearnListRoute(
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenStage: (Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LearnListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleDropdown = viewModel::toggleDropdown,
        onDismissDropdown = viewModel::dismissDropdown,
        onSelectInstrument = viewModel::selectInstrument,
        onResetDone = {
            viewModel.resetDone()
            scope.launch { snackbarHostState.show(getString(Res.string.learn_reset_message, getString(state.instrument.label))) }
        },
        onOpenStage = { stage ->
            viewModel.dismissDropdown()
            onOpenStage(stage)
        },
    )
}

/**
 * 단계 상세. 이전·다음 단계는 [onOpenStage]로 바꾸고, 마지막 단계를 확인하면 [onBack]으로 목록에 돌아간다.
 * 3~7단계의 단계 연습 화면은 아직 없어 '준비 중' 안내를, 8단계 '연습 시작'은 [onOpenSongList]로 곡 목록을 연다.
 */
@Composable
fun LearnStageRoute(
    stageNumber: Int,
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenStage: (Int) -> Unit,
    onOpenFingering: (GuideInstrument, Blow) -> Unit,
    onOpenReading: () -> Unit,
    onOpenSong: (songId: String) -> Unit,
    onOpenSongList: () -> Unit,
) {
    val stage = learnStageOf(stageNumber) ?: return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (suspend () -> String) -> Unit = { message -> scope.launch { snackbarHostState.show(message()) } }

    LaunchedEffect(state.playing) {
        val playing = state.playing ?: return@LaunchedEffect
        delay(LISTEN_HIGHLIGHT_MILLIS)
        viewModel.finishListening(playing)
    }

    LearnStageScreen(
        state = state,
        stage = stage,
        snackbarHostState = snackbarHostState,
        actions = LearnStageActions(
            onBack = onBack,
            onOpenStage = onOpenStage,
            onToggleDone = viewModel::toggleDone,
            onConfirm = { n ->
                viewModel.confirm(n)
                stageAfterConfirm(n)?.let(onOpenStage) ?: onBack()
            },
            onStartPractice = { n ->
                if (n == 8) onOpenSongList() else notify { getString(Res.string.learn_practice_pending, n, stage.title) }
            },
            onListenModel = { notify { getString(Res.string.learn_listen_model_pending) } },
            onListen = { note ->
                viewModel.listen(note.hanja)
                notify { getString(Res.string.guide_listen_pending) }
            },
            onOpenFingering = { blow -> onOpenFingering(state.instrument, blow) },
            onOpenReading = onOpenReading,
            onOpenSong = { song ->
                val songId = state.songIds[song.title]
                if (songId != null) onOpenSong(songId) else notify { getString(Res.string.learn_song_missing, song.title) }
            },
        ),
    )
}

/** 앞 안내를 닫고 새 안내를 띄운다. */
private suspend fun SnackbarHostState.show(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}

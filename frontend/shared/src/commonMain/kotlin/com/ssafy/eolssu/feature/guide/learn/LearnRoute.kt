package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.guide.Blow
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.LISTEN_HIGHLIGHT_MILLIS
import com.ssafy.eolssu.feature.guide.ReadingPage
import com.ssafy.eolssu.feature.guide.component.label
import com.ssafy.eolssu.feature.guide.layout.LearnExpandedLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_listen_pending
import eolssu.shared.generated.resources.learn_listen_model_pending
import eolssu.shared.generated.resources.learn_practice_pending
import eolssu.shared.generated.resources.learn_reset_message
import eolssu.shared.generated.resources.learn_song_missing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/*
 * LEARN-02 연결. 단계 목록과 단계 상세는 화면 이동 그래프 `learn`에 묶인 하나의 [LearnViewModel]을 받는다.
 * 태블릿 가로·넓은 웹 창([WindowWidthClass.Expanded])에서는 단계 목록 경로 하나가 목록과 상세를 한 화면에 보여주고,
 * 그 밖의 너비에서는 목록과 상세가 다른 경로다. 창 너비가 바뀌면 와이어프레임처럼 보던 단계를 이어서 보여준다.
 */

/** 단계 상세에서 다른 화면으로 가는 이동. 목록·상세 경로가 함께 받는다. */
class LearnNavigation(
    val onOpenFingering: (GuideInstrument, Blow) -> Unit,
    /** 정간보 쉽게 읽기를 그 단계에 맞는 쪽으로 펼쳐 연다. */
    val onOpenReading: (ReadingPage) -> Unit,
    val onOpenSong: (songId: String) -> Unit,
    val onOpenSongList: () -> Unit,
)

/**
 * 단계 목록. 단계를 고르면 [onOpenStage]로 단계 상세에 간다.
 * 가로 배치에서는 고른 단계를 오른쪽 칸에 보여주고, 세로로 바뀌었을 때 고른 단계가 있으면 그 단계 상세로 간다.
 */
@Composable
fun LearnListRoute(
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenStage: (Int) -> Unit,
    navigation: LearnNavigation,
    widthClass: WindowWidthClass = currentWindowLayout().widthClass,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val twoPane = widthClass == WindowWidthClass.Expanded

    LaunchedEffect(twoPane, state.selectedStage) {
        val selected = state.selectedStage
        if (!twoPane && selected != null) {
            viewModel.selectStage(null)
            onOpenStage(selected)
        }
    }

    val listActions = LearnListActions(
        onToggleDropdown = viewModel::toggleDropdown,
        onDismissDropdown = viewModel::dismissDropdown,
        onSelectInstrument = viewModel::selectInstrument,
        onResetDone = {
            viewModel.resetDone()
            scope.launch { snackbarHostState.show(getString(Res.string.learn_reset_message, getString(state.instrument.label))) }
        },
        onOpenStage = { stage ->
            if (twoPane) {
                viewModel.selectStage(stage)
            } else {
                viewModel.dismissDropdown()
                onOpenStage(stage)
            }
        },
    )

    if (twoPane) {
        ListenHighlightEffect(state.playing, viewModel)
        val stage = learnStageOf(state.shownStage) ?: return
        LearnExpandedLayout(
            state = state,
            stage = stage,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            listActions = listActions,
            stageActions = learnStageActions(
                state = state,
                stage = stage,
                viewModel = viewModel,
                notify = snackbarHostState.notifier(scope),
                onBack = {},
                onOpenStage = viewModel::selectStage,
                onConfirmed = viewModel::selectStage,
                onToggleDone = viewModel::toggleShownDone,
                navigation = navigation,
            ),
        )
    } else {
        LearnListScreen(state, widthClass, snackbarHostState, onBack, listActions)
    }
}

/**
 * 단계 상세. 이전·다음 단계는 [onOpenStage]로 바꾸고, 마지막 단계를 확인하면 [onBack]으로 목록에 돌아간다.
 * 3~7단계의 단계 연습 화면은 아직 없어 '준비 중' 안내를, 8단계 '연습 시작'은 곡 목록을 연다.
 * 창이 가로 배치 너비가 되면 이 단계를 고른 채 [onShowInList]로 단계 목록(목록 + 상세 한 화면)에 돌아간다.
 */
@Composable
fun LearnStageRoute(
    stageNumber: Int,
    viewModel: LearnViewModel,
    onBack: () -> Unit,
    onOpenStage: (Int) -> Unit,
    onShowInList: () -> Unit,
    navigation: LearnNavigation,
    widthClass: WindowWidthClass = currentWindowLayout().widthClass,
) {
    val stage = learnStageOf(stageNumber) ?: return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val twoPane = widthClass == WindowWidthClass.Expanded

    LaunchedEffect(twoPane) {
        if (twoPane) {
            viewModel.selectStage(stageNumber)
            onShowInList()
        }
    }
    ListenHighlightEffect(state.playing, viewModel)

    LearnStageScreen(
        state = state,
        stage = stage,
        widthClass = widthClass,
        snackbarHostState = snackbarHostState,
        actions = learnStageActions(
            state = state,
            stage = stage,
            viewModel = viewModel,
            notify = snackbarHostState.notifier(scope),
            onBack = onBack,
            onOpenStage = onOpenStage,
            onConfirmed = { next -> next?.let(onOpenStage) ?: onBack() },
            onToggleDone = viewModel::toggleDone,
            navigation = navigation,
        ),
    )
}

/**
 * 단계 상세의 동작. [onConfirmed]는 '확인했어요 · 다음'으로 완료 표시한 뒤 갈 단계(마지막이면 null)를 받는다.
 */
private fun learnStageActions(
    state: LearnUiState,
    stage: LearnStage,
    viewModel: LearnViewModel,
    notify: (suspend () -> String) -> Unit,
    onBack: () -> Unit,
    onOpenStage: (Int) -> Unit,
    onConfirmed: (Int?) -> Unit,
    onToggleDone: (Int) -> Unit,
    navigation: LearnNavigation,
) = LearnStageActions(
    onBack = onBack,
    onOpenStage = onOpenStage,
    onToggleDone = onToggleDone,
    onConfirm = { n ->
        viewModel.confirm(n)
        onConfirmed(stageAfterConfirm(n))
    },
    onStartPractice = { n ->
        if (n == 8) navigation.onOpenSongList() else notify { getString(Res.string.learn_practice_pending, n, stage.title) }
    },
    onListenModel = { notify { getString(Res.string.learn_listen_model_pending) } },
    onListen = { note ->
        viewModel.listen(note.hanja)
        notify { getString(Res.string.guide_listen_pending) }
    },
    onOpenFingering = { blow -> navigation.onOpenFingering(state.instrument, blow) },
    onOpenReading = navigation.onOpenReading,
    onOpenSong = { song ->
        val songId = state.songIds[song.title]
        if (songId != null) navigation.onOpenSong(songId) else notify { getString(Res.string.learn_song_missing, song.title) }
    },
    onRetrySongs = viewModel::retrySongs,
)

/** '음 듣기'를 누른 버튼의 강조를 잠시 뒤 끈다. */
@Composable
private fun ListenHighlightEffect(playing: String?, viewModel: LearnViewModel) {
    LaunchedEffect(playing) {
        val hanja = playing ?: return@LaunchedEffect
        delay(LISTEN_HIGHLIGHT_MILLIS)
        viewModel.finishListening(hanja)
    }
}

private fun SnackbarHostState.notifier(scope: CoroutineScope): (suspend () -> String) -> Unit =
    { message -> scope.launch { show(message()) } }

/** 앞 안내를 닫고 새 안내를 띄운다. */
private suspend fun SnackbarHostState.show(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}

package com.ssafy.eolssu.feature.practice

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.practice.component.PracticeBottomActions
import com.ssafy.eolssu.feature.practice.component.PracticeDialogActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_switched_to_eval
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * PRACTICE-01 연결.
 *
 * @param solo 연습곡 선택(SONG-03)에서 들어온 연습 모드. 평가 없이 혼자 연습한다.
 * @param showSwitchedNotice 연습 모드에서 평가로 바꿔 들어왔으면 안내를 띄운다.
 * @param onSwitchToEval 연습 모드에서 같은 곡의 평가 연습으로 바꾼다.
 * @param onEvaluate 연주를 마치고 '평가하기'. 연습 화면은 준비 상태로 돌려 두고 분석 화면으로 간다.
 * @param onMicDenied 마이크 안내에서 '나중에'. 평가는 마이크 없이 할 수 없어 이전 화면으로 돌아가며 안내를 띄운다.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PracticeRoute(
    songId: String,
    solo: Boolean,
    showSwitchedNotice: Boolean,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onSwitchToEval: () -> Unit,
    onEvaluate: ((songId: String, bpm: Int) -> Unit)?,
    onMicDenied: () -> Unit,
    viewModel: PracticeViewModel = koinViewModel(key = "$songId-$solo") { parametersOf(songId, solo) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showSwitchedNotice) {
        if (showSwitchedNotice) snackbarHostState.showSnackbar(getString(Res.string.practice_switched_to_eval))
    }
    // 앱을 벗어나면(전화·다른 앱) 연주를 끊긴 것으로 처리한다.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onLeaveScreen() }

    val back = { if (viewModel.onBack()) onBack() }
    // 기기 뒤로 가기도 화면의 뒤로 가기와 같게: 연주 중이면 확인 창을 띄운다. 태블릿 가로처럼 패널이 화면 안에 열려 있으면 패널부터 닫는다.
    // 휴대폰의 하단 시트·확인 창은 자기 창에서 뒤로 가기를 먼저 받는다.
    @Suppress("DEPRECATION") // ui-backhandler 1.12의 BackHandler. 대신할 NavigationEventHandler는 아직 의존성에 없다.
    BackHandler {
        if (state.panel != null) viewModel.closePanel() else back()
    }

    PracticeScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        actions = PracticeScreenActions(
            onBack = back,
            onOpenSettings = { viewModel.openPanel(PracticePanel.Settings) },
            onSelectEvent = { viewModel.selectEvent(it.index) },
            onUserScroll = viewModel::stopFollowing,
            onFollowNow = viewModel::followNow,
            onCancelCountdown = viewModel::cancelCountdown,
            bottom = PracticeBottomActions(
                onStartModel = viewModel::startModel,
                onToggleModel = viewModel::toggleModel,
                onRestartModel = viewModel::restartModel,
                onStopModel = viewModel::stopModel,
                onStartEval = viewModel::requestEvaluation,
                onSwitchToEval = {
                    viewModel.stopAll()
                    onSwitchToEval()
                },
                onAskRestart = viewModel::askRestart,
                onAskAbort = viewModel::askAbort,
            ),
            panel = PracticePanelActions(
                onClose = viewModel::closePanel,
                onOpen = viewModel::openPanel,
                onDraft = viewModel::updateDraft,
                onApplyNotation = viewModel::applyNotation,
                onApplyJangdan = viewModel::applyJangdan,
                onApplyTempo = viewModel::applyTempo,
                onToggleBigScore = viewModel::toggleBigScore,
                onMoveFingering = viewModel::moveFingering,
                onListen = { viewModel.listen(state.draft.note) },
                onTogglePreview = viewModel::togglePreview,
            ),
            dialog = PracticeDialogActions(
                onGrantMic = viewModel::grantMicAndStart,
                onDenyMic = {
                    viewModel.stopAll()
                    viewModel.closePopup()
                    onMicDenied()
                },
                onClose = viewModel::closePopup,
                onGoHome = {
                    viewModel.stopAll()
                    onGoHome()
                },
                onResume = viewModel::resumeRecording,
                onEvaluate = {
                    val bpm = state.bpm
                    viewModel.toReady()
                    onEvaluate?.invoke(songId, bpm)
                },
                onRestartNow = viewModel::restartNow,
                onExit = {
                    viewModel.stopAll()
                    onBack()
                },
                onToReady = viewModel::toReady,
            ),
        ),
    )
}

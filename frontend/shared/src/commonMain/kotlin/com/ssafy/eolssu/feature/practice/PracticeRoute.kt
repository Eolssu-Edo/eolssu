package com.ssafy.eolssu.feature.practice

import androidx.compose.runtime.CompositionLocalProvider
import com.ssafy.eolssu.core.diagnostics.LocalPerformanceVisit
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.model.Song
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.feature.practice.component.PracticeBottomActions
import com.ssafy.eolssu.feature.practice.component.PracticeDialogActions
import com.ssafy.eolssu.feature.practice.component.PracticePanelActions
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * PRACTICE-01 연결.
 *
 * @param solo 연습곡 선택(SONG-03)에서 들어온 연습 모드. 평가 없이 혼자 연습한다.
 * @param mine 내 악보로 연습(Practice-Mine). 혼자 연습과 같되 평가로 넘어가는 버튼이 없다.
 * @param learn 기초부터 배우기 8단계의 곡 연습. 혼자 연습과 같되 평가로 넘어가는 버튼이 없고 '운지법과 함께 보기'로 시작한다.
 * @param sheetTitle 내 악보 제목(‘아리랑 (내 편곡)’). 있으면 곡 제목 대신 보인다. 비우면 곡 제목.
 * @param showSwitchedNotice 일반 연습에서 평가로 전환한 진입. 곡 로딩 후 평가를 한 번만 바로 시작한다.
 * @param direct 대기 상태 없이 들어오자마자 평가를 시작하는 연주 평가. 곡 정보의 '연주 평가 시작'(S15P31E102-705)과
 *   혼자 연습에서 바꾼 평가([showSwitchedNotice]와 함께, S15P31E102-739)가 쓴다. 준비 상태로 돌아갈 일(시작 취소·연주 중단·
 *   연습 준비로·마이크 거부)이 생기면 [onBack]으로 들어온 화면(곡 정보 또는 혼자 연습)에 돌아간다.
 * @param onSwitchToEval 연습 모드에서 같은 곡의 평가 연습으로 바꾼다.
 * @param onEvaluate 연주를 마치고 '평가하기'. 저장된 녹음이 있을 때만 연습 화면을 준비 상태로 돌리고 분석 화면으로 간다.
 * @param onConfirmBackChange 떠나기 전에 확인할 때(연주·카운트다운 중) 화면의 뒤로 가기 동작을, 아니면 null을 알린다.
 *   Web 브라우저 뒤로 가기처럼 [BackHandler]를 거치지 않는 뒤로 가기를 화면 밖에서 가로챌 때 쓴다.
 *
 * 마이크를 허용하지 않으면 보드(Dialog-PermDenied·PermBlocked)대로 화면에 머물며 안내 창을 띄운다.
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
    onEvaluate: ((songId: String, bpm: Int, take: RecordedTake?, song: Song?) -> Unit)?,
    fingeringAudio: @Composable (GuideInstrument, FingeringNote, (Boolean) -> Unit) -> Unit,
    /** 한 음씩 '현재 정간' 칸의 운지 예시음 재생기(S15P31E102-717). 앱이 운지 가이드의 기본 예시음을 연결한다. */
    stepNoteAudio: @Composable (GuideInstrument) -> (FingeringNote) -> Unit = { { } },
    onConfirmBackChange: ((() -> Unit)?) -> Unit = {},
    mine: Boolean = false,
    sheetTitle: String? = null,
    learn: Boolean = false,
    direct: Boolean = false,
    perf: PerformanceVisit? = null,
    viewModel: PracticeViewModel = koinViewModel(key = "$songId-$solo-$mine-$learn-$direct") { parametersOf(songId, solo, mine, learn, perf, direct) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 평가 연주에서 음마다 모은 실시간 음높이 안내(S15P31E102-597). 관측이 없는 플랫폼은 집계가 비어 아무것도 칠하지 않는다.
    val pitchTally by viewModel.livePitch.collectAsStateWithLifecycle()
    val livePitch = remember(state.song, state.solo, pitchTally) { livePitchMarks(state, pitchTally) }
    val snackbarHostState = remember { SnackbarHostState() }
    // 한 음씩은 혼자 연습에만 있어 그때만 예시음 목록을 받는다.
    val playStepNote: (FingeringNote) -> Unit = if (state.solo) stepNoteAudio(state.instrument) else { _ -> }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sheetTitle) { viewModel.showSheetTitle(sheetTitle) }
    LaunchedEffect(showSwitchedNotice, state.song?.id) {
        if (showSwitchedNotice || state.direct) viewModel.startEvaluationOnEntry()
    }
    // 앱을 벗어나면(전화·다른 앱) 연주를 끊긴 것으로 처리한다.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onLeaveScreen() }
    // 곡 정보에서 바로 시작한 평가는 분석·결과에서 기기 뒤로 가기로 돌아와도 준비 상태를 보이지 않고 곡 정보로 간다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (viewModel.leavesDirectOnReturn()) onBack() }
    // 연습 시간은 화면이 앞에 보이는 동안만 잰다. 다른 화면으로 가거나 백그라운드·화면 잠금, 웹 탭 숨김이면 멈춘다
    // (Compose 웹도 탭이 가려지면 ON_STOP을 보낸다).
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenShown()
        onStopOrDispose { viewModel.onScreenHidden() }
    }

    val back = { if (viewModel.onBack()) onBack() }
    // 확인 창의 '곡 정보로'는 곡 정보에서 바로 시작한 평가에만 쓴다. 혼자 연습에서 바꾼 평가는 기존 문구('연습 준비로'·'홈으로 가기')를 둔다.
    val toSongInfo = state.direct && !showSwitchedNotice
    // 대기 상태 없이 시작한 평가는 준비 상태를 보이지 않고 들어온 화면(곡 정보 또는 혼자 연습)으로 돌아간다.
    val leaveEvaluation = {
        viewModel.stopAll()
        onBack()
    }
    // 기기 뒤로 가기도 화면의 뒤로 가기와 같게: 연주 중이면 확인 창을 띄운다. 태블릿 가로처럼 패널이 화면 안에 열려 있으면 패널부터 닫는다.
    // 휴대폰의 하단 시트·확인 창은 자기 창에서 뒤로 가기를 먼저 받는다.
    @Suppress("DEPRECATION") // ui-backhandler 1.12의 BackHandler. 대신할 NavigationEventHandler는 아직 의존성에 없다.
    BackHandler {
        if (state.panel != null) viewModel.closePanel() else back()
    }
    val confirmBeforeLeave = state.mode == PracticeMode.Recording || state.mode == PracticeMode.Countdown
    val currentBack by rememberUpdatedState(back)
    val currentOnConfirmBackChange by rememberUpdatedState(onConfirmBackChange)
    DisposableEffect(confirmBeforeLeave) {
        currentOnConfirmBackChange(if (confirmBeforeLeave) { { currentBack() } } else null)
        onDispose { currentOnConfirmBackChange(null) }
    }

    CompositionLocalProvider(LocalPerformanceVisit provides perf) {
        PracticeScreen(
            state = state,
            widthClass = currentWindowLayout().widthClass,
            snackbarHostState = snackbarHostState,
            actions = PracticeScreenActions(
                onBack = back,
                onOpenSettings = { viewModel.openPanel(PracticePanel.Settings) },
                onToggleHangul = viewModel::toggleHangul,
                onSelectUnit = viewModel::selectUnit,
                onUserScroll = viewModel::stopFollowing,
                onFollowNow = viewModel::followNow,
                onCancelCountdown = { if (state.direct && !state.resume) leaveEvaluation() else viewModel.cancelCountdown() },
                onUserActivity = viewModel::onUserActivity,
                onRetrySong = viewModel::retryLoadSong,
                bottom = PracticeBottomActions(
                    onStartModel = viewModel::startModel,
                    onDownloadSheetAudio = viewModel::downloadPersonalAudio,
                    onSelectModel = viewModel::selectModel,
                    onSelectRhythm = viewModel::selectRhythm,
                    onStartRhythm = viewModel::startRhythm,
                    onToggleRhythm = viewModel::toggleRhythm,
                    onToggleModel = viewModel::toggleModel,
                    onRestartModel = viewModel::restartModel,
                    onStopModel = viewModel::stopModel,
                    onStartEval = viewModel::requestEvaluation,
                    onSwitchToEval = {
                        if (state.recordingSupported) {
                            viewModel.stopAll()
                            onSwitchToEval()
                        }
                    },
                    onAskRestart = viewModel::askRestart,
                    onAskAbort = viewModel::askAbort,
                    onSeek = viewModel::seekModel,
                    onSeekToFraction = viewModel::seekToFraction,
                    onToggleStep = viewModel::toggleStep,
                    onTapStepPlaying = viewModel::tapStepPlaying,
                    onListenStepNote = {
                        viewModel.uiState.value.stepNoteToPlay?.let(playStepNote)
                        viewModel.listenStepNote()
                    },
                    onConfirmStepNote = viewModel::confirmStepNoteManually,
                ),
                panel = PracticePanelActions(
                    onClose = viewModel::closePanel,
                    onDismiss = viewModel::dismissPanels,
                    onOpen = viewModel::openPanel,
                    onDraft = viewModel::updateDraft,
                    onToggleBigScore = viewModel::toggleBigScore,
                    onMoveFingering = viewModel::moveFingering,
                    fingeringAudio = { instrument, note -> fingeringAudio(instrument, note, viewModel::reportFingeringAudio) },
                    onTogglePreview = viewModel::togglePreview,
                    onSetInstrument = viewModel::setInstrument,
                    onToggleFingeringView = viewModel::toggleFingeringView,
                ),
                dialog = PracticeDialogActions(
                    onGrantMic = {
                        scope.launch { if (viewModel.requestMicPermission()) viewModel.grantMicAndStart() else viewModel.micDenied() }
                    },
                    onDenyMic = { if (state.direct) leaveEvaluation() else viewModel.closePopup() },
                    onClose = {
                        val denied = state.popup == PracticePopup.PermDenied || state.popup == PracticePopup.PermBlocked
                        if (state.direct && denied) leaveEvaluation() else viewModel.closePopup()
                    },
                    onGoHome = {
                        viewModel.stopAll()
                        if (toSongInfo) onBack() else onGoHome()
                    },
                    onResume = viewModel::resumeRecording,
                    onEvaluate = {
                        val bpm = state.bpm
                        val take = viewModel.takeForEvaluation()
                        if (take != null) {
                            viewModel.toReady()
                            onEvaluate?.invoke(songId, bpm, take, state.song)
                        }
                    },
                    onRestartNow = viewModel::restartNow,
                    onExit = {
                        viewModel.stopAll()
                        onBack()
                    },
                    onKeepPlayingAfterExit = viewModel::keepPlayingAfterExit,
                    onToReady = { if (state.direct) leaveEvaluation() else viewModel.toReady() },
                    onOpenMicSettings = {
                        val opened = viewModel.openMicSettings()
                        if (state.direct) {
                            leaveEvaluation()
                        } else {
                            viewModel.closePopup()
                            if (!opened) scope.launch { snackbarHostState.showSnackbar(getString(Res.string.common_coming_soon)) }
                        }
                    },
                ),
            ),
            livePitch = livePitch,
            toSongInfo = toSongInfo,
        )
    }
}

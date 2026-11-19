package com.ssafy.eolssu.feature.practice

import androidx.compose.runtime.CompositionLocalProvider
import com.ssafy.eolssu.core.diagnostics.LocalPerformanceVisit
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.ssafy.eolssu.feature.practice.component.LocalStepNotePlayback
import com.ssafy.eolssu.feature.practice.component.LocalStepPitchGauge
import com.ssafy.eolssu.feature.practice.component.StepNoteAudio
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
 * @param direct 연습 준비의 플레이어 없이 평가만 하는 연주 평가. 곡 정보의 '연주 평가 시작'(S15P31E102-705)과
 *   혼자 연습에서 바꾼 평가(S15P31E102-739)가 쓴다. 들어오면 평가 대기 상태로 두고 하단의
 *   '평가 시작'을 눌러야 평가를 시작한다. 시작 취소·연습 준비로·마이크 거부도 이 대기 상태로 돌아온다(S15P31E102-747).
 *   연주 중단 창도 화면을 나가지 않고 '연주 대기로' 이 대기 상태로 돌아온다(S15P31E102-750).
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
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onSwitchToEval: () -> Unit,
    onEvaluate: ((songId: String, bpm: Int, take: RecordedTake?, song: Song?) -> Unit)?,
    fingeringAudio: @Composable (GuideInstrument, FingeringNote, (Boolean) -> Unit) -> Unit,
    /** 한 음씩 '현재 정간' 칸의 운지 예시음 재생기(S15P31E102-717). 앱이 운지 가이드의 기본 예시음과 그 재생 상태를 연결한다. */
    stepNoteAudio: @Composable (GuideInstrument) -> StepNoteAudio = { StepNoteAudio({}) },
    onConfirmBackChange: ((() -> Unit)?) -> Unit = {},
    mine: Boolean = false,
    sheetTitle: String? = null,
    learn: Boolean = false,
    direct: Boolean = false,
    /** 구간 연습(S15P31E102-794)의 구간. 있으면 이 구간만 연습하고, 평가 버튼은 구간 재평가가 된다. */
    initialSection: ScoreSection? = null,
    /** 구간 연습의 ‘구간 재평가 시작’: 같은 구간으로 구간 재평가 화면에 간다. */
    onSectionEvaluate: (section: ScoreSection) -> Unit = {},
    perf: PerformanceVisit? = null,
    viewModel: PracticeViewModel = koinViewModel(key = "$songId-$solo-$mine-$learn-$direct-$initialSection") {
        parametersOf(songId, solo, mine, learn, perf, direct, listOfNotNull(initialSection))
    },
    /** 화면 밖에서 안내를 띄울 때 넘긴다(구간 재평가의 녹음 미지원 안내). */
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 평가 연주에서 음마다 모은 실시간 음높이 안내(S15P31E102-597). 관측이 없는 플랫폼은 집계가 비어 아무것도 칠하지 않는다.
    val pitchTally by viewModel.livePitch.collectAsStateWithLifecycle()
    val livePitch = remember(state.song, state.solo, state.position, state.finished, state.settings.liveMarks, pitchTally) { livePitchMarks(state, pitchTally) }
    // 한 음씩은 혼자 연습에만 있어 그때만 예시음 목록을 받는다.
    val stepAudio: StepNoteAudio = if (state.solo) stepNoteAudio(state.instrument) else StepNoteAudio({})
    val playStepNote: (FingeringNote) -> Unit = stepAudio.play
    val scope = rememberCoroutineScope()
    LaunchedEffect(sheetTitle) { viewModel.showSheetTitle(sheetTitle) }
    // 앱을 벗어나면(전화·다른 앱) 연주를 끊긴 것으로 처리한다.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onLeaveScreen() }
    // 연주 평가는 분석·결과에서 기기 뒤로 가기로 돌아와도 평가 대기 상태를 보이지 않고 들어온 화면으로 간다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (viewModel.leavesDirectOnReturn()) onBack() }
    // 연습 시간은 화면이 앞에 보이는 동안만 잰다. 다른 화면으로 가거나 백그라운드·화면 잠금, 웹 탭 숨김이면 멈춘다
    // (Compose 웹도 탭이 가려지면 ON_STOP을 보낸다).
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenShown()
        onStopOrDispose { viewModel.onScreenHidden() }
    }

    val back = { if (viewModel.onBack()) onBack() }
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

    // 디버그 빌드에서만 한 음씩 음높이 막대에 값을 넘긴다(S15P31E102-666).
    // 예시음 재생 경과·길이는 '현재 정간' 칸만 쓰므로 화면 상태 대신 여기서 넘긴다(S15P31E102-1006).
    CompositionLocalProvider(
        LocalPerformanceVisit provides perf,
        LocalStepPitchGauge provides viewModel.stepGauge,
        LocalStepNotePlayback provides stepAudio.playback,
    ) {
        PracticeScreen(
            state = state,
            widthClass = currentWindowLayout().widthClass,
            snackbarHostState = snackbarHostState,
            actions = PracticeScreenActions(
                onBack = back,
                onOpenSettings = { viewModel.openPanel(PracticePanel.Settings) },
                onSelectUnit = viewModel::selectUnit,
                onUserScroll = viewModel::stopFollowing,
                onFollowNow = viewModel::followNow,
                onCancelCountdown = viewModel::cancelCountdown,
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
                    onStartSectionEval = {
                        val section = state.sectionPractice?.section
                        if (section != null) {
                            viewModel.stopAll()
                            onSectionEvaluate(section)
                        }
                    },
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
                    onToggleFingeringView = viewModel::toggleFingeringView,
                    onToggleHangul = viewModel::toggleHangul,
                    onToggleLiveMarks = viewModel::toggleLiveMarks,
                ),
                dialog = PracticeDialogActions(
                    // 마이크 안내 창(Permission)은 혼자 연습 '한 음씩'에서만 뜬다. 연주 평가는 기기 권한 창을 바로 띄운다.
                    onGrantMic = {
                        scope.launch { if (viewModel.requestMicPermission()) viewModel.grantMicAndStart() else viewModel.micDenied() }
                    },
                    onDenyMic = viewModel::closePopup,
                    onClose = viewModel::closePopup,
                    onRetryEvaluation = viewModel::retryEvaluation,
                    onGoHome = {
                        viewModel.stopAll()
                        onGoHome()
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
                    onToReady = viewModel::toReady,
                    onOpenMicSettings = {
                        val opened = viewModel.openMicSettings()
                        viewModel.closePopup()
                        if (!opened) scope.launch { snackbarHostState.showSnackbar(getString(Res.string.common_coming_soon)) }
                    },
                ),
            ),
            livePitch = livePitch,
        )
    }
}

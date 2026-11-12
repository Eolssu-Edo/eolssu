package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.practice.SessionSection
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_loop_mic_blocked
import eolssu.shared.generated.resources.weak_loop_mic_denied
import eolssu.shared.generated.resources.weak_loop_no_notes
import eolssu.shared.generated.resources.weak_loop_no_recorder
import eolssu.shared.generated.resources.weak_loop_open_settings
import eolssu.shared.generated.resources.weak_loop_record_failed
import eolssu.shared.generated.resources.weak_score_changed
import eolssu.shared.generated.resources.weak_score_unavailable
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

/**
 * 구간 재평가 연결(S15P31E102-776). 연습 기록의 ‘구간 재평가’ → 구간 고르기에서 정한 구간·속도로 연다. 연주 평가 화면 틀이다.
 * 앱을 벗어나면(홈 화면·전화) 재평가 녹음은 버린다.
 *
 * @param onEvaluate 구간 재평가 녹음을 마쳤다. 곡·녹음·구간(음 번호와 바탕 세션)을 분석 대기 화면에 넘기면, 분석이 끝난 뒤 RESULT-04로 간다.
 */
@Composable
fun SectionLoopRoute(
    sourceId: String,
    section: ScoreSection,
    onBack: () -> Unit,
    onEvaluate: (songId: String, bpm: Int, take: RecordedTake, section: SessionSection) -> Unit,
    perf: PerformanceVisit? = null,
    viewModel: SectionLoopViewModel = koinViewModel(key = "section-evaluate-$sourceId-$section") {
        parametersOf(sourceId, section, 1, perf)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    LaunchedEffect(state.take) {
        val take = state.take ?: return@LaunchedEffect
        val songId = state.song?.id ?: return@LaunchedEffect
        viewModel.takeConsumed()
        onEvaluate(songId, state.bpm, take.take, take.section)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        // 안내를 지우면 이 효과가 다시 시작되므로, 안내는 화면 범위에서 띄운다. 그렇지 않으면 문구를 불러오는 사이(웹)에 취소된다.
        viewModel.noticeShown()
        scope.launch { showNotice(notice, snackbarHostState, viewModel) }
    }
    val tools = SectionToolActions(
        onOpenSettings = { viewModel.openPanel(PracticePanel.Settings) },
        onOpen = viewModel::openPanel,
        onClose = viewModel::closePanel,
        onDismiss = viewModel::dismissPanels,
        onDraft = viewModel::updateDraft,
        onToggleBigScore = viewModel::toggleBigScore,
        onTogglePreview = viewModel::togglePreview,
        onSetInstrument = viewModel::setInstrument,
        onToggleFingeringView = viewModel::toggleFingeringView,
        onToggleNotation = viewModel::toggleNotation,
    )
    SectionEvaluateScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        widthClass = currentWindowLayout().widthClass,
        actions = SectionEvaluateActions(
            onBack = onBack,
            onStart = viewModel::startEvaluation,
            onCancel = viewModel::cancelEvaluation,
            onAskAbort = viewModel::askAbort,
            onKeepRecording = viewModel::keepRecording,
            tools = tools,
        ),
    )
}

/** 구간 연습·재평가 안내를 스낵바로 띄운다. 마이크를 다시 물을 수 없으면 ‘설정 열기’를 둔다. */
private suspend fun showNotice(notice: SectionLoopNotice, snackbarHostState: SnackbarHostState, viewModel: SectionLoopViewModel) {
    snackbarHostState.currentSnackbarData?.dismiss()
    val message = getString(
        when (notice) {
            SectionLoopNotice.ScoreChanged -> Res.string.weak_score_changed
            SectionLoopNotice.ScoreUnavailable -> Res.string.weak_score_unavailable
            SectionLoopNotice.NoRecorder -> Res.string.weak_loop_no_recorder
            SectionLoopNotice.NoNotes -> Res.string.weak_loop_no_notes
            SectionLoopNotice.MicDenied -> Res.string.weak_loop_mic_denied
            SectionLoopNotice.MicBlocked -> Res.string.weak_loop_mic_blocked
            SectionLoopNotice.RecordFailed -> Res.string.weak_loop_record_failed
        },
    )
    val action = if (notice == SectionLoopNotice.MicBlocked) getString(Res.string.weak_loop_open_settings) else null
    if (snackbarHostState.showSnackbar(message, actionLabel = action) == SnackbarResult.ActionPerformed) viewModel.openMicSettings()
}

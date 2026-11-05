package com.ssafy.eolssu.feature.practice

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.SessionSection
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_loop_mic_blocked
import eolssu.shared.generated.resources.weak_loop_mic_denied
import eolssu.shared.generated.resources.weak_loop_no_notes
import eolssu.shared.generated.resources.weak_loop_no_recorder
import eolssu.shared.generated.resources.weak_loop_open_settings
import eolssu.shared.generated.resources.weak_loop_record_failed
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * PRACTICE-03 구간 반복 연결. 앱을 벗어나면(홈 화면·전화) 반복을 멈추고 재평가 녹음은 버린다. 돌아와 ‘반복 계속’을 누르면 3·2·1부터 잇는다.
 *
 * @param onEvaluate ‘이 구간 재평가’ 녹음을 마쳤다. 곡·녹음·구간(음 번호와 바탕 세션)을 분석 대기 화면에 넘기면, 분석이 끝난 뒤 RESULT-04로 간다.
 */
@Composable
fun SectionLoopRoute(
    sourceId: String,
    section: ScoreSection,
    bpm: Int,
    repeats: Int?,
    onBack: () -> Unit,
    onEvaluate: (songId: String, take: RecordedTake, section: SessionSection) -> Unit,
    viewModel: SectionLoopViewModel = koinViewModel(key = "section-loop-$sourceId-$section-$bpm-$repeats") {
        // Koin 인자에 null을 넘기지 않도록 ‘계속’은 0으로 보낸다.
        parametersOf(sourceId, section, bpm, repeats ?: 0)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    LaunchedEffect(state.take) {
        val take = state.take ?: return@LaunchedEffect
        val songId = state.song?.id ?: return@LaunchedEffect
        viewModel.takeConsumed()
        onEvaluate(songId, take.take, take.section)
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.noticeShown()
        snackbarHostState.currentSnackbarData?.dismiss()
        val message = getString(
            when (notice) {
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
    SectionLoopScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTogglePause = viewModel::togglePause,
        onEvaluate = viewModel::startEvaluation,
    )
}

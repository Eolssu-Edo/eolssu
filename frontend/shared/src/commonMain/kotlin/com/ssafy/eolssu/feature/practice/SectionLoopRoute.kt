package com.ssafy.eolssu.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.ScoreSection
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * PRACTICE-03 구간 반복 연결. 앱을 벗어나면(홈 화면·전화) 반복을 멈추고, 돌아와 ‘반복 계속’을 누르면 3·2·1부터 잇는다.
 *
 * @param onEvaluate ‘이 구간 재평가’ → RESULT-04. 녹음·분석이 아직 없어 바로 예시 결과로 간다.
 */
@Composable
fun SectionLoopRoute(
    sourceId: String,
    section: ScoreSection,
    bpm: Int,
    repeats: Int?,
    onBack: () -> Unit,
    onEvaluate: () -> Unit,
    viewModel: SectionLoopViewModel = koinViewModel(key = "section-loop-$sourceId-$section-$bpm-$repeats") {
        // Koin 인자에 null을 넘기지 않도록 ‘계속’은 0으로 보낸다.
        parametersOf(sourceId, section, bpm, repeats ?: 0)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    SectionLoopScreen(
        state = state,
        onBack = onBack,
        onTogglePause = viewModel::togglePause,
        onEvaluate = {
            viewModel.stop()
            onEvaluate()
        },
    )
}

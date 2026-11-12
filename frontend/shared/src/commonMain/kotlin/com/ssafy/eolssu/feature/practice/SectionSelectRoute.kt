package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.ScoreSection
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * PRACTICE-02 취약 구간 선택 연결. [sourceId]는 평가·기록의 연습 세션 ID다.
 *
 * @param onEvaluate ‘구간 재평가’ → 같은 구간·속도의 구간 재평가.
 */
@Composable
fun SectionSelectRoute(
    sourceId: String,
    onBack: () -> Unit,
    onEvaluate: (section: ScoreSection, bpm: Int) -> Unit,
    perf: PerformanceVisit? = null,
    viewModel: SectionSelectViewModel = koinViewModel(key = "section-select-$sourceId") { parametersOf(sourceId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SectionSelectScreen(
        state = state,
        actions = SectionSelectActions(
            onBack = onBack,
            onTap = viewModel::tap,
            onReset = viewModel::resetToRecommended,
            onFilter = viewModel::selectFilter,
            onSpeed = viewModel::selectSpeed,
            onEvaluate = {
                val section = state.section
                val bpm = state.bpm
                if (state.canStart && section != null && bpm != null) onEvaluate(section, bpm)
            },
        ),
    )
}

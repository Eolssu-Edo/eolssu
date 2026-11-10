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
 * @param onStart ‘구간 연습 시작’ → PRACTICE-03 구간 반복. 반복 횟수 null은 ‘계속’이다.
 */
@Composable
fun SectionSelectRoute(
    sourceId: String,
    onBack: () -> Unit,
    onStart: (section: ScoreSection, bpm: Int, repeats: Int?) -> Unit,
    perf: PerformanceVisit? = null,
    viewModel: SectionSelectViewModel = koinViewModel(key = "section-select-$sourceId") { parametersOf(sourceId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SectionSelectScreen(
        state = state,
        actions = SectionSelectActions(
            onBack = onBack,
            onTap = viewModel::tap,
            onToggleLoop = viewModel::toggleLoop,
            onRepeat = viewModel::selectRepeat,
            onSpeed = viewModel::selectSpeed,
            onStart = {
                val section = state.section
                val bpm = state.bpm
                if (state.canStart && section != null && bpm != null) onStart(section, bpm, state.repeats)
            },
        ),
    )
}

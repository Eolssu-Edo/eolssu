package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.ScoreSection
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * PRACTICE-02 취약 구간 선택 연결. [sourceId]는 평가·기록의 연습 세션 ID다. 연습 기록의 ‘구간 연습’으로 들어온다.
 *
 * @param onStart ‘구간 연습 시작’ → 연습 화면을 고른 구간의 구간 연습으로 연다.
 * @param onEvaluate ‘구간 재평가 시작’ → 같은 구간의 구간 재평가. 속도는 각 화면의 설정에서 정한다.
 */
@Composable
fun SectionSelectRoute(
    sourceId: String,
    onBack: () -> Unit,
    onEvaluate: (section: ScoreSection) -> Unit,
    onStart: (songId: String, section: ScoreSection) -> Unit = { _, _ -> },
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
            onStart = {
                val section = state.section
                val songId = state.song?.id
                if (state.canStart && section != null && songId != null) onStart(songId, section)
            },
            onEvaluate = {
                val section = state.section
                if (state.canStart && section != null) onEvaluate(section)
            },
        ),
    )
}

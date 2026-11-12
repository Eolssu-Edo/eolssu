package com.ssafy.eolssu.feature.result

import androidx.compose.runtime.CompositionLocalProvider
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.diagnostics.LocalPerformanceVisit
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RESULT-04 구간 재평가 결과 연결. 구간 재평가는 연습 기록으로 저장하지 않으므로(세션 기준, S15P31E102-343) 저장 버튼이 없다.
 *
 * @param onOtherSection ‘다른 구간 고르기’ → 취약 구간 선택.
 * @param onWholeSong ‘곡 전체 다시 평가’ → 이 곡의 연습 화면(평가). 곡 ID를 넘긴다.
 */
@Composable
fun SectionResultRoute(
    sessionId: String,
    onBack: () -> Unit,
    onOtherSection: () -> Unit,
    onWholeSong: (songId: String) -> Unit,
    perf: PerformanceVisit? = null,
    viewModel: SectionResultViewModel = koinViewModel(key = "section-result-$sessionId") { parametersOf(sessionId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalPerformanceVisit provides perf) {
        SectionResultScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            actions = SectionResultActions(
                onBack = onBack,
                onOtherSection = onOtherSection,
                onWholeSong = { state.evaluation?.let { onWholeSong(it.song.id) } },
                onSelectFilter = viewModel::selectFilter,
            ),
        )
    }
}

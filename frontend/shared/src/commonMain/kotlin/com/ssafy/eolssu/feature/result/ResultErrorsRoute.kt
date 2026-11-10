package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** RESULT-03 평가 직후 오류 위치. 저장한 기록의 조회·삭제·재생은 history가 맡는다. */
@Composable
fun ResultErrorsRoute(
    sourceId: String,
    onBack: () -> Unit,
    onSelectSection: () -> Unit,
    perf: PerformanceVisit? = null,
    viewModel: ResultErrorsViewModel = koinViewModel(key = "result-errors-$sourceId") { parametersOf(sourceId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultErrorsScreen(state, onBack, viewModel::selectFilter, viewModel::selectCell, onSelectSection, onRetry = viewModel::retry)
}

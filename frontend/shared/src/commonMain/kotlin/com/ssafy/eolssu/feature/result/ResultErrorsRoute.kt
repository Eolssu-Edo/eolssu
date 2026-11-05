package com.ssafy.eolssu.feature.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RESULT-03 오류 위치 연결. [sourceId]는 평가한 연습 세션 ID다.
 *
 * @param onSelectSection ‘취약 구간 골라서 연습하기’ → PRACTICE-02 취약 구간 선택.
 */
@Composable
fun ResultErrorsRoute(
    sourceId: String,
    onBack: () -> Unit,
    onSelectSection: () -> Unit,
    viewModel: ResultErrorsViewModel = koinViewModel(key = "result-errors-$sourceId") { parametersOf(sourceId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultErrorsScreen(
        state = state,
        onBack = onBack,
        onSelectFilter = viewModel::selectFilter,
        onSelectCell = viewModel::selectCell,
        onSelectSection = onSelectSection,
    )
}

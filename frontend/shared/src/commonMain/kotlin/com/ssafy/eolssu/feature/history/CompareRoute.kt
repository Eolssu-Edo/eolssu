package com.ssafy.eolssu.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * COMPARE-01 이전 연주와 비교 연결. 기록과 서버 비교(EVA-04)로 그린다.
 *
 * @param previousId 이전 연주 결과의 기록 고르기 시트에서 고른 기록. null이면 바로 전 기록과 견준다.
 */
@Composable
fun CompareRoute(
    recordId: String,
    previousId: String?,
    onBack: () -> Unit,
    viewModel: CompareViewModel = koinViewModel(key = "compare-$recordId-${previousId.orEmpty()}") { parametersOf(recordId, previousId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompareScreen(
        state = state,
        actions = CompareActions(
            onBack = onBack,
            onOpenPicker = viewModel::openPicker,
            onClosePicker = viewModel::closePicker,
            onPickRecord = viewModel::pickRecord,
            onToggleMetrics = viewModel::toggleMetrics,
        ),
    )
}

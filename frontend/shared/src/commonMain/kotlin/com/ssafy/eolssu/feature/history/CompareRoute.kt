package com.ssafy.eolssu.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * COMPARE-01 이전 연주와 비교 연결. 지금은 예시 기록으로 비교하고 녹음은 재생하지 않는다.
 *
 * @param onPractice ‘틀린 구간 연습하기’ → PRACTICE-02 취약 구간 선택. 이 기록 ID를 넘긴다.
 */
@Composable
fun CompareRoute(
    recordId: String,
    onBack: () -> Unit,
    onPractice: (recordId: String) -> Unit,
    viewModel: CompareViewModel = koinViewModel(key = "compare-$recordId") { parametersOf(recordId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompareScreen(
        state = state,
        actions = CompareActions(
            onBack = onBack,
            onSelect = viewModel::select,
            onTogglePlay = viewModel::togglePlay,
            onOpenPicker = viewModel::openPicker,
            onClosePicker = viewModel::closePicker,
            onPickDay = viewModel::pickDay,
            onPickRecord = viewModel::pickRecord,
            onPractice = { onPractice(recordId) },
        ),
    )
}

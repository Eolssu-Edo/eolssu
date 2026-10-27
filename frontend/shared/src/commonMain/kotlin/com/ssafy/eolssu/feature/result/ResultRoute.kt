package com.ssafy.eolssu.feature.result

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RESULT-02 연결. 오류 위치 전체 보기·취약 구간 연습은 2차 확장 화면이라 '준비 중' 안내를 띄운다.
 *
 * @param onGoRecords 저장 뒤 '기록 보러 가기'. null이면 기록 화면이 아직 없어 '준비 중' 안내를 띄운다.
 */
@Composable
fun ResultRoute(
    evaluationId: String,
    onBack: () -> Unit,
    onPracticeAgain: () -> Unit,
    onOtherSong: () -> Unit,
    onHome: () -> Unit,
    onGoRecords: ((recordId: String) -> Unit)?,
    viewModel: ResultViewModel = koinViewModel(key = evaluationId) { parametersOf(evaluationId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    val showComingSoon: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(comingSoon)
        }
    }
    ResultScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = ResultActions(
            onBack = onBack,
            onSave = viewModel::save,
            onViewAllErrors = showComingSoon,
            onWeakSection = showComingSoon,
            onPracticeAgain = onPracticeAgain,
            onOtherSong = onOtherSong,
            onHome = onHome,
            onMemoChange = viewModel::changeMemo,
            onCloseSheet = viewModel::closeSheet,
            onGoRecords = {
                val recordId = state.savedRecord?.id
                if (onGoRecords != null && recordId != null) {
                    viewModel.closeSheet()
                    onGoRecords(recordId)
                } else {
                    viewModel.closeSheet()
                    showComingSoon()
                }
            },
        ),
    )
}

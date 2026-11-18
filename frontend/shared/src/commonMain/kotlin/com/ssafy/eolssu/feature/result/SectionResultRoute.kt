package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import androidx.compose.runtime.CompositionLocalProvider
import com.ssafy.eolssu.core.diagnostics.LocalPerformanceVisit
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.record_save_failed
import eolssu.shared.generated.resources.record_save_unavailable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RESULT-04 구간 재평가 결과 연결. ‘연습 기록 저장’은 연주 결과와 같은 저장 시트로 이 구간 세션을 저장한다(S15P31E102-832).
 *
 * @param onHome 오른쪽 위 홈 → 홈 화면.
 * @param onOtherSection ‘다른 구간 고르기’ → 취약 구간 선택.
 * @param onWholeSong ‘곡 전체 다시 평가’ → 이 곡의 연습 화면(평가). 곡 ID를 넘긴다.
 * @param onOpenRecord 저장 완료 시트의 ‘기록으로 이동’ → 방금 저장한 기록 상세. 기록 ID를 넘긴다.
 */
@Composable
fun SectionResultRoute(
    sessionId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOtherSection: () -> Unit,
    onWholeSong: (songId: String) -> Unit,
    onOpenRecord: (recordId: String) -> Unit = {},
    perf: PerformanceVisit? = null,
    viewModel: SectionResultViewModel = koinViewModel(key = "section-result-$sessionId") { parametersOf(sessionId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = when (state.recordSaveError) {
        RecordSaveError.Unavailable -> stringResource(Res.string.record_save_unavailable)
        RecordSaveError.Failed -> stringResource(Res.string.record_save_failed)
        null -> null
    }
    LaunchedEffect(state.recordSaveError) {
        if (saveErrorMessage != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(saveErrorMessage)
            viewModel.dismissRecordSaveError()
        }
    }
    CompositionLocalProvider(LocalPerformanceVisit provides perf) {
        SectionResultScreen(
            state = state,
            widthClass = currentWindowLayout().widthClass,
            snackbarHostState = snackbarHostState,
            actions = SectionResultActions(
                onBack = onBack,
                onHome = onHome,
                onOtherSection = onOtherSection,
                onSave = viewModel::save,
                onConfirmSave = viewModel::confirmSave,
                onMemoChange = viewModel::changeMemo,
                onCloseSheet = viewModel::closeSheet,
                onWholeSong = { state.evaluation?.let { onWholeSong(it.song.id) } },
                onSelectFilter = viewModel::selectFilter,
                // ‘기록으로 이동’: 시트를 닫고 방금 저장한 기록 상세로 간다.
                onOpenRecord = {
                    state.savedRecord?.id?.let { id ->
                        viewModel.closeSheet()
                        onOpenRecord(id)
                    }
                },
            ),
        )
    }
}

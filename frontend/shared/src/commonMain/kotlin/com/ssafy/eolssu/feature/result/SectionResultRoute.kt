package com.ssafy.eolssu.feature.result

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.ScoreSection
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * RESULT-04 구간 재평가 결과 연결. 예시 결과라 연습 기록 저장은 아직 하지 않고 '준비 중' 안내를 띄운다.
 *
 * @param onPracticeAgain ‘다시 연습’ → 같은 구간 반복.
 * @param onOtherSection ‘다른 구간 고르기’ → 취약 구간 선택.
 * @param onWholeSong ‘곡 전체 다시 평가’ → 이 곡의 연습 화면(평가). 곡 ID를 넘긴다.
 */
@Composable
fun SectionResultRoute(
    sourceId: String,
    section: ScoreSection,
    bpm: Int,
    onBack: () -> Unit,
    onPracticeAgain: () -> Unit,
    onOtherSection: () -> Unit,
    onWholeSong: (songId: String) -> Unit,
    viewModel: SectionResultViewModel = koinViewModel(key = "section-result-$sourceId-$section-$bpm") { parametersOf(sourceId, section, bpm) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    SectionResultScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = SectionResultActions(
            onBack = onBack,
            onSave = {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(comingSoon)
                }
            },
            onPracticeAgain = onPracticeAgain,
            onOtherSection = onOtherSection,
            onWholeSong = { state.evaluation?.let { onWholeSong(it.song.id) } },
        ),
    )
}

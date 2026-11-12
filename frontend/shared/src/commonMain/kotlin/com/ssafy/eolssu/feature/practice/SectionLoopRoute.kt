package com.ssafy.eolssu.feature.practice

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.data.practice.SessionSection
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.weak_loop_no_recorder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 구간 재평가 연결(S15P31E102-776·824). 연습 기록의 ‘구간 재평가’ → 구간 고르기에서 정한 구간으로 연다.
 * 화면·진행·확인 창은 연주 평가([PracticeRoute]의 `direct`)를 그대로 쓰고, 고른 구간을 악보에 표시하는 것과 그 구간만 녹음해
 * 평가하는 것만 다르다. 그래서 평가 대기 → 3·2·1 → 연주(악보의 회색 진행 채움·붉은 글자·하단 진행 막대) → ‘연주를 마쳤어요’ →
 * 분석 순서와 연주 중단·나가기·끊김 확인 창이 연주 평가와 같다. 속도는 연습 화면과 같은 곡 속도 설정을 따르고 연주 중에는 잠긴다.
 *
 * 분석·결과에서 돌아오면 연주 평가와 달리 평가 대기 상태로 남아 같은 구간을 다시 평가할 수 있다.
 *
 * @param onEvaluate ‘평가하기’. 곡·녹음·구간(음 번호와 바탕 세션)을 분석 대기 화면에 넘기면, 분석이 끝난 뒤 RESULT-04로 간다.
 */
@Composable
fun SectionLoopRoute(
    sourceId: String,
    section: ScoreSection,
    onBack: () -> Unit,
    onEvaluate: (songId: String, bpm: Int, take: RecordedTake, section: SessionSection) -> Unit,
    perf: PerformanceVisit? = null,
    // 연주 평가와 같은 ViewModel 정의를 쓴다. 곡 ID 대신 바탕 세션(여덟째 인자)의 악보를 불러온다.
    viewModel: PracticeViewModel = koinViewModel(key = "section-evaluate-$sourceId-$section") {
        parametersOf("", false, false, false, perf, true, listOf(section), sourceId)
    },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // 녹음기가 없는 플랫폼(웹)은 ‘평가 시작’이 잠긴다. 들어올 때 한 번 까닭을 알린다.
    val noRecorder by remember(viewModel) {
        viewModel.uiState.map { it.song != null && !it.recordingSupported }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(false)
    LaunchedEffect(noRecorder) {
        if (noRecorder) snackbarHostState.showSnackbar(getString(Res.string.weak_loop_no_recorder))
    }
    PracticeRoute(
        songId = "",
        solo = false,
        onBack = onBack,
        // 연주 평가의 확인 창에는 ‘홈으로 가기’가 없다(‘연주 대기로’).
        onGoHome = onBack,
        onSwitchToEval = {},
        onEvaluate = { _, bpm, take, song ->
            val sessionSection = viewModel.sectionForEvaluation()
            if (take != null && song != null && sessionSection != null) onEvaluate(song.id, bpm, take, sessionSection)
        },
        // 구간이 정해져 있어 악보의 음을 눌러 운지법을 열지 않는다.
        fingeringAudio = { _, _, _ -> },
        direct = true,
        initialSection = section,
        perf = perf,
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
    )
}

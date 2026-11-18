package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.diagnostics.LocalPerformanceVisit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [SongDetailViewModel]의 Koin 인자(곡 ID, 계측). 곡 상세와 태블릿 가로 곡 목록의 오른쪽 칸이 함께 써서
 * 정의(`AppModule`)와 인자 수가 어긋나 화면을 열 때 앱이 종료되지 않게 한다(S15P31E102-1012).
 */
internal fun songDetailParameters(songId: String, perf: PerformanceVisit? = null) = parametersOf(songId, perf)

/**
 * SONG-02 연결. '연주 평가 시작'을 누르면 [onStartPractice]로 연주 평가 화면의 대기 상태로 간다.
 * 연주 평가 화면이 연결되지 않았으면(null) '준비 중' 안내를 띄운다.
 */
@Composable
fun SongDetailRoute(
    songId: String,
    onBack: () -> Unit,
    onStartPractice: ((songId: String) -> Unit)?,
    perf: PerformanceVisit? = null,
    viewModel: SongDetailViewModel = koinViewModel(key = songId) { songDetailParameters(songId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    CompositionLocalProvider(LocalPerformanceVisit provides perf) {
        SongDetailScreen(
            state = state,
            widthClass = currentWindowLayout().widthClass,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onRetry = viewModel::retry,
            onStartPractice = {
                if (onStartPractice != null) {
                    onStartPractice(songId)
                } else {
                    scope.launch { snackbarHostState.showSnackbar(comingSoon) }
                }
            },
        )
    }
}

package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * SONG-02 연결. '연주 평가 시작'을 누르면 [onStartPractice]로 연주 평가 화면에 가서 바로 평가를 시작한다.
 * 연주 평가 화면이 연결되지 않았으면(null) '준비 중' 안내를 띄운다.
 * '연습 설정'은 [settingsSheet]로 연주 평가 화면과 같은 연습 설정 시트를 띄우고, 닫으면 곡 정보에 남는다(S15P31E102-705).
 */
@Composable
fun SongDetailRoute(
    songId: String,
    onBack: () -> Unit,
    onStartPractice: ((songId: String) -> Unit)?,
    settingsSheet: @Composable (songId: String, onClose: () -> Unit) -> Unit = { _, _ -> },
    perf: PerformanceVisit? = null,
    viewModel: SongDetailViewModel = koinViewModel(key = songId) { parametersOf(songId, perf) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    SongDetailScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenSettings = { showSettings = true },
        onStartPractice = {
            if (onStartPractice != null) {
                onStartPractice(songId)
            } else {
                scope.launch { snackbarHostState.showSnackbar(comingSoon) }
            }
        },
    )
    if (showSettings) settingsSheet(songId) { showSettings = false }
}

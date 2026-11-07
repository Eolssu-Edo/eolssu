package com.ssafy.eolssu.feature.home

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.StartMessage
import com.ssafy.eolssu.core.ui.component.StartMessageEffect
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_coming_soon
import eolssu.shared.generated.resources.home_exit_notice
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

/**
 * 홈 화면 연결. 'AI 연주 평가 시작'은 [onStartEvaluation]으로 곡 목록에, 오른쪽 위 아이콘은 [onOpenMyPage]로 마이페이지에 간다.
 * 학습·연습 카드는 [onOpenFeature]가 화면을 열면 true를 돌려주고, 아직 연결된 화면이 없으면 false라 '준비 중' 안내를 띄운다.
 * [startMessage]는 가입을 마치고 왔을 때처럼 이전 화면이 남긴 안내다.
 * [exitOnDoubleBack]이면(홈 아래에 쌓인 화면이 없을 때) Android 시스템 뒤로 가기 첫 입력은 하단 안내만 띄우고,
 * 2초 안에 다시 누르면 앱을 닫는다. iOS·Web과 홈 아래에 화면이 있을 때는 뒤로 가기를 가로채지 않는다.
 */
@Composable
fun HomeRoute(
    onStartEvaluation: () -> Unit,
    onOpenFeature: (HomeFeature) -> Boolean,
    onOpenMyPage: () -> Unit,
    startMessage: StartMessage? = null,
    onStartMessageShown: () -> Unit = {},
    exitOnDoubleBack: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.common_coming_soon)
    StartMessageEffect(snackbarHostState, startMessage, onStartMessageShown)
    HomeDoubleBackExit(exitOnDoubleBack, snackbarHostState)

    HomeScreen(
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onOpenMyPage = onOpenMyPage,
        onStartEvaluation = onStartEvaluation,
        onFeatureClick = { feature ->
            if (!onOpenFeature(feature)) scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(comingSoon)
            }
        },
    )
}

/** 뒤로 가기 버튼·제스처 모두 [BackHandler]로 받는다. 안내는 [HomeBackExitWindowMillis] 뒤 스스로 닫힌다. */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION") // ui-backhandler 1.12의 BackHandler. 대신할 NavigationEventHandler는 아직 의존성에 없다.
@Composable
private fun HomeDoubleBackExit(enabled: Boolean, snackbarHostState: SnackbarHostState) {
    val exitApp = rememberHomeAppExit()
    val notice = stringResource(Res.string.home_exit_notice)
    val scope = rememberCoroutineScope()
    val backExit = remember { HomeBackExit() }
    val clock = remember { TimeSource.Monotonic.markNow() }
    var noticeJob by remember { mutableStateOf<Job?>(null) }
    BackHandler(enabled = enabled && exitApp != null) {
        if (backExit.press(clock.elapsedNow().inWholeMilliseconds)) {
            noticeJob?.cancel()
            exitApp?.invoke()
            return@BackHandler
        }
        noticeJob?.cancel()
        noticeJob = scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            withTimeoutOrNull(HomeBackExitWindowMillis) { snackbarHostState.showSnackbar(notice, duration = SnackbarDuration.Indefinite) }
        }
    }
}

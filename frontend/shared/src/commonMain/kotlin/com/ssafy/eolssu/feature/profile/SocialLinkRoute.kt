package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** MY-05 연결. 연결하면 [onLinked]로 마이페이지에 안내를 남기고, 뒤로 가기·다른 계정 연결 안내의 확인은 [onBack]으로 돌아간다. */
@Composable
fun SocialLinkRoute(
    provider: SocialProvider,
    onBack: () -> Unit,
    onLinked: () -> Unit,
    viewModel: SocialLinkViewModel = koinViewModel { parametersOf(provider) },
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    LaunchedEffect(step) { if (step == SocialLinkStep.Linked) onLinked() }
    SocialLinkScreen(
        provider = provider,
        step = step,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onRetry = viewModel::start,
        onReopen = viewModel::reopen,
    )
}

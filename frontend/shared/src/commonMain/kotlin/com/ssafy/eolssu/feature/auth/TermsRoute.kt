package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.data.user.isHttpsDocumentUrl
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 서버가 제공한 HTTPS 원문을 플랫폼의 외부 문서 열기로 전달한다. */
@Composable
fun TermsRoute(termId: String, onClose: () -> Unit, viewModel: TermsViewModel = koinViewModel { parametersOf(termId) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val opener = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.load() }
    TermsScreen(state, onClose, onOpen = {
        val url = state.term?.contentUrl
        viewModel.opened(url != null && isHttpsDocumentUrl(url) && runCatching { opener.openUri(url) }.isSuccess)
    }, onRetry = { scope.launch { viewModel.load() } }, widthClass = currentWindowLayout().widthClass)
}

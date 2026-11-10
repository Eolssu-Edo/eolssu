package com.ssafy.eolssu.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 약관 상세(AUTH-02). 들어오면 서버 약관 본문을 받아 바로 보여 주고, 닫으면 약관 동의 화면으로 돌아간다. */
@Composable
fun TermsRoute(termId: String, onClose: () -> Unit, viewModel: TermsViewModel = koinViewModel { parametersOf(termId) }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.load() }
    TermsScreen(state, onClose, onRetry = { scope.launch { viewModel.load() } }, widthClass = currentWindowLayout().widthClass)
}

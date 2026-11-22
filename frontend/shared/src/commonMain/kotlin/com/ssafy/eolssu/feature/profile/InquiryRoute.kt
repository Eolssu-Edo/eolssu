package com.ssafy.eolssu.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** 마이페이지 문의하기 연결(S15P31E102-1240). 보내면 [onSent]로 마이페이지에 돌아간다. */
@Composable
fun InquiryRoute(
    onBack: () -> Unit,
    onSent: () -> Unit,
    viewModel: InquiryViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InquiryScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onTitleChange = viewModel::changeTitle,
        onContentChange = viewModel::changeContent,
        onSend = { scope.launch { if (viewModel.send()) onSent() } },
    )
}

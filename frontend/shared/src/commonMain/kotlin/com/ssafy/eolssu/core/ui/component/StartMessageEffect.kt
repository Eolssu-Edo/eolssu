package com.ssafy.eolssu.core.ui.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 이전 화면이 남긴 안내([message])를 화면에 들어올 때 한 번 띄운다. 띄우자마자 [onShown]으로 지우게 해
 * 다시 그리거나 화면을 돌려도 반복하지 않는다. 안내가 지워져도 이미 뜬 안내는 끝까지 보인다.
 */
@Composable
fun StartMessageEffect(snackbarHostState: SnackbarHostState, message: StringResource?, onShown: () -> Unit) {
    val scope = rememberCoroutineScope()
    val text = message?.let { stringResource(it) }
    LaunchedEffect(text) {
        if (text == null) return@LaunchedEffect
        onShown()
        scope.launch { snackbarHostState.showSnackbar(text) }
    }
}

package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_loading
import eolssu.shared.generated.resources.learn_reset_failed
import eolssu.shared.generated.resources.learn_retry
import eolssu.shared.generated.resources.learn_save_failed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * 서버에서 받는 학습 내용(단계 목록·단계 상세)의 받는 중·실패 표시. 실패하면 [failedMessage]와 '다시 시도'가 보인다.
 */
@Composable
internal fun LearnLoadStatus(failed: Boolean, failedMessage: StringResource, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (failed) {
            Text(
                stringResource(failedMessage),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            EolssuButton(stringResource(Res.string.learn_retry), onRetry, style = EolssuButtonStyle.Secondary, small = true)
        } else {
            val loading = stringResource(Res.string.learn_loading)
            CircularProgressIndicator(
                Modifier.size(32.dp).semantics { contentDescription = loading },
                color = colors.text,
                trackColor = colors.surfaceStrong,
                strokeWidth = 3.dp,
            )
        }
    }
}

/** 완료 표시를 서버에 남기지 못해 되돌렸으면 안내를 띄우고 [onShown]으로 지운다. */
@Composable
internal fun LearnSaveErrorEffect(error: LearnSaveError?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect
        onShown()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(getString(if (error == LearnSaveError.Reset) Res.string.learn_reset_failed else Res.string.learn_save_failed))
    }
}

package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.analysis_back_to_practice
import eolssu.shared.generated.resources.analysis_failed_decode_error
import eolssu.shared.generated.resources.analysis_failed_no_sound
import eolssu.shared.generated.resources.analysis_failed_server_error
import eolssu.shared.generated.resources.analysis_failed_timeout
import eolssu.shared.generated.resources.analysis_failed_too_short
import eolssu.shared.generated.resources.analysis_failed_upload_timeout
import eolssu.shared.generated.resources.analysis_failed_title
import eolssu.shared.generated.resources.analysis_play_again
import eolssu.shared.generated.resources.analysis_resend
import eolssu.shared.generated.resources.analysis_retry
import eolssu.shared.generated.resources.analysis_running_body
import eolssu.shared.generated.resources.analysis_running_title
import eolssu.shared.generated.resources.analysis_title
import eolssu.shared.generated.resources.analysis_upload_failed_title
import eolssu.shared.generated.resources.analysis_upload_kept
import eolssu.shared.generated.resources.analysis_upload_rejected
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** ANALYSIS-01 연결. 분석이 끝나면 [onResult]로 결과 화면에 간다. */
@Composable
fun AnalysisRoute(
    songId: String,
    bpm: Int,
    onResult: (evaluationId: String) -> Unit,
    onBackToPractice: () -> Unit,
    viewModel: AnalysisViewModel = koinViewModel(key = "analysis-$songId-$bpm") { parametersOf(songId, bpm) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.evaluationId) { state.evaluationId?.let(onResult) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.resume() }
    AnalysisScreen(
        state,
        wide = currentWindowLayout().widthClass != WindowWidthClass.Compact,
        onRetry = viewModel::retry,
        onResend = viewModel::resend,
        onBackToPractice = onBackToPractice,
    )
}

/**
 * ANALYSIS-01 분석 대기·실패. 가운데 내용 하나라 태블릿([wide])도 배치는 같고 화면만 넓으며, 버튼 최대 너비가 360dp다.
 * 실패하면 사유별 안내를 보이고, 같은 녹음으로 다시 분석할 수 있으면 '분석 다시 시도', 아니면 '다시 연주하기'를 준다.
 * 녹음을 보내지 못했으면 기기에 남은 녹음을 '녹음 다시 보내기'로 보내게 하고, 보낼 수 없는 파일이면 '다시 연주하기'만 준다.
 */
@Composable
fun AnalysisScreen(
    state: AnalysisUiState,
    wide: Boolean,
    onRetry: () -> Unit,
    onBackToPractice: () -> Unit,
    onResend: () -> Unit = {},
) {
    val colors = EolssuTheme.colors
    val failure = state.failure
    val upload = state.upload
    val stopped = failure != null || upload != null
    val maxWidth = if (wide) WindowBreakpoints.ContentMaxWidth else CompactContentMaxWidth
    EolssuScaffold(
        contentMaxWidth = maxWidth,
        topBar = { EolssuTopBar(stringResource(Res.string.analysis_title), onBack = null, maxWidth = maxWidth) },
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            if (stopped) {
                Box(Modifier.padding(bottom = 10.dp).size(56.dp).background(colors.recSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(EolssuIcons.Alert, contentDescription = null, modifier = Modifier.size(26.dp), tint = colors.rec)
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.padding(bottom = 10.dp).size(56.dp),
                    color = colors.text,
                    trackColor = colors.surfaceStrong,
                    strokeWidth = 3.dp,
                )
            }
            Text(
                stringResource(
                    when {
                        upload != null -> Res.string.analysis_upload_failed_title
                        failure != null -> Res.string.analysis_failed_title
                        else -> Res.string.analysis_running_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            state.song?.let {
                Text(
                    it.title,
                    modifier = Modifier.background(colors.surface, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                )
            }
            Text(
                stringResource(upload?.messageRes() ?: failure?.messageRes() ?: Res.string.analysis_running_body),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.7.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            if (stopped) {
                Column(
                    Modifier.padding(top = 10.dp).widthIn(max = if (wide) 360.dp else 320.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (upload == UploadProblem.CanResend) {
                        EolssuButton(stringResource(Res.string.analysis_resend), onResend, busy = state.resending)
                        EolssuButton(
                            stringResource(Res.string.analysis_back_to_practice), onBackToPractice,
                            style = EolssuButtonStyle.Secondary, enabled = !state.resending,
                        )
                    } else if (upload == null && failure?.retryable == true) {
                        EolssuButton(stringResource(Res.string.analysis_retry), onRetry, busy = state.retrying)
                        EolssuButton(
                            stringResource(Res.string.analysis_back_to_practice), onBackToPractice,
                            style = EolssuButtonStyle.Secondary, enabled = !state.retrying,
                        )
                    } else {
                        EolssuButton(stringResource(Res.string.analysis_play_again), onBackToPractice)
                    }
                }
            }
        }
    }
}

/** 녹음을 보내지 못한 사유별 안내 문구 리소스. */
internal fun UploadProblem.messageRes(): StringResource = when (this) {
    UploadProblem.CanResend -> Res.string.analysis_upload_kept
    UploadProblem.Rejected -> Res.string.analysis_upload_rejected
}

/** 실패 사유별 안내 문구 리소스. */
internal fun AnalysisFailure.messageRes(): StringResource = when (this) {
    AnalysisFailure.NoSound -> Res.string.analysis_failed_no_sound
    AnalysisFailure.TooShort -> Res.string.analysis_failed_too_short
    AnalysisFailure.DecodeError -> Res.string.analysis_failed_decode_error
    AnalysisFailure.UploadTimeout -> Res.string.analysis_failed_upload_timeout
    AnalysisFailure.Timeout -> Res.string.analysis_failed_timeout
    AnalysisFailure.ServerError -> Res.string.analysis_failed_server_error
}

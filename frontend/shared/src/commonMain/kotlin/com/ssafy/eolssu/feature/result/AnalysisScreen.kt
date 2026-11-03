package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.result.component.AnalysisFailedFigure
import com.ssafy.eolssu.feature.result.component.AnalyzingFigure
import com.ssafy.eolssu.feature.result.component.BoardTopBar
import com.ssafy.eolssu.feature.result.component.LiteBadge
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.analysis_back_to_practice
import eolssu.shared.generated.resources.analysis_failed_board
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
import eolssu.shared.generated.resources.analysis_retry_busy
import eolssu.shared.generated.resources.analysis_running_body
import eolssu.shared.generated.resources.analysis_running_title
import eolssu.shared.generated.resources.analysis_title
import eolssu.shared.generated.resources.analysis_upload_failed_title
import eolssu.shared.generated.resources.analysis_upload_kept
import eolssu.shared.generated.resources.analysis_upload_missing
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
    take: RecordedTake?,
    pendingSessionId: String?,
    onResult: (evaluationId: String) -> Unit,
    onBackToPractice: () -> Unit,
    onExit: (AnalysisExit) -> Unit,
    viewModel: AnalysisViewModel = koinViewModel(key = "analysis-$songId-$bpm-${take?.key ?: pendingSessionId}") {
        parametersOf(songId, bpm, take, pendingSessionId)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.evaluationId) { state.evaluationId?.let(onResult) }
    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.resume() }
    AnalysisScreen(
        state,
        wide = currentWindowLayout().widthClass != WindowWidthClass.Compact,
        onRetry = viewModel::retry,
        onResend = viewModel::resend,
        onBackToPractice = onBackToPractice,
        onRetrySong = viewModel::retrySong,
    )
}

/**
 * ANALYSIS-01 분석 대기·실패(연두 보드 Analysis · Analysis-Failed). 가운데 내용 하나라 태블릿([wide])도 배치는 같고 화면만 넓으며,
 * 버튼 최대 너비가 360dp다.
 *
 * - 대기: 도는 붓 일원상과 호랑이 · ‘연주를 확인하고 있어요.’(Song Myung 22sp) · 곡 이름 배지 · 안내.
 * - 실패: 다시 해 보는 호랑이와 붉은 경고 원 · 제목 · 곡 이름 배지 · 안내. 다시 분석할 수 있는 실패(서버·시간 초과)는
 *   보드 문장(‘네트워크나 서버 문제로…’) 아래에 사유 한 줄을 덧붙이고, 녹음 자체의 문제는 보드 문장이 사실과 달라 사유만 보인다.
 * - 같은 녹음으로 다시 분석할 수 있으면 ‘분석 다시 시도’, 아니면 ‘다시 연주하기’. 녹음을 보내지 못했으면 기기에 남은 녹음을
 *   ‘녹음 다시 보내기’로 보내게 하고, 보낼 수 없는 파일이면 ‘다시 연주하기’만 준다.
 */
@Composable
fun AnalysisScreen(
    state: AnalysisUiState,
    wide: Boolean,
    onRetry: () -> Unit,
    onBackToPractice: () -> Unit,
    onResend: () -> Unit = {},
    onRetrySong: () -> Unit = {},
) {
    if (state.songLookup != SongLookup.Ready) {
        EolssuScaffold(topBar = { EolssuTopBar(stringResource(Res.string.analysis_title), onBack = onBackToPractice) }) {
            SongLookupContent(state.songLookup, onRetrySong, onBackToPractice)
        }
        return
    }
    val colors = EolssuTheme.colors
    val failure = state.failure
    val upload = state.upload
    val stopped = failure != null || upload != null
    val maxWidth = if (wide) WindowBreakpoints.ContentMaxWidth else CompactContentMaxWidth
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.3.sp)
    EolssuScaffold(
        contentMaxWidth = maxWidth,
        topBar = { BoardTopBar(stringResource(Res.string.analysis_title), maxWidth = maxWidth) },
    ) {
        // 보드 `.body`는 가운데 정렬(아래 80)이다. 글꼴을 키워 넘치면 세로로 스크롤한다.
        BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(start = 20.dp, end = 20.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            if (stopped) AnalysisFailedFigure() else AnalyzingFigure()
            Text(
                stringResource(
                    when {
                        upload != null -> Res.string.analysis_upload_failed_title
                        failure != null -> Res.string.analysis_failed_title
                        else -> Res.string.analysis_running_title
                    },
                ),
                modifier = Modifier
                    .padding(top = if (stopped) 0.dp else 8.dp)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 28.6.sp),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            state.song?.let { LiteBadge(it.title) }
            val lines = when {
                upload != null -> listOf(stringResource(upload.messageRes()))
                failure != null && failure.retryable -> listOf(stringResource(Res.string.analysis_failed_board), stringResource(failure.messageRes()))
                failure != null -> listOf(stringResource(failure.messageRes()))
                else -> listOf(stringResource(Res.string.analysis_running_body))
            }
            Text(lines.joinToString("\n"), style = bodyStyle, color = colors.textSub, textAlign = TextAlign.Center)
            if (failure != null && state.retryBusy) {
                Text(stringResource(Res.string.analysis_retry_busy), style = bodyStyle, color = colors.recText, textAlign = TextAlign.Center)
            }
            if (stopped) {
                Column(
                    Modifier.padding(top = 10.dp).widthIn(max = if (wide) 360.dp else maxWidth).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
}

/** 녹음을 보내지 못한 사유별 안내 문구 리소스. */
internal fun UploadProblem.messageRes(): StringResource = when (this) {
    UploadProblem.CanResend -> Res.string.analysis_upload_kept
    UploadProblem.Rejected -> Res.string.analysis_upload_rejected
    UploadProblem.Missing -> Res.string.analysis_upload_missing
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

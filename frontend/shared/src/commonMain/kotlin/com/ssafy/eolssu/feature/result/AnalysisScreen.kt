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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import eolssu.shared.generated.resources.analysis_failed_body
import eolssu.shared.generated.resources.analysis_failed_title
import eolssu.shared.generated.resources.analysis_retry
import eolssu.shared.generated.resources.analysis_running_body
import eolssu.shared.generated.resources.analysis_running_title
import eolssu.shared.generated.resources.analysis_title
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
    AnalysisScreen(
        state,
        wide = currentWindowLayout().widthClass != WindowWidthClass.Compact,
        onRetry = viewModel::retry,
        onBackToPractice = onBackToPractice,
    )
}

/**
 * ANALYSIS-01 분석 대기·실패. 가운데 내용 하나라 태블릿([wide])도 배치는 같고 화면만 넓으며, 버튼 최대 너비가 360dp다.
 */
@Composable
fun AnalysisScreen(state: AnalysisUiState, wide: Boolean, onRetry: () -> Unit, onBackToPractice: () -> Unit) {
    val colors = EolssuTheme.colors
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
            if (state.failed) {
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
                stringResource(if (state.failed) Res.string.analysis_failed_title else Res.string.analysis_running_title),
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
                stringResource(if (state.failed) Res.string.analysis_failed_body else Res.string.analysis_running_body),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.7.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            if (state.failed) {
                Column(
                    Modifier.padding(top = 10.dp).widthIn(max = if (wide) 360.dp else 320.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EolssuButton(stringResource(Res.string.analysis_retry), onRetry, busy = state.retrying)
                    EolssuButton(
                        stringResource(Res.string.analysis_back_to_practice), onBackToPractice,
                        style = EolssuButtonStyle.Secondary, enabled = !state.retrying,
                    )
                }
            }
        }
    }
}

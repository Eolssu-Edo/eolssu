package com.ssafy.eolssu.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.score.scoreSectionLabel
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.SectionMetricChange
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_save
import eolssu.shared.generated.resources.result_score_unit
import eolssu.shared.generated.resources.weak_load_missing
import eolssu.shared.generated.resources.weak_result_after
import eolssu.shared.generated.resources.weak_result_again
import eolssu.shared.generated.resources.weak_result_before
import eolssu.shared.generated.resources.weak_result_down
import eolssu.shared.generated.resources.weak_result_next_ready
import eolssu.shared.generated.resources.weak_result_next_slow
import eolssu.shared.generated.resources.weak_result_next_title
import eolssu.shared.generated.resources.weak_result_other
import eolssu.shared.generated.resources.weak_result_previous
import eolssu.shared.generated.resources.weak_result_previous_sample
import eolssu.shared.generated.resources.weak_result_same
import eolssu.shared.generated.resources.weak_result_subtitle
import eolssu.shared.generated.resources.weak_result_title
import eolssu.shared.generated.resources.weak_result_up
import eolssu.shared.generated.resources.weak_result_whole
import org.jetbrains.compose.resources.stringResource

/** 구간 재평가 결과에서 부르는 동작. */
class SectionResultActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onPracticeAgain: () -> Unit,
    val onOtherSection: () -> Unit,
    val onWholeSong: () -> Unit,
)

/**
 * RESULT-04 구간 재평가 결과(2차 `SectionResult`): 총점 · 이전 대비 알약 · 항목별 이전 → 이번 막대 · 다음 단계.
 * 2차 결정으로 ‘남은 오류’ 칸은 없다. 아래에 ‘다시 연습’과 ‘다른 구간 고르기’ · ‘곡 전체 다시 평가’.
 */
@Composable
fun SectionResultScreen(state: SectionResultUiState, snackbarHostState: SnackbarHostState, actions: SectionResultActions) {
    val colors = EolssuTheme.colors
    val evaluation = state.evaluation
    EolssuScaffold(
        containerColor = colors.surface,
        snackbarHostState = snackbarHostState,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.weak_result_title),
                onBack = actions.onBack,
                subtitle = evaluation?.let {
                    stringResource(Res.string.weak_result_subtitle, it.song.title, scoreSectionLabel(it.section, it.song.score), it.bpm)
                },
                right = { EolssuIconButton(EolssuIcons.Bookmark, stringResource(Res.string.result_save), actions.onSave) },
            )
        },
        bottomBar = if (evaluation != null) {
            {
                EolssuButton(stringResource(Res.string.weak_result_again), actions.onPracticeAgain)
                // 둘 다 흰 바탕에 옅은 테두리(`#E8E4DA`)다.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EolssuButton(
                        stringResource(Res.string.weak_result_other), actions.onOtherSection, Modifier.weight(1f),
                        style = EolssuButtonStyle.Secondary, compact = true, borderColor = colors.lineSoft,
                    )
                    EolssuButton(
                        stringResource(Res.string.weak_result_whole), actions.onWholeSong, Modifier.weight(1f),
                        style = EolssuButtonStyle.Secondary, compact = true, borderColor = colors.lineSoft,
                    )
                }
            }
        } else {
            null
        },
    ) {
        if (evaluation == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(Res.string.weak_load_missing))
            }
            return@EolssuScaffold
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            ScoreHeader(evaluation, state.delta)
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                evaluation.metrics.forEach { MetricChangeCard(it) }
            }
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().border(1.dp, colors.lineSoft, shape).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    stringResource(Res.string.weak_result_next_title),
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    if (evaluation.bpm < evaluation.song.baseBpm) {
                        stringResource(Res.string.weak_result_next_slow, evaluation.song.baseBpm, WholeSongReadyScore)
                    } else {
                        stringResource(Res.string.weak_result_next_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                )
            }
        }
    }
}

/** 총점(60sp, ‘85점’)과 ‘이전보다 17점 올랐어요’ 연두 알약, ‘이전 이 구간 점수 68점 · 예시 결과’. */
@Composable
private fun ScoreHeader(evaluation: SectionEvaluation, delta: Int?) {
    val colors = EolssuTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                append("${evaluation.total}")
                withStyle(SpanStyle(fontSize = 18.sp, color = colors.textMuted, fontWeight = FontWeight.Medium)) {
                    append(stringResource(Res.string.result_score_unit))
                }
            },
            style = EolssuTheme.serif.brand.copy(fontSize = 60.sp, lineHeight = 60.sp),
        )
        if (delta != null) {
            Row(
                Modifier.padding(top = 10.dp).heightIn(min = 28.dp).background(if (delta > 0) colors.primary else colors.surfaceStrong, CircleShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (delta > 0) Icon(EolssuIcons.ArrowUpRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onPrimary)
                Text(
                    when {
                        delta > 0 -> stringResource(Res.string.weak_result_up, delta)
                        delta < 0 -> stringResource(Res.string.weak_result_down, -delta)
                        else -> stringResource(Res.string.weak_result_same)
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                )
            }
        }
        val previous = evaluation.previousTotal
        if (previous != null) {
            Text(
                stringResource(if (evaluation.sample) Res.string.weak_result_previous_sample else Res.string.weak_result_previous, previous),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textMuted,
            )
        }
    }
}

/** 항목 카드: 이름 · ‘70 → 86점’ · 이전(회색)·이번(연두) 6dp 막대. */
@Composable
private fun MetricChangeCard(change: SectionMetricChange) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val unit = stringResource(Res.string.result_score_unit)
    Column(Modifier.fillMaxWidth().border(1.dp, colors.lineSoft, shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(metricLabel(change.metric), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(
                buildAnnotatedString {
                    change.before?.let {
                        withStyle(SpanStyle(color = colors.textMuted)) { append("$it") }
                        append(" → ")
                    }
                    withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) { append("${change.after}") }
                    append(unit)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            )
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            change.before?.let { MetricBar(stringResource(Res.string.weak_result_before), it, colors.textDisabled) }
            MetricBar(stringResource(Res.string.weak_result_after), change.after, colors.accent)
        }
    }
}

@Composable
private fun MetricBar(label: String, score: Int, color: androidx.compose.ui.graphics.Color) {
    val colors = EolssuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textMuted)
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.background)) {
            Box(Modifier.fillMaxWidth(score.coerceIn(0, 100) / 100f).fillMaxHeight().background(color))
        }
    }
}

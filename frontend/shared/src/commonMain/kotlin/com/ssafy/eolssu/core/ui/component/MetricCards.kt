package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_metric_length
import eolssu.shared.generated.resources.result_metric_pitch
import eolssu.shared.generated.resources.result_metric_rhythm
import eolssu.shared.generated.resources.result_metric_weak
import eolssu.shared.generated.resources.result_score_unit
import org.jetbrains.compose.resources.stringResource

/*
 * 평가 항목 점수 카드. 평가 결과와 이전 연주 결과(연습 기록)가 함께 쓴다.
 */

private val CardShape = RoundedCornerShape(16.dp)

/** 보드 항목 세 칸(`.card` × 3, 사이 10): 이름(13sp) · ‘더 연습’ · 점수(22sp + ‘점’ 12sp) · 5dp 막대. */
@Composable
fun MetricCards(result: EvaluationResult, weak: Set<EvaluationMetric>, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        result.metrics.forEach { metric ->
            Column(
                Modifier.weight(1f).fillMaxHeight().background(colors.surface, CardShape).border(1.dp, colors.lineSoft, CardShape).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metricLabel(metric.metric),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                    if (metric.metric in weak) WeakBadge()
                }
                Text(
                    buildAnnotatedString {
                        append("${metric.score}")
                        withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = colors.textMuted)) {
                            append(stringResource(Res.string.result_score_unit))
                        }
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                        fontSize = 22.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.text,
                )
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceStrong)) {
                    Box(Modifier.fillMaxWidth(metric.score.coerceIn(0, 100) / 100f).fillMaxHeight().background(colors.primary))
                }
            }
        }
    }
}

/** 보드 `.lv.l2`(20dp): ‘더 연습’. */
@Composable
private fun WeakBadge() {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(20.dp).background(colors.accentTintStrong, shape).border(1.dp, colors.accentTintLine, shape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.result_metric_weak),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
            maxLines = 1,
        )
    }
}

@Composable
fun metricLabel(metric: EvaluationMetric): String = stringResource(
    when (metric) {
        EvaluationMetric.Pitch -> Res.string.result_metric_pitch
        EvaluationMetric.Rhythm -> Res.string.result_metric_rhythm
        EvaluationMetric.Length -> Res.string.result_metric_length
    },
)

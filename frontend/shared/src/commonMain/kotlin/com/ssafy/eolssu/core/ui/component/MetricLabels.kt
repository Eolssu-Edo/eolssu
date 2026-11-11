package com.ssafy.eolssu.core.ui.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.EvaluationMetric
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.result_metric_length
import eolssu.shared.generated.resources.result_metric_pitch
import eolssu.shared.generated.resources.result_metric_rhythm
import org.jetbrains.compose.resources.stringResource

/*
 * 평가 항목 이름. 점수 상자([MetricBars])와 이전 연주와 비교가 함께 쓴다.
 */

/** 평가 항목 이름(음정 · 박자 · 음 길이). */
@Composable
fun metricLabel(metric: EvaluationMetric): String = stringResource(
    when (metric) {
        EvaluationMetric.Pitch -> Res.string.result_metric_pitch
        EvaluationMetric.Rhythm -> Res.string.result_metric_rhythm
        EvaluationMetric.Length -> Res.string.result_metric_length
    },
)

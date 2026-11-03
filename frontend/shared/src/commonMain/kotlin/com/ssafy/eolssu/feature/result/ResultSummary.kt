package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult

/**
 * 결과 카드의 ‘더 연습’ 표시와 앱 총평을 정하는 값(보드 RESULT-02). 서버 총평([EvaluationResult.feedback])이 있으면 화면이 그것을 먼저 쓴다.
 *
 * @property weak ‘더 연습’을 붙일 항목. 세 항목 중 가장 낮은 점수(같으면 모두)이고, 세 점수가 모두 같으면 비어 있다.
 * @property focus 앱 총평이 말할 항목. [weak] 중 음정 → 박자 → 음 길이 순으로 첫 항목. 없으면 null.
 * @property good 총점이 [GoodScore] 이상이면 ‘좋아요!’로 시작하는 총평을 쓴다.
 */
data class ResultSummary(
    val weak: Set<EvaluationMetric>,
    val focus: EvaluationMetric?,
    val good: Boolean,
) {
    companion object {
        /** ‘좋아요!’ 총평을 쓰는 총점 하한. */
        const val GoodScore = 80
    }
}

fun EvaluationResult.summary(): ResultSummary {
    val scores = metrics.map { it.score }
    val min = scores.minOrNull()
    val max = scores.maxOrNull()
    val weak = if (min == null || min == max) emptySet() else metrics.filter { it.score == min }.map { it.metric }.toSet()
    return ResultSummary(
        weak = weak,
        focus = EvaluationMetric.entries.firstOrNull { it in weak },
        good = total >= ResultSummary.GoodScore,
    )
}

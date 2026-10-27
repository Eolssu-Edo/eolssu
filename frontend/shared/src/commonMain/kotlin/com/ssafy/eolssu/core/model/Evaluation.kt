package com.ssafy.eolssu.core.model

/** 평가 항목. 와이어프레임 API 정리의 평가 항목 키 pitch·rhythm·length와 같다. */
enum class EvaluationMetric { Pitch, Rhythm, Length }

/** 항목 점수(0~100 정수). */
data class MetricScore(val metric: EvaluationMetric, val score: Int)

/** 틀린 정간 하나. 정간보의 각·정간 위치와 틀린 항목. */
data class ScoreError(val column: Int, val row: Int, val metric: EvaluationMetric)

/**
 * 연주 평가 결과. 점수 단위·항목 키는 서버 결과를 그대로 쓰고 화면에서 다시 계산하지 않는다.
 *
 * @property feedback 총평 한 줄(서버가 준다).
 * @property sample 예시 결과인지. 실제 분석이 붙기 전에는 true로 화면에 '예시 결과'를 표시한다.
 */
data class EvaluationResult(
    val id: String,
    val songId: String,
    val total: Int,
    val feedback: String,
    val metrics: List<MetricScore>,
    val errors: List<ScoreError>,
    val bpm: Int,
    val recordedSeconds: Int,
    val recordedAt: KoreanDateTime,
    val sample: Boolean,
) {
    /**
     * 틀린 칸이 가장 많이 몰린 이웃한 두 각(0부터)과 그 수. 틀린 칸이 없으면 null.
     * 결과 화면의 '틀린 칸 14개 중 9개가 3–4각에 몰려 있어요' 문구에 쓴다.
     */
    fun densestColumnPair(): Pair<Int, Int>? {
        if (errors.isEmpty()) return null
        val byColumn = errors.groupingBy { it.column }.eachCount()
        val last = byColumn.keys.max()
        return (0..maxOf(0, last - 1))
            .map { c -> c to (byColumn[c] ?: 0) + (byColumn[c + 1] ?: 0) }
            .maxBy { it.second }
    }
}

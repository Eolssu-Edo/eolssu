package com.ssafy.eolssu.core.model

/** 정간보의 정간 하나(각·정간 번호, 0부터). 연주 순서(각 → 정간)로 비교한다. */
data class ScorePosition(val column: Int, val row: Int) : Comparable<ScorePosition> {
    override fun compareTo(other: ScorePosition): Int = compareValuesBy(this, other, { it.column }, { it.row })
}

/**
 * 취약 구간 연습에서 고른 구간. [start]부터 [end]까지(둘 다 포함) 연주 순서로 이어진 정간이다.
 * 구간 반복·재평가·이전 연주 비교가 함께 쓴다.
 */
data class ScoreSection(val start: ScorePosition, val end: ScorePosition) {
    init {
        require(start <= end) { "구간의 처음은 끝보다 앞이어야 한다: $start > $end" }
    }

    operator fun contains(position: ScorePosition): Boolean = position in start..end

    fun contains(column: Int, row: Int): Boolean = ScorePosition(column, row) in this

    /** 구간이 걸친 각. */
    val columns: IntRange get() = start.column..end.column

    /** 처음부터 끝까지 정간을 모두 덮는 각. 이 각만 취약 구간 테두리를 두른다. 없으면 null. */
    fun fullColumns(score: Score): IntRange? {
        val full = columns.filter { c ->
            c in score.columns.indices && ScorePosition(c, 0) in this && ScorePosition(c, score.columns[c].lastIndex) in this
        }
        return if (full.isEmpty()) null else full.first()..full.last()
    }

    /** 구간의 정간을 연주 순서로 나열한다. */
    fun positions(score: Score): List<ScorePosition> = columns.flatMap { c ->
        (score.columns.getOrNull(c)?.indices ?: IntRange.EMPTY).map { ScorePosition(c, it) }.filter { it in this }
    }

    companion object {
        /** 두 정간을 순서와 관계없이 이어 구간으로 만든다. */
        fun between(a: ScorePosition, b: ScorePosition): ScoreSection = ScoreSection(minOf(a, b), maxOf(a, b))

        /** [first]~[last] 각 전체. */
        fun ofColumns(score: Score, first: Int, last: Int): ScoreSection =
            ScoreSection(ScorePosition(first, 0), ScorePosition(last, score.columns[last].lastIndex))
    }
}

/** 틀린 정간이 가장 많이 몰린 이웃한 두 각 전체(추천 취약 구간). 틀린 정간이 없으면 null. */
fun EvaluationResult.recommendedSection(score: Score): ScoreSection? {
    val (first, _) = densestColumnPair() ?: return null
    if (score.columns.isEmpty()) return null
    val last = minOf(first + 1, score.columns.lastIndex)
    return ScoreSection.ofColumns(score, minOf(first, last), last)
}

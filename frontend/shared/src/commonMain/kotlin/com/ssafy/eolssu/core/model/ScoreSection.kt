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

    /** 처음부터 끝까지 정간을 모두 덮는 각. 이 각만 각 표시(각 머리·테두리)를 강조한다. 없으면 null. */
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

/**
 * 율명 음 번호 [notes](양 끝 포함, 서버 `noteIdx`)를 연주한 정간 구간. 첫 음의 정간부터 마지막 음이 이음으로 이어지는 마지막 정간까지다.
 * 구간 앞뒤의 쉼·빈 정간은 넣지 않는다. 악보에 그 음이 없으면 null.
 */
fun Song.notesSection(notes: IntRange): ScoreSection? {
    val events = timeline.filter { it.noteIndex != null && it.noteIndex in notes }
    if (events.isEmpty()) return null
    val first = events.minBy { it.noteIndex!! }
    val last = events.maxBy { it.noteIndex!! }
    val end = displayUnits.filter { it.event.index == last.index }.maxOfOrNull { ScorePosition(it.column, it.row) }
    return ScoreSection.between(ScorePosition(first.column, first.row), end ?: ScorePosition(last.column, last.row))
}

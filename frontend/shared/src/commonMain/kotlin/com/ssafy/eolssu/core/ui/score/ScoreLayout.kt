package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreEvent

/**
 * 화면에 그리는 세로 줄 하나. 한 각이 [Jangdan.strips]에 따라 여러 줄로 나뉠 수 있다.
 *
 * @property strip 같은 각 안에서의 줄 순서. 0이면 각 번호를 적는 머리 칸이 있다.
 * @property rows 이 줄에 그리는 정간 번호 범위.
 */
internal data class ScoreStrip(val column: Int, val strip: Int, val rows: IntRange)

/**
 * 연주 순서(각 0의 첫 줄부터)로 나열한 줄 목록. 화면에는 이 목록을 오른쪽에서 왼쪽으로 놓는다.
 *
 * @param maxColumns 앞에서부터 이 수의 각만 그린다. 미리보기용.
 * @param maxRows 각마다 첫 정간부터 이 수만 한 줄로 그린다. 표기 설정 예시 칸용.
 */
internal fun scoreStrips(score: Score, jangdan: Jangdan, maxColumns: Int? = null, maxRows: Int? = null): List<ScoreStrip> {
    val columnCount = minOf(maxColumns ?: score.columns.size, score.columns.size)
    return (0 until columnCount).flatMap { column ->
        val size = score.columns[column].size
        if (maxRows != null) {
            listOf(ScoreStrip(column, 0, 0 until minOf(maxRows, size)))
        } else {
            val strips = mutableListOf<ScoreStrip>()
            var start = 0
            for (count in jangdan.strips) {
                if (start >= size) break
                strips += ScoreStrip(column, strips.size, start until minOf(start + count, size))
                start += count
            }
            strips
        }
    }
}

/** 정간이 현재 연주 위치와 어떤 관계인지. */
internal enum class CellProgress { Upcoming, Current, Past }

/** [events]는 한 정간에 든 음들. 현재 위치가 없으면 모두 [CellProgress.Upcoming]이다. */
internal fun cellProgress(events: List<ScoreEvent>, currentIndex: Int?): CellProgress = when {
    currentIndex == null -> CellProgress.Upcoming
    events.any { it.index == currentIndex } -> CellProgress.Current
    events.all { it.index < currentIndex } -> CellProgress.Past
    else -> CellProgress.Upcoming
}

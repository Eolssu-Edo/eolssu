package com.ssafy.eolssu.backend.feature.evaluation.domain

/**
 * 저장된 음표 판정을 각(column) 단위로 모아 반복 연습할 구간을 고른다(EVA-03).
 * 틀린 칸은 오류 음표가 하나 이상 있는 정간이다. 이어진 [WINDOW_COLUMNS]각 안의 틀린 칸이 많은 순으로 고르고,
 * 같으면 앞 구간을 고른다. 고른 구간끼리는 각이 겹치지 않으며 양 끝의 틀린 칸 없는 각은 뺀다.
 */
object WeakSections {
    const val WINDOW_COLUMNS = 2

    /** [cells]는 곡 전체 NOTE 순번으로 접근하며 [endNoteIdx]까지 있어야 한다. */
    fun recommend(
        notes: List<PracticeEvaluationNote>, cells: List<NoteCell>, startNoteIdx: Int, endNoteIdx: Int, limit: Int,
    ): WeakSectionReport {
        require(limit > 0 && startNoteIdx in 0..endNoteIdx && endNoteIdx < cells.size)
        val errors = notes.filter { it.isError && it.noteIdx in startNoteIdx..endNoteIdx }
        val errorCellsByColumn = errors.groupBy({ cells[it.noteIdx].column }, { cells[it.noteIdx].cellId })
            .mapValues { it.value.toSet() }
        fun count(columns: IntRange) = columns.sumOf { errorCellsByColumn[it]?.size ?: 0 }
        fun trim(columns: IntRange): IntRange? {
            val withErrors = columns.filter { errorCellsByColumn.containsKey(it) }
            return if (withErrors.isEmpty()) null else withErrors.first()..withErrors.last()
        }

        val firstColumn = cells[startNoteIdx].column
        val lastColumn = cells[endNoteIdx].column
        val chosen = mutableListOf<IntRange>()
        while (chosen.size < limit) {
            chosen += (firstColumn..lastColumn)
                .map { it..minOf(it + WINDOW_COLUMNS - 1, lastColumn) }
                .filter { window -> chosen.none { it.first <= window.last && window.first <= it.last } }
                .mapNotNull(::trim)
                .maxWithOrNull(compareBy({ count(it) }, { -it.first }))
                ?: break
        }
        return WeakSectionReport(errorCellsByColumn.values.sumOf { it.size }, chosen.map { columns ->
            val noteIdxs = (startNoteIdx..endNoteIdx).filter { cells[it].column in columns }
            val windowErrors = errors.filter { cells[it.noteIdx].column in columns }
            WeakSection(noteIdxs.first(), noteIdxs.last(), columns.first, columns.last, count(columns), dominant(windowErrors))
        })
    }

    /** 가장 많은 판정. 같으면 회의 D2 순서(누락 > 음높이 > 박자)를 따른다. */
    private fun dominant(errors: List<PracticeEvaluationNote>): String =
        errors.groupingBy { it.verdict }.eachCount().entries
            .maxWith(compareBy({ it.value }, { PRIORITY.indexOf(it.key) }))
            .key

    private val PRIORITY = listOf(Verdict.TIMING, Verdict.PITCH, Verdict.MISSED)
}

data class WeakSectionReport(val totalErrorCellCount: Int, val sections: List<WeakSection>)

data class WeakSection(
    val startNoteIdx: Int, val endNoteIdx: Int, val startColumn: Int, val endColumn: Int,
    val errorCellCount: Int, val dominantError: String,
)

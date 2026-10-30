package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.ScoreError
import com.ssafy.eolssu.core.model.ScoreErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultErrorViewTest {

    private fun result(errors: List<ScoreError>) = EvaluationResult(
        id = "e", songId = "s", total = 80, feedback = "", metrics = emptyList(), errors = errors,
        bpm = 60, recordedSeconds = 10, recordedAt = KoreanDateTime(2026, 10, 20, 15, 20), sample = true,
    )

    private val errors = listOf(
        ScoreError(0, 4, EvaluationMetric.Rhythm),
        ScoreError(3, 1, EvaluationMetric.Pitch),
        ScoreError(2, 5, EvaluationMetric.Length),
        ScoreError(2, 2, EvaluationMetric.Pitch),
    )

    @Test
    fun filterCountsAndShowsOnlyMatchingErrors() {
        val r = result(errors)
        assertEquals(4, r.errorCount(ErrorFilter.All))
        assertEquals(2, r.errorCount(ErrorFilter.Pitch))
        assertEquals(1, r.errorCount(ErrorFilter.Rhythm))
        assertEquals(1, r.errorCount(ErrorFilter.Length))
        assertEquals(mapOf((3 to 1) to ScoreErrorKind.Pitch, (2 to 2) to ScoreErrorKind.Pitch), r.visibleErrors(ErrorFilter.Pitch))
        assertEquals(4, r.visibleErrors(ErrorFilter.All).size)
    }

    @Test
    fun errorAtIgnoresFilter() {
        val r = result(errors)
        assertEquals(ScoreErrorKind.Length, r.errorAt(ScoreCellPosition(2, 5)))
        assertNull(r.errorAt(ScoreCellPosition(1, 1)))
    }

    @Test
    fun defaultSelectionIsFirstErrorOfDensestPair() {
        // 2–3각(0부터)에 3개가 몰려 있고, 그중 연주 순서로 첫 오류는 2각 2정간이다.
        assertEquals(ScoreCellPosition(2, 2), result(errors).defaultSelectedCell())
    }

    @Test
    fun defaultSelectionWithoutErrorsIsFirstCell() {
        assertEquals(ScoreCellPosition(0, 0), result(emptyList()).defaultSelectedCell())
    }

    @Test
    fun missedNoteRemainsAnErrorWithoutBecomingLengthError() {
        val r = result(listOf(ScoreError(2, 3, ScoreErrorKind.Missed))).copy(sample = false)

        assertEquals(1, r.errorCount(ErrorFilter.All))
        assertEquals(1, r.errorCount(ErrorFilter.Missed))
        assertEquals(0, r.errorCount(ErrorFilter.Length))
        assertEquals(mapOf((2 to 3) to ScoreErrorKind.Missed), r.visibleErrors(ErrorFilter.All))
        assertEquals(mapOf((2 to 3) to ScoreErrorKind.Missed), r.visibleErrors(ErrorFilter.Missed))
        assertEquals(emptyMap(), r.visibleErrors(ErrorFilter.Length))
        assertEquals(ScoreErrorKind.Missed, r.errorAt(ScoreCellPosition(2, 3)))
        assertEquals(ScoreCellPosition(2, 3), r.defaultSelectedCell())
    }
}

package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreSectionTest {
    /** 각마다 정간 수가 다른 악보(3 · 2 · 3). */
    private val score = Score(listOf(3, 2, 3).map { size -> List(size) { ScoreCell(ScoreNote.Hold) } })

    @Test
    fun betweenOrdersPositionsInPlayingOrder() {
        val section = ScoreSection.between(ScorePosition(2, 0), ScorePosition(0, 2))
        assertEquals(ScorePosition(0, 2), section.start)
        assertEquals(ScorePosition(2, 0), section.end)
        assertTrue(section.contains(1, 1))
        assertFalse(section.contains(0, 1))
        assertFalse(section.contains(2, 1))
        assertFailsWith<IllegalArgumentException> { ScoreSection(ScorePosition(1, 0), ScorePosition(0, 0)) }
    }

    @Test
    fun fullColumnsOnlyCoverColumnsWhollyInside() {
        assertEquals(1..1, ScoreSection.between(ScorePosition(0, 2), ScorePosition(2, 0)).fullColumns(score))
        assertEquals(0..2, ScoreSection.ofColumns(score, 0, 2).fullColumns(score))
        assertNull(ScoreSection(ScorePosition(0, 1), ScorePosition(0, 2)).fullColumns(score))
    }

    @Test
    fun positionsFollowVariableColumnLengths() {
        val positions = ScoreSection.between(ScorePosition(0, 2), ScorePosition(2, 0)).positions(score)
        assertEquals(listOf(ScorePosition(0, 2), ScorePosition(1, 0), ScorePosition(1, 1), ScorePosition(2, 0)), positions)
    }

    @Test
    fun recommendedSectionIsTheDensestColumnPair() {
        val result = EvaluationResult(
            id = "e", songId = "s", total = 80, feedback = "", metrics = emptyList(),
            errors = listOf(ScoreError(1, 0, EvaluationMetric.Pitch), ScoreError(2, 1, EvaluationMetric.Rhythm), ScoreError(2, 2, EvaluationMetric.Length)),
            bpm = 60, recordedSeconds = 10, recordedAt = KoreanDateTime(2026, 10, 20, 15, 20), sample = true,
        )
        assertEquals(ScoreSection(ScorePosition(1, 0), ScorePosition(2, 2)), result.recommendedSection(score))
        assertNull(result.copy(errors = emptyList()).recommendedSection(score))
    }
}

package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SectionPracticeStateTest {
    private val recommended = ScoreSection(ScorePosition(2, 0), ScorePosition(3, 8))

    @Test
    fun firstTapStartsAndSecondTapClosesTheSectionInEitherOrder() {
        val start = SectionSelectUiState(loading = false, section = recommended)
        val picking = start.tap(ScorePosition(4, 3))
        assertEquals(ScoreSection(ScorePosition(4, 3), ScorePosition(4, 3)), picking.section)
        assertTrue(picking.picking)
        assertFalse(picking.canStart)

        val closed = picking.tap(ScorePosition(1, 7))
        assertEquals(ScoreSection(ScorePosition(1, 7), ScorePosition(4, 3)), closed.section)
        assertFalse(closed.picking)
        assertTrue(closed.canStart)

        // 다시 누르면 새 구간을 처음부터 고른다.
        assertEquals(ScoreSection(ScorePosition(0, 0), ScorePosition(0, 0)), closed.tap(ScorePosition(0, 0)).section)
    }

    @Test
    fun recommendedHintAllowsStartingOrReselectingAndResetRestoresIt() {
        val start = SectionSelectUiState(loading = false, section = recommended, recommended = recommended)
        assertEquals(SectionSelectStep.Recommended, start.step)
        assertTrue(start.canStart)

        val picking = start.tap(ScorePosition(4, 3))
        assertEquals(SectionSelectStep.End, picking.step)
        val ready = picking.tap(ScorePosition(5, 1))
        assertEquals(SectionSelectStep.Ready, ready.step)

        // 다시 누르면 끝 정간을 기다리는 단계로 돌아간다.
        assertEquals(SectionSelectStep.End, ready.tap(ScorePosition(0, 0)).step)

        val reset = picking.resetToRecommended()
        assertEquals(recommended, reset.section)
        assertFalse(reset.picking)
        assertEquals(SectionSelectStep.Recommended, reset.step)
        assertTrue(reset.canStart)
    }

    @Test
    fun withoutRecommendationWaitsForStartAndResetClearsSelection() {
        val start = SectionSelectUiState(loading = false)
        assertEquals(SectionSelectStep.Start, start.step)
        assertFalse(start.canStart)

        val picking = start.tap(ScorePosition(0, 0))
        assertEquals(SectionSelectStep.End, picking.step)
        assertFalse(picking.canStart)

        val ready = picking.tap(ScorePosition(1, 0))
        assertEquals(SectionSelectStep.Ready, ready.step)
        assertTrue(ready.canStart)

        val reset = ready.resetToRecommended()
        assertNull(reset.section)
        assertEquals(SectionSelectStep.Start, reset.step)
        assertFalse(reset.canStart)
    }
}

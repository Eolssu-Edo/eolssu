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
    fun repeatsAndSpeed() {
        val state = SectionSelectUiState(loading = false)
        assertEquals(5, state.repeats)
        assertNull(state.copy(repeat = SectionRepeat.Endless).repeats)
        assertEquals(1, state.copy(loop = false, repeat = SectionRepeat.Endless).repeats)
        assertEquals(48, TempoStep.Slow.bpm(60))
        assertEquals(36, TempoStep.Slowest.bpm(60))
        assertEquals(72, TempoStep.Fastest.bpm(60))
        assertEquals(TempoStep.Slow, state.speed)
    }

    private val cells = listOf(ScorePosition(2, 7), ScorePosition(2, 8), ScorePosition(3, 0))

    @Test
    fun loopAdvancesThroughRoundsAndFinishes() {
        var state = SectionLoopUiState(loading = false, cells = cells, repeats = 2, running = true)
        repeat(2) { state = state.advance() }
        assertEquals(2, state.step)
        state = state.advance()
        assertEquals(2, state.round)
        assertEquals(0, state.step)
        assertEquals(listOf(1f, 0f), state.roundProgress)
        repeat(3) { state = state.advance() }
        assertTrue(state.finished)
        assertFalse(state.running)
        assertEquals(listOf(1f, 1f), state.roundProgress)
        // 끝난 뒤에는 더 나아가지 않는다.
        assertEquals(state, state.advance())
    }

    @Test
    fun roundPercentFollowsStepAndFillsWhenFinished() {
        var state = SectionLoopUiState(loading = false, cells = cells, repeats = 1, running = true)
        assertEquals(0, state.roundPercent)
        state = state.advance()
        assertEquals(33, state.roundPercent)
        repeat(2) { state = state.advance() }
        assertTrue(state.finished)
        assertEquals(100, state.roundPercent)
        assertEquals(0, SectionLoopUiState().roundPercent)
    }

    @Test
    fun endlessLoopNeverFinishes() {
        var state = SectionLoopUiState(loading = false, cells = cells, repeats = null, running = true)
        repeat(30) { state = state.advance() }
        assertFalse(state.finished)
        assertEquals(11, state.round)
        assertEquals(1, state.roundProgress.size)
    }

    @Test
    fun countdownCountsThreeBeatsThenResumes() {
        var state = SectionLoopUiState(loading = false, cells = cells, repeats = 5, round = 2, step = 1, running = true, bpm = 48)
        state = state.pause().beginCountdown()
        assertEquals(3, state.countdown)
        assertTrue(state.resumed)
        assertFalse(state.running)
        state = state.countdownTick().countdownTick()
        assertEquals(1, state.countdown)
        state = state.countdownTick()
        assertNull(state.countdown)
        assertTrue(state.running)
        assertEquals(2, state.round)
        assertEquals(1, state.step)
    }

    @Test
    fun countdownAfterFinishingRestartsFromTheFirstRound() {
        val finished = SectionLoopUiState(loading = false, cells = cells, repeats = 3, round = 3, step = 2, finished = true)
        val restarted = finished.beginCountdown()
        assertEquals(1, restarted.round)
        assertEquals(0, restarted.step)
        assertFalse(restarted.finished)
        assertFalse(restarted.resumed)
    }
}

package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnUiStateTest {

    @Test
    fun startsWithDansoAndNothingDone() {
        val state = LearnUiState()
        assertEquals(GuideInstrument.Danso, state.instrument)
        assertEquals(0, state.requiredDoneCount)
        assertEquals(1, state.nextStage?.number)
    }

    @Test
    fun progressCountsRequiredStagesOnly() {
        val state = LearnUiState().toggleDone(1).toggleDone(2).toggleDone(9)

        assertEquals(setOf(1, 2, 9), state.doneStages)
        assertEquals(2, state.requiredDoneCount)
        assertEquals(0.25f, state.progress)
        assertEquals(3, state.nextStage?.number)
    }

    @Test
    fun nextStageIncludesOptionalStageAndIsNullWhenAllDone() {
        val required = LearnStages.filterNot { it.optional }.fold(LearnUiState()) { s, stage -> s.confirm(stage.number) }
        assertEquals(1f, required.progress)
        assertEquals(9, required.nextStage?.number)

        assertNull(required.confirm(9).nextStage)
    }

    @Test
    fun doneMarksAreKeptPerInstrument() {
        val danso = LearnUiState().toggleDone(1)
        val sogeum = danso.selectInstrument(GuideInstrument.Sogeum)
        assertTrue(sogeum.doneStages.isEmpty())

        val back = sogeum.toggleDone(3).selectInstrument(GuideInstrument.Danso)
        assertEquals(setOf(1), back.doneStages)
    }

    @Test
    fun toggleRemovesAndResetClearsOnlyCurrentInstrument() {
        val state = LearnUiState().toggleDone(4).toggleDone(4)
        assertFalse(state.isDone(4))

        val both = LearnUiState().confirm(1).selectInstrument(GuideInstrument.Sogeum).confirm(2)
        val reset = both.resetDone()
        assertTrue(reset.doneStages.isEmpty())
        assertEquals(setOf(1), reset.selectInstrument(GuideInstrument.Danso).doneStages)
    }

    @Test
    fun confirmKeepsDoneAndMovesToNextStageOrList() {
        val state = LearnUiState().confirm(1).confirm(1)
        assertEquals(setOf(1), state.doneStages)
        assertEquals(2, stageAfterConfirm(1))
        assertNull(stageAfterConfirm(9))
    }

    @Test
    fun selectingInstrumentClosesDropdown() {
        val state = LearnUiState().toggleDropdown()
        assertTrue(state.isDropdownOpen)
        assertFalse(state.selectInstrument(GuideInstrument.DaegeumSanjo).isDropdownOpen)
        assertFalse(state.dismissDropdown().isDropdownOpen)
    }

    @Test
    fun shownStageFollowsSelectionThenNextStage() {
        val state = LearnUiState().confirm(1).confirm(2)
        assertNull(state.selectedStage)
        assertEquals(3, state.shownStage)

        val selected = state.toggleDropdown().selectStage(6)
        assertEquals(6, selected.shownStage)
        assertFalse(selected.isDropdownOpen)

        assertEquals(3, selected.selectStage(null).shownStage)
        assertNull(state.selectStage(0).selectedStage)
        assertNull(state.selectStage(10).selectedStage)
    }

    @Test
    fun shownStageIsFirstStageWhenAllDone() {
        val all = LearnStages.fold(LearnUiState()) { s, stage -> s.confirm(stage.number) }
        assertEquals(1, all.shownStage)
    }

    @Test
    fun togglingShownDoneKeepsTheSameStageInView() {
        val state = LearnUiState().confirm(1)
        assertEquals(2, state.shownStage)

        val marked = state.toggleShownDone(2)
        assertTrue(marked.isDone(2))
        assertEquals(2, marked.shownStage)
    }
}

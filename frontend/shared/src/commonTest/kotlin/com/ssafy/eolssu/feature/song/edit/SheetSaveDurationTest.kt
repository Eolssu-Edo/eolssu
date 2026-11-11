package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class SheetSaveDurationTest {
    private fun draft() = smallScoreData().editingDraft("저장 길이", 60, null)

    @Test fun enteringANewBpmChangesDurationWithoutChangingTheDraft() {
        val draft = draft()
        assertEquals(3.0, assertNotNull(draft.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(1.5, assertNotNull(draft.estimatedDurationSeconds(120)), 1e-9)
        assertEquals(60, draft.bpm)
    }

    @Test fun dottedQuarterUsesTheSourceTickRatioWithoutCountingSubdivisionsTwice() {
        val score = smallScoreData().let { it.copy(music = it.music.copy(ticksPerBeat = 12, ticksPerJeonggan = 4)) }
        val draft = score.editingDraft("점4분음표", 60, null)
        assertEquals("DOTTED_QUARTER", score.music.bpmUnit)
        assertEquals(1.0, assertNotNull(draft.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(0.5, assertNotNull(draft.estimatedDurationSeconds(120)), 1e-9)
    }

    @Test fun blankAndPartiallyFilledCellsStillContributeTheirFullDuration() {
        val draft = draft().copy(cells = listOf(emptyList(), listOf(ScoreNote.Rest, ScoreNote.Blank), emptyList()))
        assertEquals(3.0, assertNotNull(draft.estimatedDurationSeconds(60)), 1e-9)
        val longer = draft.addColumnAfter(0)
        assertEquals(5, longer.cells.size)
        assertEquals(5.0, assertNotNull(longer.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(3.0, assertNotNull(longer.deleteColumn(1).estimatedDurationSeconds(60)), 1e-9)
    }

    @Test fun clearingJangdanKeepsTheOriginalTimingAvailable() {
        val draft = draft().copy(jangdanId = "3")
        val cleared = SheetEditUiState(EditSource.Mine("31"), draft).clearJangdan().draft
        assertNull(cleared.jangdanId)
        assertEquals("", cleared.jangdanName)
        assertEquals(draft.estimatedDurationSeconds(90), cleared.estimatedDurationSeconds(90))
        assertEquals(2.0, assertNotNull(cleared.estimatedDurationSeconds(90)), 1e-9)
    }

    @Test fun legacyDraftUsesJangdanSubdivisionsAndUnknownTimingHasNoEstimate() {
        val legacy = SheetDraft("예시", Jangdan("세마치", listOf(3), 1, 3), 60, List(3) { emptyList() })
        assertEquals(1.0, assertNotNull(legacy.estimatedDurationSeconds(60)), 1e-9)
        assertNull(legacy.copy(jangdan = null, columnSizes = listOf(3)).estimatedDurationSeconds(60))
    }

    @Test fun invalidBpmOrSourceTimingDoesNotProduceAMisleadingDuration() {
        val draft = draft()
        for (bpm in listOf(-1, 0, 29, 161)) assertNull(draft.estimatedDurationSeconds(bpm))
        for (ticks in listOf(0, -1)) {
            val source = assertNotNull(draft.original)
            assertNull(draft.copy(original = source.copy(music = source.music.copy(ticksPerBeat = ticks))).estimatedDurationSeconds(60))
            assertNull(draft.copy(original = source.copy(music = source.music.copy(ticksPerJeonggan = ticks))).estimatedDurationSeconds(60))
        }
    }
}

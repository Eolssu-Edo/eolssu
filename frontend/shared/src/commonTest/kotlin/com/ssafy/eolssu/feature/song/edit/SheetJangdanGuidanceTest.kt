package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SheetJangdanGuidanceTest {
    @Test fun fourQuarterBeatsAreDistinctFromTwelveBeatJungmori() {
        val draft = draft("QUARTER", 12, 12, listOf(4, 4, 2))
        val timing = assertNotNull(draft.jangdanTiming())
        assertEquals("QUARTER", timing.bpmUnit)
        assertEquals("1", timing.beatsPerCell)
        assertEquals(listOf(SheetGakBeats(4, "4"), SheetGakBeats(2, "2")), timing.gakLengths)
        assertFalse(timing.unsupportedCellDuration)
        assertEquals("4", jangdan("단모리", "QUARTER", 8).jangdanBeats())
        assertEquals("12", jangdan("중모리", "QUARTER", 12).jangdanBeats())
        assertTrue(draft.isCompatibleWith(jangdan("단모리", "QUARTER", 8)))
        assertFalse(draft.isCompatibleWith(jangdan("중모리", "QUARTER", 12)))
    }

    @Test fun wrongImportedTimingExplainsThreeBeatsPerCellEvenWhenTotalIsTwelve() {
        val draft = draft("QUARTER", 12, 36, listOf(4, 4))
        val timing = assertNotNull(draft.jangdanTiming())
        assertEquals("3", timing.beatsPerCell)
        assertEquals(listOf(SheetGakBeats(4, "12")), timing.gakLengths)
        assertTrue(timing.unsupportedCellDuration)
        assertFalse(draft.isCompatibleWith(jangdan("중모리", "QUARTER", 12)))
    }

    @Test fun compoundAndHalfBeatsStayExactInsteadOfBeingRounded() {
        val compound = assertNotNull(draft("DOTTED_QUARTER", 12, 4, listOf(12)).jangdanTiming())
        assertEquals("1/3", compound.beatsPerCell)
        assertEquals(listOf(SheetGakBeats(12, "4")), compound.gakLengths)
        assertFalse(compound.unsupportedCellDuration)
        assertEquals("3", jangdan("세마치", "DOTTED_QUARTER", 9).jangdanBeats())
        assertEquals("4", jangdan("굿거리", "DOTTED_QUARTER", 12).jangdanBeats())
        val half = assertNotNull(draft("QUARTER", 12, 6, listOf(3)).jangdanTiming())
        assertEquals("1/2", half.beatsPerCell)
        assertEquals(listOf(SheetGakBeats(3, "3/2")), half.gakLengths)
    }

    @Test fun unknownTimingAndCatalogNamesAreNotGuessed() {
        assertNull(draft("UNKNOWN", 12, 12, listOf(4)).jangdanTiming())
        assertNull(draft("QUARTER", 0, 12, listOf(4)).jangdanTiming())
        assertNull(jangdan("unknown", "QUARTER", 4).jangdanBeats())
        assertNull(jangdan("중모리", "UNKNOWN", 12).jangdanBeats())
    }

    private fun jangdan(name: String, unit: String, cells: Int) = CatalogJangdanSummary(name, name, 90, unit, cells, 1)
    private fun draft(unit: String, beat: Int, cell: Int, sizes: List<Int>): SheetDraft = SheetDraft(
        title = "장단 안내 검사", jangdan = null, bpm = 90,
        cells = List(sizes.sum()) { listOf(ScoreNote.Rest) }, columnSizes = sizes,
        original = smallScoreData().let { it.copy(music = it.music.copy(bpmUnit = unit, ticksPerBeat = beat, ticksPerJeonggan = cell)) },
    )
}

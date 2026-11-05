package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.*
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.practice.component.*
import kotlin.test.*

class PracticeFingeringPagingTest {
    private val semachi = Jangdan("세마치", listOf(3, 3, 3), 3, 3)
    private fun pitches(count: Int) = List(count) { ScoreCell(ScoreNote.SourcePitch("고", 1)) }

    @Test fun pagesContainTwoDaegangAndKeepOriginalOrder() {
        val song = fingerSong(pitches(9), semachi)
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(6, 3), pages.map { it.cells.size })
        assertEquals(listOf(2, 1), pages.map { it.groups.size })
        assertEquals((0..8).toList(), pages.flatMap { it.cells }.map { it.index })
        assertEquals(0, pages.pageAt(5.999))
        assertEquals(1, pages.pageAt(6.0))
        assertEquals(0, pages.pageAt(0.0)) // 뒤로 탐색
    }

    @Test fun threeJeongganDorajiUsesOriginalTicksRatherThanNineSubbeats() {
        val base = fingerSong(pitches(3), semachi)
        val song = base.copy(score = base.score.copy(timing = ScoreTiming(311.13, "세마치", ScoreBpmUnit.DOTTED_QUARTER, 3, 3)))
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(2, 1), pages.map { it.cells.size })
        assertEquals(1, pages.pageAt(2.0))
    }

    @Test fun reviewedMappingControlsBoundariesAndPreservesPickup() {
        val base = fingerSong(pitches(5), semachi)
        val song = base.copy(
            score = base.score.copy(timing = ScoreTiming(311.13, "세마치", ScoreBpmUnit.DOTTED_QUARTER, 3, 3)),
            jangdanPlayback = JangdanPlayback("1", "세마치", 3, 9, List(9) { "" }, listOf(3, 3, 3)),
        )
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(2, 2, 1), pages.map { it.cells.size })
        assertEquals((0..4).toList(), pages.flatMap { it.cells }.map { it.index })
    }

    @Test fun holdsAcrossPagesUseDisplayPositionAndPreviewSkipsNonOnsets() {
        val song = fingerSong(listOf(
            ScoreCell(ScoreNote.SourcePitch("고", 1)), ScoreCell(ScoreNote.Hold),
            ScoreCell(ScoreNote.Rest), ScoreCell(ScoreNote.SourcePitch("임", 1)),
            ScoreCell(ScoreNote.Blank), ScoreCell(ScoreNote.SourcePitch("임", 1)),
        ), Jangdan("검사", listOf(1, 1), 2, 1))
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(1, pages.pageAt(2.4))
        assertEquals(listOf(3, 5), nextFingerUnits(song, 0).map { it.event.index })
        assertEquals(listOf(5), nextFingerUnits(song, 3).map { it.event.index })
        assertTrue(nextFingerUnits(song, 5).isEmpty())
    }

    @Test fun unknownPatternKeepsColumnAndEmptyScoreHasNoPages() {
        val song = fingerSong(pitches(3))
        assertEquals(1, fingerPages(song, fingerCells(song, GuideInstrument.Sogeum)).size)
        assertTrue(fingerPages(fingerSong(emptyList()), emptyList()).isEmpty())
    }

    @Test fun twoFractionalDaegangStillMakeExactlyThreeOriginalJeonggan() {
        val base = fingerSong(pitches(6), semachi)
        val song = base.copy(score = base.score.copy(timing = ScoreTiming(311.13, "세마치", ScoreBpmUnit.DOTTED_QUARTER, 3, 2)))
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(3, 3), pages.map { it.cells.size })
        assertEquals(1, pages.pageAt(3.0))
    }

    @Test fun pageBoundaryInsideDisplaySlotKeepsEventAndShowsContinuationWithoutAnotherFingering() {
        val base = fingerSong(pitches(4), Jangdan("검사", listOf(1, 2), 3, 3))
        val song = base.copy(score = base.score.copy(timing = ScoreTiming(311.13, "검사", ScoreBpmUnit.DOTTED_QUARTER, 3, 2)))
        val original = song.displayUnits.toList()
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(0.0, 1.5, 3.0), pages.map { it.start })
        val continuation = pages[1].cells.first()
        assertEquals(ScoreNote.Hold, continuation.units.first().note)
        assertNull(continuation.entries.first().holes)
        assertSame(song.timeline[1], continuation.units.first().event)
        assertEquals(4.0, pages.flatMap { it.cells }.flatMap { it.units }.sumOf { it.unitDuration }, 0.000001)
        assertEquals(original, song.displayUnits)
    }
}

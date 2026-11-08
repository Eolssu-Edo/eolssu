package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.*
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.practice.component.*
import kotlin.test.*

class PracticeFingeringPagingTest {
    private val semachi = Jangdan("세마치", listOf(3, 3, 3), 3, 3)
    private fun pitches(count: Int) = List(count) { ScoreCell(ScoreNote.SourcePitch("고", 1)) }

    @Test fun previewRowsKeepTheirOwnFingeringAndOriginalLyricSlot() {
        val base = fingerSong(listOf(ScoreCell(ScoreNote.SourcePitch("고", 1)), ScoreCell(ScoreNote.Hold, ScoreNote.SourcePitch("태", 1), ScoreNote.SourcePitch("황", 1))))
        val scoreCells = base.score.columns.single().toMutableList()
        scoreCells[1] = scoreCells[1].copy(lyric = "- 라 지")
        val song = base.copy(score = base.score.copy(columns = listOf(scoreCells)))
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        val preview = nextFingerCells(song, cells, 0)
        assertEquals(listOf("라", "지"), preview.map { it.lyricSlots.single() })
        assertEquals(listOf(ScoreNote.SourcePitch("태", 1), ScoreNote.SourcePitch("황", 1)), preview.map { it.units.single().note })
        assertEquals(cells[1].entries.drop(1), preview.map { it.entries.single() })
        assertEquals(1, nextFingerCells(song, cells, preview.first().firstEvent!!.index).size)
        assertTrue(nextFingerCells(song, cells, preview.last().firstEvent!!.index).isEmpty())
    }

    private fun columnsSong(columns: List<List<ScoreCell>>): Song =
        fingerSong(columns.first(), semachi).let { it.copy(score = it.score.copy(columns = columns)) }

    private fun List<List<FingerCell>>.positions() = map { page -> page.map { it.column to it.row } }

    private fun List<FingerCell>.fingerings() = sumOf { cell -> cell.entries.count { it.note != null } }

    @Test fun singleBeatCellsShowFiveFingeringsPerPageAndPreviewStaysTwo() {
        val song = columnsSong(List(4) { pitches(3) })
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        val pages = fingerAnglePages(song, cells)
        assertEquals(listOf(5, 5, 2), pages.map { it.fingerings() })
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1), pages.positions().first())
        val last = pages.first().flatMap { it.units }.maxOf { it.event.index }
        assertEquals(listOf(5, 6), nextFingerCells(song, cells, last).map { it.firstEvent!!.index })
    }

    @Test fun restAndHoldAreNotCountedAndStayWithTheirPage() {
        val pitch = ScoreCell(ScoreNote.SourcePitch("고", 1))
        val song = columnsSong(listOf(listOf(pitch, ScoreCell(ScoreNote.Rest), ScoreCell(ScoreNote.Hold, ScoreNote.SourcePitch("태", 1)), pitch, pitch, pitch, ScoreCell(ScoreNote.Hold), pitch)))
        val pages = fingerAnglePages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(5, 1), pages.map { it.fingerings() })
        // 다섯째 운지 뒤의 장음은 같은 페이지에 남는다.
        assertEquals(7, pages.first().size)
    }

    @Test fun cellsSplitAcrossPagesToShowExactlyFiveFingerings() {
        val triple = ScoreCell(ScoreNote.SourcePitch("고", 1), ScoreNote.SourcePitch("태", 1), ScoreNote.SourcePitch("황", 1))
        val song = columnsSong(listOf(List(3) { triple }))
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        val pages = fingerAnglePages(song, cells)
        assertEquals(listOf(5, 4), pages.map { it.fingerings() })
        assertEquals(listOf(3, 2), pages[0].map { it.units.size })
        assertEquals(listOf(1, 3), pages[1].map { it.units.size })
        assertTrue(pages[1].first().continued)
        assertEquals(cells.flatMap { it.units }.map { it.key }, pages.flatten().flatMap { it.units }.map { it.key })
    }

    @Test fun lowScreenBudgetShowsFewerFingeringsPerPageInOriginalOrder() {
        val song = columnsSong(List(4) { pitches(3) })
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        // 자리 28 + 줄 여백 4 = 32씩, 100 높이에는 세 줄만 들어간다.
        val pages = fingerAnglePages(song, cells, FingerPageBudget(height = 100f, slotHeight = 28f, rowGap = 4f))
        assertEquals(listOf(3, 3, 3, 3), pages.map { it.fingerings() })
        assertTrue(pages.all { page -> page.sumOf { 28.0 * it.units.size + 4.0 } <= 100.0 })
        assertEquals(cells.map { it.column to it.row }, pages.flatten().map { it.column to it.row })
    }

    @Test fun tallScreenBudgetKeepsFiveFingeringCap() {
        val song = columnsSong(List(4) { pitches(3) })
        val pages = fingerAnglePages(song, fingerCells(song, GuideInstrument.Sogeum), FingerPageBudget(1000f, 28f, 4f))
        assertEquals(listOf(5, 5, 2), pages.map { it.fingerings() })
    }

    @Test fun budgetSmallerThanOneSlotStillShowsOneSlotPerPage() {
        val song = columnsSong(listOf(pitches(3)))
        val pages = fingerAnglePages(song, fingerCells(song, GuideInstrument.Sogeum), FingerPageBudget(10f, 28f, 4f))
        assertEquals(listOf(1, 1, 1), pages.map { it.fingerings() })
    }

    @Test fun holdsThatOverflowTheBudgetMoveToTheNextPageAsContinuation() {
        val held = ScoreCell(ScoreNote.SourcePitch("고", 1), ScoreNote.Hold, ScoreNote.Hold, ScoreNote.Hold)
        val song = columnsSong(listOf(listOf(held, ScoreCell(ScoreNote.SourcePitch("태", 1)))))
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        // 첫 정간: 율명 28 + 장음 14×3 + 여백 4 = 74. 60 높이에서는 장음 일부가 다음 페이지로 넘어간다.
        val pages = fingerAnglePages(song, cells, FingerPageBudget(60f, 28f, 4f))
        assertTrue(pages.size >= 2)
        assertTrue(pages[1].first().continued)
        assertEquals(cells.flatMap { it.units }.map { it.key }, pages.flatten().flatMap { it.units }.map { it.key })
    }

    @Test fun sparseDaegangPagesContainSixPitchesPlusTwoSeparatePreviewNotes() {
        val song = fingerSong(List(8) { listOf(ScoreCell(ScoreNote.SourcePitch("고", 1)), ScoreCell(ScoreNote.Hold)) }.flatten(), Jangdan("검사", listOf(1, 1), 2, 1))
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(6, pages.first().cells.flatMap { it.entries }.count { it.note != null })
        assertEquals(12.0, pages[1].start)
        assertEquals(listOf(12, 14), nextFingerUnits(song, pages.first().cells.last().units.last().event.index).map { it.event.index })
        assertEquals((0..15).toList(), pages.flatMap { it.cells }.map { it.index })
    }

    @Test fun trailingHoldsRemainOnLastPitchPage() {
        val song = fingerSong(pitches(6) + List(4) { ScoreCell(ScoreNote.Hold) }, Jangdan("검사", listOf(1, 1), 2, 1))
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(1, pages.size)
        assertEquals(10, pages.single().cells.size)
    }

    @Test fun pagesContainSixPitchesAtDaegangBoundariesAndKeepOriginalOrder() {
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
        assertEquals(listOf(3), pages.map { it.cells.size })
        assertEquals(0, pages.pageAt(2.0))
    }

    @Test fun reviewedMappingControlsBoundariesAndPreservesPickup() {
        val base = fingerSong(pitches(5), semachi)
        val song = base.copy(
            score = base.score.copy(timing = ScoreTiming(311.13, "세마치", ScoreBpmUnit.DOTTED_QUARTER, 3, 3)),
            jangdanPlayback = JangdanPlayback("1", "세마치", 3, 9, List(9) { "" }, listOf(3, 3, 3)),
        )
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(5), pages.map { it.cells.size })
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), pages.single().groups.map { it.start })
        assertEquals((0..4).toList(), pages.flatMap { it.cells }.map { it.index })
    }

    @Test fun holdsAcrossPagesUseDisplayPositionAndPreviewSkipsNonOnsets() {
        val song = fingerSong(listOf(
            ScoreCell(ScoreNote.SourcePitch("고", 1)), ScoreCell(ScoreNote.Hold),
            ScoreCell(ScoreNote.Rest), ScoreCell(ScoreNote.SourcePitch("임", 1)),
            ScoreCell(ScoreNote.Blank), ScoreCell(ScoreNote.SourcePitch("임", 1)),
        ), Jangdan("검사", listOf(1, 1), 2, 1))
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(0, pages.pageAt(2.4))
        assertEquals(listOf(3, 5), nextFingerUnits(song, 0).map { it.event.index })
        assertEquals(listOf(5), nextFingerUnits(song, 3).map { it.event.index })
        assertTrue(nextFingerUnits(song, 5).isEmpty())
    }

    @Test fun unknownPatternKeepsColumnAndEmptyScoreHasNoPages() {
        val song = fingerSong(pitches(3))
        assertEquals(1, fingerPages(song, fingerCells(song, GuideInstrument.Sogeum)).size)
        assertTrue(fingerPages(fingerSong(emptyList()), emptyList()).isEmpty())
    }

    @Test fun fractionalDaegangKeepTheirOriginalTimingInsideSixPitchPage() {
        val base = fingerSong(pitches(6), semachi)
        val song = base.copy(score = base.score.copy(timing = ScoreTiming(311.13, "세마치", ScoreBpmUnit.DOTTED_QUARTER, 3, 2)))
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(6), pages.map { it.cells.size })
        assertEquals(listOf(0.0, 1.5, 3.0, 4.5), pages.single().groups.map { it.start })
        assertEquals(0, pages.pageAt(3.0))
    }

    @Test fun pageBoundaryInsideDisplaySlotKeepsEventAndShowsContinuationWithoutAnotherFingering() {
        val base = fingerSong(pitches(5) + ScoreCell(ScoreNote.Rest) + pitches(9), Jangdan("검사", listOf(1, 2), 3, 3))
        val song = base.copy(score = base.score.copy(timing = ScoreTiming(311.13, "검사", ScoreBpmUnit.DOTTED_QUARTER, 3, 2)))
        val original = song.displayUnits.toList()
        val pages = fingerPages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(6.5, pages[1].start)
        val continuation = pages[1].cells.first()
        assertEquals(ScoreNote.Hold, continuation.units.first().note)
        assertNull(continuation.entries.first().holes)
        assertSame(song.timeline[6], continuation.units.first().event)
        assertEquals(15.0, pages.flatMap { it.cells }.flatMap { it.units }.sumOf { it.unitDuration }, 0.000001)
        assertEquals(original, song.displayUnits)
    }
}

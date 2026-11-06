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

    @Test fun singleBeatCellsFillFiveFingeringsPerPageAndPreviewStaysTwo() {
        // 3정간 각에 정간마다 한 음: 한 페이지에 운지 5개, 다음 음은 두 개.
        val song = columnsSong(List(4) { pitches(3) })
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        val pages = fingerAnglePages(song, cells)
        assertEquals(listOf(5, 5, 2), pages.map { it.size })
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1), pages.positions().first())
        val last = pages.first().flatMap { it.units }.maxOf { it.event.index }
        assertEquals(listOf(5, 6), nextFingerCells(song, cells, last).map { it.firstEvent!!.index })
    }

    @Test fun restAndHoldCountAsOneFingeringEach() {
        val song = columnsSong(listOf(listOf(ScoreCell(ScoreNote.SourcePitch("고", 1)), ScoreCell(ScoreNote.Rest), ScoreCell(ScoreNote.Hold, ScoreNote.SourcePitch("태", 1)), ScoreCell(ScoreNote.SourcePitch("황", 1)), ScoreCell(ScoreNote.SourcePitch("고", 1)))))
        val pages = fingerAnglePages(song, fingerCells(song, GuideInstrument.Sogeum))
        // 고 1 + 쉼 1 + (장음·태) 2 + 황 1 = 5칸, 남은 고는 다음 페이지.
        assertEquals(listOf(4, 1), pages.map { it.size })
    }

    @Test fun mixedCellsNeverExceedFiveFingeringsAndKeepOrder() {
        val split = ScoreCell(ScoreNote.SourcePitch("고", 1), ScoreNote.SourcePitch("태", 1))
        val triple = ScoreCell(ScoreNote.SourcePitch("고", 1), ScoreNote.SourcePitch("태", 1), ScoreNote.SourcePitch("황", 1))
        val song = columnsSong(listOf(pitches(2) + split, pitches(3), List(9) { ScoreCell(ScoreNote.SourcePitch("고", 1)) }, List(3) { triple }))
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        val pages = fingerAnglePages(song, cells)
        assertTrue(pages.all { page -> page.sumOf { it.parts } <= FingerPageMaxSlots })
        assertEquals(cells.map { it.index }, pages.flatten().map { it.index })
        assertEquals(listOf(4, 5, 5, 2, 1, 1), pages.map { it.size })
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

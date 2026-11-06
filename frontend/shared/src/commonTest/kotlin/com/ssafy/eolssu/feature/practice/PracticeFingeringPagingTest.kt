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

    @Test fun singleBeatThreeCellAnglesShareAPageAndPreviewStaysTwo() {
        // 도라지타령처럼 3정간 각에 정간마다 한 음: 한 페이지에 두 각(6정간), 다음 음은 두 개.
        val song = columnsSong(List(4) { pitches(3) })
        val cells = fingerCells(song, GuideInstrument.Sogeum)
        val pages = fingerAnglePages(song, cells)
        assertEquals(2, pages.size)
        assertEquals(listOf((0..2).map { 0 to it } + (0..2).map { 1 to it }, (0..2).map { 2 to it } + (0..2).map { 3 to it }), pages.positions())
        val last = pages.first().flatMap { it.units }.maxOf { it.event.index }
        assertEquals(listOf(6, 7), nextFingerCells(song, cells, last).map { it.firstEvent!!.index })
    }

    @Test fun subdividedAngleIsNotExtendedButSingleBeatAngleTakesTheNextOne() {
        val split = ScoreCell(ScoreNote.SourcePitch("고", 1), ScoreNote.SourcePitch("태", 1))
        // 소박 있는 1각은 혼자, 소박 없는 2각은 3각과 함께, 4각은 남은 각이다.
        val first = columnsSong(listOf(pitches(2) + split, pitches(3), pitches(3), pitches(3)))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), fingerAnglePages(first, fingerCells(first, GuideInstrument.Sogeum)).map { page -> page.map { it.column }.distinct() })
        // 단소 도라지타령: 소박 없는 1각 뒤 2각에 소박이 있어도 이어 붙인다.
        val second = columnsSong(listOf(pitches(3), pitches(2) + split, pitches(3)))
        assertEquals(listOf(listOf(0, 1), listOf(2)), fingerAnglePages(second, fingerCells(second, GuideInstrument.Sogeum)).map { page -> page.map { it.column }.distinct() })
    }

    @Test fun longSingleBeatAnglesJoinAtDaegangBoundaries() {
        // 대강 3·3·3 한 각(9정간)은 대강 둘(6정간)씩 묶고, 다음 각과도 대강 단위로 잇는다.
        val song = columnsSong(List(2) { pitches(9) })
        val pages = fingerAnglePages(song, fingerCells(song, GuideInstrument.Sogeum))
        assertEquals(listOf(6, 6, 6), pages.map { it.size })
        assertEquals((0 until 18).toList(), pages.flatten().map { it.index })
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

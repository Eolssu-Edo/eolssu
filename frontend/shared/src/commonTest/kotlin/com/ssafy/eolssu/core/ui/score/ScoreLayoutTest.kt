package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreDisplayUnit
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreTiming
import com.ssafy.eolssu.core.model.ScoreBpmUnit
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.key
import com.ssafy.eolssu.core.model.timeline
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreLayoutTest {

    private val hwang = ScoreCell(ScoreNote.Pitch(Yul.Hwangjong))

    private fun score(columns: Int, rows: Int) = Score(List(columns) { List(rows) { hwang } })

    private val gagok16 = Jangdan("가곡 16박", daegang = listOf(11, 5), beats = 16, subdivision = 1)
    private val semachi = Jangdan("세마치", daegang = listOf(3, 3, 3), beats = 3, subdivision = 3)

    @Test
    fun stackedPlayheadKeepsThreeColumnsInOneHorizontalPosition() {
        val song = smallScoreSong().copy(score = score(columns = 7, rows = 3))
        val strips = scoreStrips(song.score, null)
        val units = song.displayUnits.groupBy { it.column to it.row }
        for (column in 0..6) {
            val position = requireNotNull(playheadStrip(song, strips, units, column * 3, 0f, columnsPerStack = 3))
            assertEquals(column / 3, position.toInt())
        }
        assertEquals((0..6).toList(), strips.map { it.column })
    }

    @Test
    fun playheadStripMovesForwardWithoutGapsAcrossNotesAndColumns() {
        val song = smallScoreSong()
        val strips = scoreStrips(song.score, song.jangdan)
        val units = song.displayUnits.groupBy { it.column to it.row }
        fun at(index: Int, fraction: Float) = requireNotNull(playheadStrip(song, strips, units, index, fraction))
        assertEquals(0.0, at(0, 0f), 1e-9)
        var last = 0.0
        for (event in song.timeline) {
            // 다음 음은 앞 음이 끝난 자리에서 이어 시작한다.
            assertEquals(last, at(event.index, 0f), 1e-6, "event ${event.index}")
            for (step in 1..10) {
                val now = at(event.index, step / 10f)
                assertTrue(now >= last, "event ${event.index} step $step: $now < $last")
                last = now
            }
        }
        assertEquals(strips.size.toDouble(), last, 1e-6)
    }

    @Test
    fun shortGakIsOneStripPerColumn() {
        val strips = scoreStrips(score(columns = 2, rows = 9), semachi)
        assertEquals(listOf(ScoreStrip(0, 0, 0..8), ScoreStrip(1, 0, 0..8)), strips)
    }

    @Test
    fun longGakIsSplitIntoStripsInReadingOrder() {
        val strips = scoreStrips(score(columns = 2, rows = 16), gagok16)
        assertEquals(
            listOf(ScoreStrip(0, 0, 0..10), ScoreStrip(0, 1, 11..15), ScoreStrip(1, 0, 0..10), ScoreStrip(1, 1, 11..15)),
            strips,
        )
    }

    @Test
    fun shortLastColumnDropsEmptyStrips() {
        val score = Score(listOf(List(16) { hwang }, List(7) { hwang }))
        assertEquals(listOf(ScoreStrip(1, 0, 0..6)), scoreStrips(score, gagok16).filter { it.column == 1 })
    }

    @Test
    fun maxColumnsAndRowsLimitPreview() {
        val strips = scoreStrips(score(columns = 5, rows = 16), gagok16, maxColumns = 1, maxRows = 3)
        assertEquals(listOf(ScoreStrip(0, 0, 0..2)), strips)
    }

    @Test
    fun cellProgressAroundCurrentIndex() {
        val timeline = score(columns = 1, rows = 3).timeline("s")
        val (first, second, third) = timeline.map { listOf(it) }
        assertEquals(CellProgress.Upcoming, cellProgress(second, currentIndex = null))
        assertEquals(CellProgress.Past, cellProgress(first, currentIndex = 1))
        assertEquals(CellProgress.Current, cellProgress(second, currentIndex = 1))
        assertEquals(CellProgress.Upcoming, cellProgress(third, currentIndex = 1))
    }

    @Test
    fun splitCellIsCurrentWhileAnyNoteIsCurrent() {
        val split = Score(listOf(listOf(ScoreCell(ScoreNote.Pitch(Yul.Taeju), ScoreNote.Pitch(Yul.Jungnyeo)))))
        val events = split.timeline("s")
        assertEquals(CellProgress.Current, cellProgress(events, currentIndex = 1))
        assertEquals(CellProgress.Past, cellProgress(events, currentIndex = 2))
    }

    @Test
    fun sourceColumnsHaveIndependentLengthsAndNeverLoseTrailingCells() {
        val source = Score(listOf(List(14) { hwang }, List(5) { hwang }),
            timing = ScoreTiming(311.13, "세마치", ScoreBpmUnit.DOTTED_QUARTER, 3, 3))
        assertEquals(listOf(ScoreStrip(0, 0, 0..6), ScoreStrip(0, 1, 7..13), ScoreStrip(1, 0, 0..4)), scoreStrips(source, semachi))
        assertEquals(19, scoreStrips(source, semachi).sumOf { it.rows.count() })
    }

    @Test
    fun longSourceGakSplitsIntoEvenStripsInsteadOfTwelvePlusRemainder() {
        // S15P31E102-922 정악대금 인연: 16정간 각이 12·4로 잘려 짧은 줄이 따로 떨어진 각처럼 보였다. 8·8로 고르게 나눈다.
        val timing = ScoreTiming(400.0, "", ScoreBpmUnit.QUARTER, 4, 2)
        val source = Score(listOf(List(1) { hwang }, List(16) { hwang }, List(25) { hwang }), timing = timing)
        val strips = scoreStrips(source, null)
        assertEquals(listOf(0..0), strips.filter { it.column == 0 }.map { it.rows })
        assertEquals(listOf(0..7, 8..15), strips.filter { it.column == 1 }.map { it.rows })
        assertEquals(listOf(0..8, 9..16, 17..24), strips.filter { it.column == 2 }.map { it.rows })
        // 장단을 모르는 악보도 같은 규칙을 쓴다.
        assertEquals(listOf(0..7, 8..15), scoreStrips(score(columns = 1, rows = 16), null).map { it.rows })
    }

    @Test
    fun tiedEventProgressMovesToTheNextCellInsteadOfHighlightingEveryLinkedCell() {
        assertEquals(CellProgress.Upcoming, cellProgressAt(1.0, 0.75))
        assertEquals(CellProgress.Past, cellProgressAt(0.0, 1.25))
        assertEquals(CellProgress.Current, cellProgressAt(1.0, 1.25))
        assertEquals(CellProgress.Upcoming, cellProgressAt(1.0, null))
    }

    @Test
    fun liveMarkWaitsUntilPlayheadPassesTheWholeUnit() {
        // 세 줄로 나눈 정간의 마지막 소박: 2/3 ~ 1 정간.
        val event = ScoreEvent(4, "e4", 0, 0, 2, 0, ScoreNote.Pitch(Yul.Hwangjong), 2.0 / 3, 1.0 / 3)
        val unit = ScoreDisplayUnit(0, 0, 2, 0, "黃", event.note, event, 2.0 / 3, 1.0 / 3)

        assertFalse(unitPassed(unit, null))
        assertFalse(unitPassed(unit, 2.0 / 3))
        assertFalse(unitPassed(unit, event.unitStart + event.unitDuration * 0.99f))
        // 진행이 음 끝(fraction 1)에 닿으면 분수 합 오차와 관계없이 지난 것으로 본다.
        assertTrue(unitPassed(unit, event.unitStart + event.unitDuration * 1f))
        assertTrue(unitPassed(unit, 1.5))
    }

    @Test
    fun everyDisplayUnitPassesExactlyWhenItsEventEnds() {
        val song = smallScoreSong()
        for (unit in song.displayUnits) {
            val event = unit.event
            assertTrue(unitPassed(unit, event.unitStart + event.unitDuration * 1f), "${unit.key} at its event end")
            assertFalse(unitPassed(unit, unit.unitStart), "${unit.key} at its own start")
        }
    }
}

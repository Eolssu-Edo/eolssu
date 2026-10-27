package com.ssafy.eolssu.core.ui.score

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.model.timeline
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreLayoutTest {

    private val hwang = ScoreCell(ScoreNote.Pitch(Yul.Hwangjong))

    private fun score(columns: Int, rows: Int) = Score(List(columns) { List(rows) { hwang } })

    private val gagok16 = Jangdan("가곡 16박", daegang = listOf(11, 5), beats = 16, subdivision = 1)
    private val semachi = Jangdan("세마치", daegang = listOf(3, 3, 3), beats = 3, subdivision = 3)

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
}

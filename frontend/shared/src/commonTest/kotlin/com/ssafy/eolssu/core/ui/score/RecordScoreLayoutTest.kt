package com.ssafy.eolssu.core.ui.score

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreUnit
import com.ssafy.eolssu.core.model.timeline
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordScoreLayoutTest {
    @Test
    fun threeAndFourRowNotesHaveRoomWithoutChangingCoordinatesOrTimeline() {
        val note = ScoreNote.Pitch(Yul.Hwangjong)
        val score = Score(listOf(listOf(ScoreCell(note), ScoreCell(note, note, note)), listOf(ScoreCell(note, note, note, note))))
        val song = smallScoreSong().copy(score = score)
        val timeline = song.timeline.toList()
        val metrics = JeongganboMetrics.practiceBoard(false, 2)
        val heights = recordScoreCellHeights(song, metrics)
        assertTrue(requireNotNull(heights[0 to 1]) / 3 >= 22.dp)
        assertTrue(requireNotNull(heights[1 to 0]) / 4 >= 22.dp)
        assertEquals(setOf(0 to 1, 1 to 0), heights.keys)
        assertEquals(timeline, song.timeline)
        assertEquals(score, song.score)
    }

    @Test
    fun sourceGridCountsVerticalRowsRatherThanNumberOfEvents() {
        val note = ScoreNote.Pitch(Yul.Hwangjong)
        val source = Score(listOf(listOf(ScoreCell(note, note, note, note))))
        val events = source.timeline("fixture")
        val units = events.mapIndexed { index, event -> ScoreUnit(0, index, note.symbol, event.id) }
        val song = smallScoreSong().copy(score = source.copy(
            columns = listOf(listOf(ScoreCell(List(4) { note }, units = units))), events = events,
        ))
        assertTrue(recordScoreCellHeights(song, recordScoreMetrics(song)).isEmpty())
        assertEquals(events, song.timeline)
        assertEquals(units, song.score.columns[0][0].units)
    }

    @Test
    fun oneAndTwoRowCellsKeepTheEvaluationCellSize() {
        val note = ScoreNote.Pitch(Yul.Hwangjong)
        val song = smallScoreSong().copy(score = Score(listOf(listOf(ScoreCell(note), ScoreCell(note, note)))))
        assertTrue(recordScoreCellHeights(song, JeongganboMetrics.practiceBoard(false, 2)).isEmpty())
    }

    @Test
    fun errorScoreGivesSplitRowsRoomAndAlignsColumnsWithTheSameCellCount() {
        val note = ScoreNote.Pitch(Yul.Hwangjong)
        // 1각은 세 줄로 나뉜 정간이 있고, 2각은 나뉘지 않았다. 정간 수는 둘 다 2다.
        val score = Score(listOf(listOf(ScoreCell(note), ScoreCell(note, note, note)), listOf(ScoreCell(note), ScoreCell(note))))
        val song = smallScoreSong().copy(score = score)
        val metrics = JeongganboMetrics.practiceBoard(false, 2)
        val heights = errorScoreCellHeights(song, metrics)

        assertTrue(requireNotNull(heights[0 to 1]) / 3 >= 22.dp)
        assertEquals(metrics.cellHeight, heights[0 to 0])
        val first = heights.getValue(0 to 0) + heights.getValue(0 to 1)
        val second = heights.getValue(1 to 0) + heights.getValue(1 to 1)
        assertEquals(first.value, second.value, 0.01f)
        // 짧은 각은 정간을 고르게 늘린다.
        assertEquals(heights.getValue(1 to 0), heights.getValue(1 to 1))
        assertEquals(score, song.score)
    }
}

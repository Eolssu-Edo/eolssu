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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 연습 화면을 뺀 모든 화면이 쓰는 공통 정간보 형식([readingScoreFormat]). */
class ReadingScoreFormatTest {
    private val note = ScoreNote.Pitch(Yul.Hwangjong)

    @Test
    fun splitNotesGetReadableGlyphsAndRoomWithoutChangingMusic() {
        val score = Score(listOf(listOf(ScoreCell(note), ScoreCell(note, note), ScoreCell(note, note, note), ScoreCell(note, note, note, note))))
        val song = smallScoreSong().copy(score = score, jangdan = null)
        val before = song.timeline.toList()
        val format = readingScoreFormat(song)

        assertEquals(21f, format.metrics.yulSizeFor(1).value)
        assertEquals(16f, format.metrics.yulSizeFor(2).value, 0.01f)
        assertEquals(15f, format.metrics.yulSizeFor(3).value, 0.01f)
        assertTrue(format.cellHeights.getValue(0 to 1) / 2 >= ReadingSplitRowHeight)
        assertTrue(format.cellHeights.getValue(0 to 2) / 3 >= ReadingSplitRowHeight)
        assertTrue(format.cellHeights.getValue(0 to 3) / 4 >= ReadingSplitRowHeight)
        assertEquals(score, song.score)
        assertEquals(before, song.timeline)
    }

    @Test
    fun longGakKeepsSplitNotesReadable() {
        val song = smallScoreSong().copy(score = Score(listOf(List(12) { ScoreCell(note, note, note) })), jangdan = null)
        val format = readingScoreFormat(song)
        assertEquals(18f, format.metrics.yulSizeFor(1).value)
        assertEquals(16f, format.metrics.yulSizeFor(2).value, 0.01f)
        assertEquals(15f, format.metrics.yulSizeFor(3).value, 0.01f)
        assertTrue(format.cellHeights.values.all { it / 3 >= ReadingSplitRowHeight })
    }

    @Test
    fun sideBySideNotesDoNotAddHeight() {
        val source = Score(listOf(listOf(ScoreCell(note, note, note, note))))
        val events = source.timeline("fixture")
        val units = events.mapIndexed { index, event -> ScoreUnit(0, index, note.symbol, event.id) }
        val song = smallScoreSong().copy(score = source.copy(
            columns = listOf(listOf(ScoreCell(List(4) { note }, units = units))), events = events,
        ))
        val format = readingScoreFormat(song)
        assertEquals(format.metrics.cellHeight, format.cellHeights.getValue(0 to 0))
        assertEquals(units, song.score.columns[0][0].units)
    }

    @Test
    fun gakWithTheSameCellCountStartAndEndTogether() {
        // 1각은 세 줄로 나뉜 정간이 있고, 2각은 나뉘지 않았다. 정간 수는 둘 다 2다.
        val score = Score(listOf(listOf(ScoreCell(note), ScoreCell(note, note, note)), listOf(ScoreCell(note), ScoreCell(note))))
        val song = smallScoreSong().copy(score = score, jangdan = null)
        val heights = readingScoreFormat(song).cellHeights

        val first = heights.getValue(0 to 0) + heights.getValue(0 to 1)
        val second = heights.getValue(1 to 0) + heights.getValue(1 to 1)
        assertEquals(first.value, second.value, 0.01f)
        // 짧은 각은 정간을 고르게 늘린다.
        assertEquals(heights.getValue(1 to 0), heights.getValue(1 to 1))
        assertEquals(score, song.score)
    }

    @Test
    fun lyricLaneOnlyWhenTheSongHasLyrics() {
        val plain = smallScoreSong().copy(score = Score(listOf(listOf(ScoreCell(note), ScoreCell(note)))))
        val sung = plain.copy(score = Score(listOf(listOf(ScoreCell(listOf(note), lyric = "아"), ScoreCell(note)))))

        assertNull(readingScoreFormat(plain).look.lyricLane)
        assertEquals(25.dp, readingScoreFormat(sung).look.lyricLane)
        // 곁줄은 각 너비 안에 들어가므로 가사가 있어도 각 너비는 같다.
        assertEquals(readingScoreFormat(plain).metrics.columnWidth, readingScoreFormat(sung).metrics.columnWidth)
        assertEquals(JeongganboLook.Reading.copy(lyricLane = null), readingScoreFormat(plain).look)
    }
}

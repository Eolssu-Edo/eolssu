package com.ssafy.eolssu.feature.practice

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.ui.score.errorScoreCellHeights
import com.ssafy.eolssu.data.song.smallScoreSong
import com.ssafy.eolssu.feature.practice.component.sectionLoopScoreMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionScoreLayoutTest {
    @Test
    fun splitNotesHaveReadableGlyphsAndVerticalRoomWithoutChangingMusic() {
        val note = ScoreNote.Pitch(Yul.Hwangjong)
        val score = Score(listOf(listOf(ScoreCell(note), ScoreCell(note, note), ScoreCell(note, note, note))))
        val song = smallScoreSong().copy(score = score)
        val before = song.timeline.toList()
        val metrics = sectionLoopScoreMetrics(song)
        val heights = errorScoreCellHeights(song, metrics, minSplitRowHeight = 26.dp)
        assertTrue(metrics.yulSizeFor(2).value >= 16f)
        assertTrue(metrics.yulSizeFor(3).value >= 15f)
        assertEquals(21f, metrics.yulSizeFor(1).value)
        assertTrue(heights.getValue(0 to 1) / 2 >= 26.dp)
        assertTrue(heights.getValue(0 to 2) / 3 >= 26.dp)
        assertEquals(score, song.score)
        assertEquals(before, song.timeline)
    }

    @Test
    fun longGakStillKeepsSplitNotesReadable() {
        val note = ScoreNote.Pitch(Yul.Hwangjong)
        val song = smallScoreSong().copy(score = Score(listOf(List(12) { ScoreCell(note, note, note) })), jangdan = null)
        val metrics = sectionLoopScoreMetrics(song)
        val heights = errorScoreCellHeights(song, metrics, minSplitRowHeight = 26.dp)
        assertTrue(metrics.yulSizeFor(2).value >= 16f)
        assertTrue(metrics.yulSizeFor(3).value >= 15f)
        assertTrue(heights.values.all { it / 3 >= 26.dp })
        assertEquals(18f, metrics.yulSizeFor(1).value)
    }
}

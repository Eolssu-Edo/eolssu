package com.ssafy.eolssu.feature.result

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.feature.practice.component.practiceScorePacking
import com.ssafy.eolssu.feature.result.component.WeakSectionLines
import com.ssafy.eolssu.feature.result.component.weakColumns
import com.ssafy.eolssu.feature.result.component.weakSectionLines
import com.ssafy.eolssu.feature.result.component.weakSectionScoreHeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 결과 카드가 악보 전체 중 틀린 칸이 가장 몰린 두 각을 가운데에 두는지. */
class ResultPreviewColumnsTest {
    @Test
    fun weakColumnsAreTheDensestPair() {
        assertEquals(3..4, weakColumns(clusterStart = 3, columnCount = 8))
        // 마지막 각이면 그 각만.
        assertEquals(7..7, weakColumns(clusterStart = 7, columnCount = 8))
    }

    @Test
    fun weakColumnsWithoutErrorsIsTheFirstGak() {
        assertEquals(0..0, weakColumns(clusterStart = null, columnCount = 8))
        assertEquals(IntRange.EMPTY, weakColumns(clusterStart = null, columnCount = 0))
    }

    @Test
    fun linesCountEveryStripOfAGak() {
        // 각마다 두 줄: 4·5각(3·4)은 6~9번째 줄(0부터 6..9), 가운데 7.5.
        assertEquals(WeakSectionLines(total = 16, weakCount = 4, weakCenter = 7.5f), weakSectionLines(List(8) { 2 }, 1, 3..4))
        assertEquals(WeakSectionLines(total = 8, weakCount = 2, weakCenter = 3.5f), weakSectionLines(List(8) { 1 }, 1, 3..4))
    }

    @Test
    fun linesFollowPracticeStacks() {
        // 세 각씩 쌓으면 묶음은 0..2 / 3..5 / 6..8 / 9. 3·4각은 같은 줄(1).
        assertEquals(WeakSectionLines(total = 4, weakCount = 1, weakCenter = 1f), weakSectionLines(List(10) { 1 }, 3, 3..4))
        // 두 묶음에 걸치면 두 줄.
        assertEquals(WeakSectionLines(total = 4, weakCount = 2, weakCenter = 1.5f), weakSectionLines(List(10) { 1 }, 3, 5..6))
        assertEquals(WeakSectionLines(0, 0, 0f), weakSectionLines(emptyList(), 3, IntRange.EMPTY))
    }

    @Test
    fun errorDetailHeightKeepsLongGakOnOneLine() {
        // S15P31E102-740 단소 아리랑 각(12정간 중 3소박 8개): 결과 카드는 두 줄로 나눈 높이, 틀린 부분 상세는 한 줄 높이를 쓴다.
        val note = ScoreNote.Pitch(Yul.Hwangjong)
        val cell = ScoreCell(note)
        val triple = ScoreCell(note, note, note)
        val column = listOf(cell, triple, triple, cell, triple, triple, cell, triple, triple, cell, triple, triple)
        val song = Song("arirang", "아리랑", SongLevel.Intro, null, 60, "", true, Score(List(4) { column }))
        val base = JeongganboMetrics.practiceBoard(false, 12)
        val card = weakSectionScoreHeight(song, base, 1f)
        val detail = weakSectionScoreHeight(song, base, 1f, oneLine = true)
        assertTrue(practiceScorePacking(song.score, base, card, 1f).splitColumns)
        val packing = practiceScorePacking(song.score, base, detail, 1f)
        assertFalse(packing.splitColumns)
        assertTrue(packing.metrics.cellHeight >= 32.dp)
        assertTrue(detail > card)
    }
}

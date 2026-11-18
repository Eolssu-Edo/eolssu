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
import com.ssafy.eolssu.feature.result.component.weakSectionScoreHeight
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 연주 결과 정간보 높이가 긴 각을 한 줄에 두고 연습 최소 정간을 지키는지. */
class ResultPreviewColumnsTest {
    @Test
    fun errorDetailHeightKeepsLongGakOnOneLine() {
        // S15P31E102-740 단소 아리랑 각(12정간 중 3소박 8개): 연습 배치는 두 줄로 나누지만 연주 결과 정간보는 한 줄 높이를 쓴다.
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

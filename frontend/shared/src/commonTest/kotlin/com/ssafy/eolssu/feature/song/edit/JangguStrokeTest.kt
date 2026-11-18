package com.ssafy.eolssu.feature.song.edit

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.data.song.CatalogJangdanPattern
import com.ssafy.eolssu.feature.song.edit.component.JangguCellMaxSize
import com.ssafy.eolssu.feature.song.edit.component.jangguCellSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 장단 바꾸기 창의 장구 기호 칸(S15P31E102-1138). */
class JangguStrokeTest {
    @Test fun gutgeoriKeepsBlankCellsAndMarksBigBeatStarts() {
        val cells = CatalogJangdanPattern(
            listOf("떵", "", "기덕", "쿵", "더러러러", "", "쿵", "", "기덕", "쿵", "더러러러", ""), listOf(3, 3, 3, 3),
        ).jangguCells()!!
        assertEquals(12, cells.size)
        assertEquals(listOf(JangguStroke.Hap, null, JangguStroke.GiDeok, JangguStroke.Buk, JangguStroke.Roll),
            cells.take(5).map { it.stroke })
        assertEquals(listOf(0, 3, 6, 9), cells.indices.filter { cells[it].groupStart })
    }

    @Test fun everyCatalogGueumHasASymbol() {
        // data/reference/jangdans.json 12종에 쓰인 구음 전부.
        val gueum = listOf("궁", "기닥", "기덕", "다", "더러러러", "덕", "덕기", "덕덕", "덩", "따", "떵", "쿵")
        val cells = CatalogJangdanPattern(gueum, listOf(gueum.size)).jangguCells()!!
        assertTrue(cells.all { it.stroke != null })
        assertEquals(JangguStroke.Hap, cells[gueum.indexOf("떵")].stroke)
        assertEquals(JangguStroke.Buk, cells[gueum.indexOf("궁")].stroke)
        assertEquals(JangguStroke.Chae, cells[gueum.indexOf("따")].stroke)
        assertEquals(JangguStroke.GiDeok, cells[gueum.indexOf("기닥")].stroke)
    }

    @Test fun unknownGueumOrMismatchedGroupsHideTheRow() {
        assertNull(CatalogJangdanPattern(listOf("덩", "모름"), listOf(2)).jangguCells())
        assertNull(CatalogJangdanPattern(listOf("덩", "덕", "궁"), listOf(2)).jangguCells())
        assertNull(CatalogJangdanPattern(emptyList(), emptyList()).jangguCells())
    }

    @Test fun cellsStayEighteenDpUnlessTheLongestJangdanOverflows() {
        assertEquals(JangguCellMaxSize, jangguCellSize(258.dp, 12))
        assertEquals(JangguCellMaxSize, jangguCellSize(258.dp, 4))
        val narrow = jangguCellSize(200.dp, 12)
        assertTrue(narrow < JangguCellMaxSize)
        assertTrue(narrow * 12 + 2.dp * 11 <= 200.dp)
    }
}

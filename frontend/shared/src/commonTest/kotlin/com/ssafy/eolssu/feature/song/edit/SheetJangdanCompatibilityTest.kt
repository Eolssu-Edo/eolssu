package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SheetJangdanCompatibilityTest {
    @Test fun danmoriAcceptsFourQuarterBeatsAndAShortEndingButRejectsTheOldDoubleLength() {
        val jangdan = CatalogJangdanSummary("7", "단모리", 120, "QUARTER", 8, 4)
        assertTrue(draft("QUARTER", 12, 12, listOf(4, 2)).isCompatibleWith(jangdan))
        assertFalse(draft("QUARTER", 12, 24, listOf(4, 4)).isCompatibleWith(jangdan))
        assertFalse(draft("QUARTER", 12, 12, listOf(8, 8)).isCompatibleWith(jangdan))
        assertFalse(draft("QUARTER", 12, 12, listOf(2, 4)).isCompatibleWith(jangdan), "중간의 짧은 각은 허용하지 않는다")
        assertFalse(draft("QUARTER", 12, 12, listOf(4, 4))
            .isCompatibleWith(jangdan.copy(name = "미등록 장단", beatCount = 4)), "모르는 장단의 칸 음가를 추정하지 않는다")
    }

    @Test fun catalogJangdansUseTheirPatternCellNoteValues() {
        // 기대 정간 수·tick 비율은 각 장단의 악보 음가에서 직접 정한다.
        val cases = listOf(
            Case("세마치", "DOTTED_QUARTER", 9, 3, 12),
            Case("굿거리", "DOTTED_QUARTER", 12, 12, 4),
            Case("중모리", "QUARTER", 12, 12, 12),
            Case("자진모리", "DOTTED_QUARTER", 12, 4, 12),
            Case("중중모리", "DOTTED_QUARTER", 12, 4, 12),
            Case("자진타령", "DOTTED_QUARTER", 12, 4, 12),
            Case("엇모리", "QUARTER", 10, 10, 6),
            Case("단모리", "QUARTER", 8, 4, 12),
            Case("타령", "QUARTER", 12, 12, 6),
            Case("계면가락도드리", "QUARTER", 12, 12, 6),
            Case("세령산", "QUARTER", 10, 10, 18),
        )
        for (case in cases) {
            val jangdan = CatalogJangdanSummary(case.name, case.name, 120, case.bpmUnit, case.patternCells, 1)
            assertTrue(draft(case.bpmUnit, 12, case.cellTicks, listOf(case.perGak, case.perGak))
                .isCompatibleWith(jangdan), case.name)
        }
    }

    @Test fun threeHalfBeatCellsAreAllowedOnlyForSeryeongsan() {
        val seryeongsan = CatalogJangdanSummary("10", "세령산", 63, "QUARTER", 10, 4)
        assertTrue(draft("QUARTER", 12, 18, listOf(10, 5)).isCompatibleWith(seryeongsan))
        val semachi = CatalogJangdanSummary("1", "세마치", 96, "DOTTED_QUARTER", 9, 3)
        assertFalse(draft("DOTTED_QUARTER", 12, 18, listOf(2, 2)).isCompatibleWith(semachi))
    }

    private data class Case(val name: String, val bpmUnit: String, val patternCells: Int, val perGak: Int, val cellTicks: Int)

    private fun draft(bpmUnit: String, beatTicks: Int, cellTicks: Int, sizes: List<Int>): SheetDraft {
        val source = smallScoreData()
        return SheetDraft(
            title = "장단 시간 검증", jangdan = null, bpm = 120,
            cells = List(sizes.sum()) { listOf(ScoreNote.Rest) }, columnSizes = sizes,
            original = source.copy(music = source.music.copy(
                bpmUnit = bpmUnit, ticksPerBeat = beatTicks, ticksPerJeonggan = cellTicks)),
        )
    }
}

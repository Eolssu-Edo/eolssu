package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.*

/** 음원 생성기가 함께 해석하지 못하는 시김새와 적을 수 없는 음을 입력 단계에서 알린다(S15P31E102-1080). */
class SigimsaePlaybackRulesTest {
    private fun state() = SheetEditUiState(EditSource.Mine("31"), smallScoreData().editingDraft("시김새", 90, null))

    @Test fun incompatibleSigimsaeIsReplacedAndAnnounced() {
        val first = state().toggleSigimsae(Sigimsae.Toeseong)
        assertEquals(listOf(Sigimsae.Toeseong), first.draft.sigimsaeAt(0))
        assertNull(first.message)

        val replaced = first.toggleSigimsae(Sigimsae.Chuseong)
        assertEquals(listOf(Sigimsae.Chuseong), replaced.draft.sigimsaeAt(0), "퇴성·추성은 함께 해석되지 않아 새로 고른 것만 남는다")
        assertEquals(SheetEditMessage.SigimsaeReplaced, replaced.message)
        assertEquals(first.draft, replaced.undo().draft, "실행 취소로 되돌린다")

        val kept = first.toggleSigimsae(Sigimsae.Yoseong)
        assertEquals(listOf(Sigimsae.Toeseong, Sigimsae.Yoseong), kept.draft.sigimsaeAt(0), "함께 쓸 수 있으면 그대로 더한다")
        assertNull(kept.message)
    }

    @Test fun jeonseongIsOfferedAndTreatedAsAGraceFigure() {
        assertTrue(Sigimsae.entries.any { it.name == Sigimsae.Jeonseong.name }, "1084 이후 전성은 음원으로 만들 수 있다")
        assertFalse(sigimsaeCompatible("전성", "니레"), "앞꾸밈음은 한 음에 하나만 붙는다")
        assertTrue(sigimsaeCompatible("전성", "요성표"))
    }

    @Test fun pairTableIsSymmetricAndOrdered() {
        assertFalse(sigimsaeCompatible("퇴성", "추성"))
        assertFalse(sigimsaeCompatible("추성", "퇴성"))
        assertFalse(sigimsaeCompatible("니레", "니라"))
        assertTrue(sigimsaeCompatible("요성표", "퇴성"))
        assertTrue(IncompatibleSigimsaePairs.all { pair -> pair.split('|').let { (a, b) -> a < b } })
    }

    @Test fun tappingAnUnavailablePitchExplainsInsteadOfDoingNothing() {
        val state = state().copy(availablePitches = setOf("황" to 0))
        val refused = state.putYul(Yul.Taeju, 0)
        assertEquals(state.draft, refused.draft)
        assertEquals(SheetEditMessage.PitchUnavailable, refused.message)
        assertNull(state.putYul(Yul.Hwangjong, 0).message)
    }
}

package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Yul
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SheetDraftTest {
    private val jangdan = Jangdan("세마치", listOf(3), beats = 1, subdivision = 3)
    private val hwang = ScoreNote.Pitch(Yul.Hwangjong)
    private val tae = ScoreNote.Pitch(Yul.Taeju)

    /** 한 각 3정간 · 2각: [黃][ㅡ][빈] / [太][빈][빈]. */
    private fun draft(sigimsae: Map<Int, Sigimsae> = emptyMap()) = SheetDraft(
        title = "예시", jangdan = jangdan, bpm = 60,
        cells = listOf(listOf(hwang), listOf(ScoreNote.Hold), emptyList(), listOf(tae), emptyList(), emptyList()),
        sigimsae = sigimsae,
    )

    @Test
    fun emptyCellsCountOnlyFullyBlankCells() {
        val split = draft().split(2).put(2, hwang).first
        assertEquals(listOf(4, 5), split.emptyCells, "나눈 정간은 한 자리라도 차면 빈 정간이 아니다")
        assertEquals(listOf(2, 4, 5), draft().emptyCells)
    }

    @Test
    fun songDrawsEmptyCellsAsBlankAndKeepsColumns() {
        val song = draft().toSong()
        assertEquals(2, song.score.columns.size)
        assertEquals(listOf(ScoreNote.Blank), song.score.columns[0][2].notes)
    }

    @Test
    fun putFillsSplitCellTopDownBeforeAdvancing() {
        val split = draft().split(2)
        assertEquals(listOf(ScoreNote.Blank, ScoreNote.Blank), split.cells[2])
        val (first, advanceFirst) = split.put(2, hwang)
        assertFalse(advanceFirst, "빈 자리가 남으면 넘어가지 않는다")
        val (second, advanceSecond) = first.put(2, tae)
        assertTrue(advanceSecond)
        assertEquals(listOf(hwang, tae), second.cells[2])
    }

    @Test
    fun putSymbolDropsSigimsaeAndEraseClearsBoth() {
        val withSig = draft(mapOf(0 to Sigimsae.Yoseong))
        assertTrue(0 !in withSig.put(0, ScoreNote.Rest).first.sigimsae, "율명이 없어진 정간의 시김새는 지운다")
        val erased = withSig.erase(0)
        assertEquals(emptyList(), erased.cells[0])
        assertTrue(erased.sigimsae.isEmpty())
    }

    @Test
    fun sigimsaeOnlyOnPitchCells() {
        assertSame(draft().withSigimsae(1, Sigimsae.Toeseong).sigimsae, draft().sigimsae, "지속 기호 정간에는 붙지 않는다")
        assertEquals(Sigimsae.Toeseong, draft().withSigimsae(0, Sigimsae.Toeseong).sigimsae[0])
        assertEquals(mapOf(ScoreUnitKey("c1-j0", 0, 0) to listOf("요성표")), draft(mapOf(3 to Sigimsae.Yoseong)).annotations())
    }

    @Test
    fun splitStopsAtMaxAndMergeKeepsFirstNote() {
        var d = draft()
        repeat(5) { d = d.split(0) }
        assertEquals(SheetDraft.MAX_SPLIT, d.cells[0].size)
        assertEquals(listOf(hwang), d.merge(0).cells[0])
        assertEquals(emptyList(), draft().split(2).merge(2).cells[2])
    }

    @Test
    fun addAndDeleteColumnShiftSigimsae() {
        val withSig = draft(mapOf(0 to Sigimsae.Yoseong, 3 to Sigimsae.Chuseong))
        val added = withSig.addColumnAfter(0)
        assertEquals(3, added.columnCount)
        assertEquals(listOf(emptyList<ScoreNote>(), emptyList(), emptyList()), added.cells.subList(3, 6))
        assertEquals(mapOf(0 to Sigimsae.Yoseong, 6 to Sigimsae.Chuseong), added.sigimsae)

        val deleted = withSig.deleteColumn(0)
        assertEquals(1, deleted.columnCount)
        assertEquals(listOf(tae), deleted.cells[0])
        assertEquals(mapOf(0 to Sigimsae.Chuseong), deleted.sigimsae)
        assertSame(deleted, deleted.deleteColumn(0), "각이 하나뿐이면 지우지 않는다")
    }

    @Test
    fun shiftPitchMovesWithinKeyboardOrder() {
        assertEquals(listOf(tae), draft().shiftPitch(0, 0, 1).cells[0])
        assertEquals(listOf(hwang), draft().shiftPitch(0, 0, -1).cells[0], "가장 낮은 율명 아래로는 내려가지 않는다")
        assertEquals(draft(), draft().shiftPitch(1, 0, 1), "지속 기호는 바꾸지 않는다")
    }
}

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
    private fun draft(sigimsae: Map<NoteSlot, List<Sigimsae>> = emptyMap()) = SheetDraft(
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
        val withSig = draft(mapOf(NoteSlot(0) to listOf(Sigimsae.Yoseong)))
        assertTrue(NoteSlot(0) !in withSig.put(0, ScoreNote.Rest).first.sigimsae, "율명이 없어진 정간의 시김새는 지운다")
        val erased = withSig.erase(0)
        assertEquals(emptyList(), erased.cells[0])
        assertTrue(erased.sigimsae.isEmpty())
    }

    @Test
    fun sigimsaeOnlyOnPitchCells() {
        assertSame(draft().toggleSigimsae(1, Sigimsae.Toeseong).sigimsae, draft().sigimsae, "지속 기호 정간에는 붙지 않는다")
        assertEquals(listOf(Sigimsae.Toeseong), draft().toggleSigimsae(0, Sigimsae.Toeseong).sigimsaeAt(0))
        assertEquals(mapOf(ScoreUnitKey("c1-j0", 0, 0) to listOf("요성표")), draft(mapOf(NoteSlot(3) to listOf(Sigimsae.Yoseong))).annotations())
    }

    @Test
    fun severalSigimsaeKeepOrderAndRemoveOneByOne() {
        // 음원 생성기가 함께 해석하는 조합으로 쓴다(S15P31E102-1080).
        val added = draft().toggleSigimsae(0, Sigimsae.Chuseong).toggleSigimsae(0, Sigimsae.Yoseong).toggleSigimsae(0, Sigimsae("특강표"))
        assertEquals(listOf(Sigimsae.Chuseong, Sigimsae.Yoseong, Sigimsae("특강표")), added.sigimsaeAt(0), "붙인 순서대로 둔다")
        assertEquals(mapOf(ScoreUnitKey("c0-j0", 0, 0) to listOf("추성", "요성표", "특강표")), added.annotations())
        assertSame(added, added.toggleSigimsae(0, Sigimsae("끊는표")), "한 정간에 ${SheetDraft.MAX_SIGIMSAE}개까지만 붙인다")
        assertEquals(listOf(Sigimsae.Chuseong, Sigimsae("특강표")), added.toggleSigimsae(0, Sigimsae.Yoseong).sigimsaeAt(0), "다시 누르면 그 시김새만 뗀다")
        assertEquals(listOf(Sigimsae.Yoseong, Sigimsae("특강표")), added.removeSigimsae(0, 0).sigimsaeAt(0))
        assertTrue(NoteSlot(0) !in added.withSigimsae(0, emptyList()).sigimsae)
    }

    @Test
    fun registerKeysWriteOctaveNotesInExistingNotation() {
        assertEquals(ScoreNote.Pitch(Yul.CheongTaeju), draft().pitchNote(Yul.Taeju, 1), "예시 악보는 기존 율명으로 적는다")
        assertEquals(ScoreNote.SourcePitch("태", 2), draft().pitchNote(Yul.Taeju, 2), "기존 율명에 없는 음역은 공통 악보 음으로 적는다")
        assertEquals("중청태", (draft().pitchNote(Yul.Taeju, 2) as ScoreNote.SourcePitch).hangul)
        assertEquals("배임", (draft().pitchNote(Yul.Imjong, -1) as ScoreNote.SourcePitch).hangul)
    }

    @Test
    fun oneSubNoteOfASplitCellChangesAlone() {
        val split = draft().split(2).put(2, hwang).first.put(2, tae).first
        assertEquals(listOf(hwang, tae), split.cells[2])
        val changed = split.putAt(2, 1, ScoreNote.Rest)
        assertEquals(listOf(hwang, ScoreNote.Rest), changed.cells[2], "다른 소박과 나눔은 그대로다")
        assertEquals(listOf(ScoreNote.Blank, tae), split.eraseAt(2, 0).cells[2], "소박 하나만 비우고 나눔은 남긴다")
        assertEquals(1, split.subIndexOf(2, 1, 0))
        assertEquals(ScoreUnitKey("c0-j2", 1, 0), split.unitKeyOf(2, 1))
    }

    @Test
    fun sigimsaeGoesOnTheChosenSubNoteAndLeavesWithIt() {
        val split = draft().split(2).put(2, hwang).first.put(2, tae).first
        val onSecond = split.toggleSigimsae(2, Sigimsae.Chuseong, sub = 1)
        assertEquals(listOf(Sigimsae.Chuseong), onSecond.sigimsaeAt(2, 1), "고른 소박에 붙는다")
        assertTrue(onSecond.sigimsaeAt(2, 0).isEmpty(), "첫 소박에는 붙지 않는다")
        assertEquals(mapOf(ScoreUnitKey("c0-j2", 1, 0) to listOf("추성")), onSecond.annotations(), "그 소박 오른쪽에 그린다")
        assertTrue(onSecond.putAt(2, 1, ScoreNote.Rest).sigimsaeAt(2, 1).isEmpty(), "율명이 아니게 되면 그 소박의 시김새만 지운다")
        assertEquals(listOf(Sigimsae.Chuseong), onSecond.putAt(2, 0, ScoreNote.Rest).sigimsaeAt(2, 1), "다른 소박을 고쳐도 남는다")
        assertTrue(onSecond.merge(2).sigimsae.isEmpty(), "합쳐서 없어진 소박의 시김새는 지운다")
        assertEquals(1, split.copy(cells = split.cells.toMutableList().also { it[2] = listOf(ScoreNote.Hold, tae) }).sigimsaeSub(2, null),
            "소박을 고르지 않았으면 첫 율명 소박에 붙인다")
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
        val withSig = draft(mapOf(NoteSlot(0) to listOf(Sigimsae.Yoseong), NoteSlot(3) to listOf(Sigimsae.Chuseong)))
        val added = withSig.addColumnAfter(0)
        assertEquals(3, added.columnCount)
        assertEquals(listOf(emptyList<ScoreNote>(), emptyList(), emptyList()), added.cells.subList(3, 6))
        assertEquals(mapOf(NoteSlot(0) to listOf(Sigimsae.Yoseong), NoteSlot(6) to listOf(Sigimsae.Chuseong)), added.sigimsae)

        val deleted = withSig.deleteColumn(0)
        assertEquals(1, deleted.columnCount)
        assertEquals(listOf(tae), deleted.cells[0])
        assertEquals(mapOf(NoteSlot(0) to listOf(Sigimsae.Chuseong)), deleted.sigimsae)
        assertSame(deleted, deleted.deleteColumn(0), "각이 하나뿐이면 지우지 않는다")
    }

    @Test
    fun shiftPitchMovesWithinKeyboardOrder() {
        assertEquals(listOf(tae), draft().shiftPitch(0, 0, 1).cells[0])
        assertEquals(listOf(hwang), draft().shiftPitch(0, 0, -1).cells[0], "가장 낮은 율명 아래로는 내려가지 않는다")
        assertEquals(draft(), draft().shiftPitch(1, 0, 1), "지속 기호는 바꾸지 않는다")
    }

    /** 나누기·합치기 버튼은 실제로 바뀌는 정간에서만 켠다(S15P31E102-1096). */
    @Test
    fun splitAndMergeAreAvailableOnlyWhenTheyChangeTheCell() {
        val base = draft()
        assertTrue(base.canSplit(2))
        assertFalse(base.canMerge(2), "나누지 않은 정간은 합칠 게 없다")
        val full = base.split(2).split(2)
        assertEquals(SheetDraft.MAX_SPLIT, full.cells[2].size)
        assertFalse(full.canSplit(2), "최대로 나눈 정간은 더 나누지 않는다")
        assertSame(full, full.split(2))
        assertTrue(full.canMerge(2))
        assertSame(base, base.merge(0))
        assertFalse(base.canSplit(99))
    }
}

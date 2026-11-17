package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.data.song.CommonScoreData
import kotlin.test.*

class SheetPitchCorrectionTest {
    @Test fun correctingMissingGlyphKeepsRawNoteDurationTiesAndBreath() {
        val raw = smallScoreData().let { score -> score.copy(events = score.events.mapIndexed { index, event ->
            if (index == 1) event.copy(octave = 3) else event
        }, columns = score.columns.map { column -> column.copy(cells = column.cells.map { cell ->
            cell.copy(units = cell.units.map { unit ->
                if (unit.eventId == score.events[1].id && "${cell.id}-r${unit.row}-u${unit.col}" == unit.eventId)
                    unit.copy(symbol = "") else unit
            })
        }) }) }
        val initial = state().copy(draft = raw.editingDraft("raw", 60, null))
        val target = initial.pitchCorrectionsAt(1).single()
        assertEquals(Yul.Jungnyeo to 3, target.pitch)
        val changed = initial.applyPitchCorrection(target, initial.pitchAlternatives(target.pitch).first())
        val after = changed.draft.toCommonScore()
        assertEquals(raw.events.map { Triple(it.id, it.startTick, it.durationTick) }, after.events.map { Triple(it.id, it.startTick, it.durationTick) })
        assertEquals(raw.events.map { it.breathAfter to it.sigimsae }, after.events.map { it.breathAfter to it.sigimsae })
        assertEquals(raw.columns[0].cells[1], after.columns[0].cells[1])
        assertEquals(initial.draft, changed.undo().draft)
    }
    @Test fun staffShiftSkipsGloballyRegisteredNotesWithoutInstrumentSupport() {
        val selected = state().copy(availablePitches = setOf("황" to 0, "대" to 0, "태" to 0),
            instrumentPitches = setOf("황" to 0, "태" to 0)).selectStaffNote(StaffNoteKey(0, 0))
        val shifted = selected.shiftStaffNote(1)
        assertEquals("태" to 0, shifted.draft.cells[0][0].pitchKey()?.let { it.first.hangul to it.second })
    }

    @Test fun requiredCatalogFailureDisablesNewInputWithoutDiscardingTheDraft() {
        val initial = state().copy(completionCatalogState = SheetCompletionCatalogState.Failed)
        assertEquals(emptySet(), initial.supportedInputPitches)
        // 적지 않을 때는 악보를 그대로 두고 이유를 알린다(S15P31E102-1080).
        assertEquals(initial.copy(message = SheetEditMessage.PitchUnavailable), initial.putYul(Yul.Hwangjong, 0))
        assertEquals(initial.draft, initial.copy(completionCatalogState = SheetCompletionCatalogState.Loading).draft)
    }
    private val registered = setOf("황" to 0, "중" to 1, "황" to 2, "태" to 2, "중" to 2, "대" to 1)
    private fun state() = SheetEditUiState(EditSource.Mine("31"), smallScoreData().editingDraft("검사", 60, null))
        .copy(availablePitches = registered, instrumentPitches = registered - ("중" to 1))

    @Test fun clickingContinuationFindsTheStartingPitchInsteadOfReplacingTheHold() {
        val state = state()
        val target = state.pitchCorrectionsAt(1).single()
        assertEquals(0, target.originCell)
        assertEquals(1, target.originSub)
        assertEquals(Yul.Jungnyeo to 1, target.pitch)
        assertTrue(target.isContinuation)
        assertEquals(state.draft, state.copy().draft, "후보 조회는 악보를 바꾸지 않는다")
        assertTrue(state.pitchCorrectionsAt(2).isEmpty(), "쉼은 음역 오류가 아니다")
    }

    @Test fun unregisteredJungCheongDaeHasNearestSupportedPitchesAndSameYulOctaveAlternative() {
        val state = state()
        val choices = state.pitchAlternatives(Yul.Daeryeo to 2)
        assertEquals(listOf(Yul.Hwangjong to 2, Yul.Taeju to 2), choices.take(2))
        assertTrue(Yul.Daeryeo to 1 in choices)
        assertTrue(choices.all { (yul, octave) -> yul.hangul to octave in state.supportedInputPitches.orEmpty() })
        assertFalse(Yul.Daeryeo to 2 in choices)
    }

    @Test fun missingCatalogNeverInventsCandidatesOrDeclaresAValidPitchUnsupported() {
        val unknown = state().copy(availablePitches = null, instrumentPitches = null)
        assertTrue(unknown.pitchAlternatives(Yul.Daeryeo to 2).isEmpty())
        assertTrue(unknown.pitchCorrectionsAt(0).isEmpty())
        val valid = state().copy(instrumentPitches = registered)
        assertTrue(valid.pitchCorrectionsAt(1).isEmpty())
    }

    @Test fun applyingATiedPitchCorrectionPreservesEventsAndSupportsOneStepUndo() {
        val initial = state()
        val before = initial.draft.toCommonScore()
        val target = initial.pitchCorrectionsAt(1).single()
        val choice = initial.pitchAlternatives(target.pitch).first()
        val changed = initial.applyPitchCorrection(target, choice)
        val after = changed.draft.toCommonScore()
        assertEquals(1, changed.past.size)
        assertEquals(initial.draft.cells[1], changed.draft.cells[1], "지속은 새 음으로 바꾸지 않는다")
        assertEquals(before.events.map { Triple(it.id, it.startTick, it.durationTick) }, after.events.map { Triple(it.id, it.startTick, it.durationTick) })
        assertEquals(before.events.map { it.sigimsae }, after.events.map { it.sigimsae })
        assertEquals(before.columns.flatMap { it.cells }.flatMap { it.units }.map { it.eventId }, after.columns.flatMap { it.cells }.flatMap { it.units }.map { it.eventId })
        assertEquals(initial.draft, changed.undo().draft)
        assertTrue(changed.pitchCorrectionsAt(1).isEmpty())
        assertEquals(changed, changed.applyPitchCorrection(target, choice), "이미 바뀐 시작 음에는 오래된 후보를 적용하지 않는다")
    }

    @Test fun inputAndRecommendationRespectInstrumentCatalogAndConfirmationGuard() {
        val initial = state()
        assertEquals(initial.copy(message = SheetEditMessage.PitchUnavailable), initial.putYul(Yul.Jungnyeo, 1), "저장할 수 없는 악기 범위 음을 새로 입력하지 않는다")
        val target = initial.pitchCorrectionsAt(1).single()
        val locked = initial.copy(confirming = true)
        assertEquals(locked, locked.applyPitchCorrection(target, initial.pitchAlternatives(target.pitch).first()))
        assertEquals(initial, initial.applyPitchCorrection(target, Yul.Daeryeo to 2))
    }

    @Test fun crossGakTiesSurviveOtherEditsAndPitchCorrection() {
        val id = "c0-j0-r0-u0"
        val source = smallScoreData().copy(
            columns = listOf(
                CommonScoreData.Column(0, listOf(CommonScoreData.Cell("c0-j0", 0, listOf(CommonScoreData.Unit(0, 0, "청중", id))))),
                CommonScoreData.Column(1, (0..3).map { j -> CommonScoreData.Cell("c1-j$j", j, listOf(CommonScoreData.Unit(0, 0, if (j == 1) "" else "-", id))) }),
            ),
            events = listOf(CommonScoreData.Event(id, "NOTE", "중", 1, 0, 20, "c0-j0", false, emptyList())),
        )
        source.toScore()
        val initial = state().copy(draft = source.editingDraft("각을 넘는 음", 60, null))
        val annotated = initial.toggleSigimsae(Sigimsae.Yoseong)
        val encoded = annotated.draft.toCommonScore()
        encoded.toScore()
        assertEquals(20, encoded.events.single().durationTick)
        assertEquals(listOf("요성표"), encoded.events.single().sigimsae)
        val target = annotated.pitchCorrectionsAt(4).single()
        val changed = annotated.applyPitchCorrection(target, annotated.pitchAlternatives(target.pitch).first())
        val after = changed.draft.toCommonScore()
        after.toScore()
        assertEquals(20, after.events.single().durationTick)
        assertEquals(listOf("요성표"), after.events.single().sigimsae)
        assertEquals(source.columns.flatMap { it.cells }.flatMap { it.units }.map { it.eventId }, after.columns.flatMap { it.cells }.flatMap { it.units }.map { it.eventId })
        assertEquals(annotated.draft, changed.undo().draft)
    }

    @Test fun legacyDashThatStartsARestKeepsItsSilenceAfterAnnotationEditing() {
        val note = "c0-j0-r0-u0"
        val rest = "c1-j0-r0-u0"
        val source = smallScoreData().copy(
            columns = listOf(
                CommonScoreData.Column(0, listOf(CommonScoreData.Cell("c0-j0", 0, listOf(CommonScoreData.Unit(0, 0, "임", note))))),
                CommonScoreData.Column(1, listOf(CommonScoreData.Cell("c1-j0", 0, listOf(CommonScoreData.Unit(0, 0, "-", rest))))),
            ),
            events = listOf(
                CommonScoreData.Event(note, "NOTE", "임", 0, 0, 4, "c0-j0", false, emptyList()),
                CommonScoreData.Event(rest, "REST", null, null, 4, 4, "c1-j0", false, emptyList()),
            ),
        )
        source.toScore()
        val edited = source.editingDraft("기존 쉼", 60, null).toggleSigimsae(0, Sigimsae.Yoseong).toCommonScore()
        edited.toScore()
        assertEquals(source.events.map { Triple(it.id, it.kind, it.durationTick) }, edited.events.map { Triple(it.id, it.kind, it.durationTick) })
        assertEquals(rest, edited.columns[1].cells.single().units.single().eventId)
    }
    @Test fun missingNotationRendersOriginalPitchWithoutRewritingDraft() {
        for (octave in listOf(2, 3)) {
            val source = smallScoreData().let { original -> original.copy(
                columns = original.columns.map { column -> column.copy(cells = column.cells.map { cell ->
                    cell.copy(units = cell.units.map { it.copy(symbol = "") })
                }) },
                events = original.events.map { it.copy(kind = "NOTE", yul = "대", octave = octave) },
            ) }
            val draft = source.editingDraft("표시 확인", 60, null)
            val before = draft.toCommonScore()
            val shown = draft.renderEditingScore().columns.first().first().notes.first()
            assertEquals(com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("대", octave), shown)
            assertTrue(shown.symbol.isNotBlank())
            assertEquals(before, draft.toCommonScore())
        }
    }

}

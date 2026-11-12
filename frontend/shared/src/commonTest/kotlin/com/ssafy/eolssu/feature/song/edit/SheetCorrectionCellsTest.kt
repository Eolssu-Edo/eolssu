package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetCorrectionCellsTest : CatalogViewModelTest() {
    private val allowed = setOf("황" to 0, "태" to 0)
    private fun state() = SheetEditUiState(EditSource.Mine("31"), smallScoreData().editingDraft("검사", 60, null))

    @Test fun numericTempoLocationsStayFixedWhilePitchAndBaseTempoRemainEditable() {
        val timed = state().copy(hasTempoPlan = true)
        listOf(timed.split(), timed.merge(), timed.addColumn(), timed.deleteColumn()).forEach { changed ->
            assertEquals(timed.draft, changed.draft, "구간 빠르기 위치를 보존한다")
            assertNotNull(changed.message)
            assertFalse(changed.canUndo)
        }
        assertNotEquals(timed.draft, timed.selectUnit(0, 1).putYul(Yul.Taeju).draft)
        assertEquals(120, timed.updateDetails("검사", 120).draft.bpm)
        assertNotEquals(timed.draft, timed.copy(hasTempoPlan = false).addColumn().draft)
    }

    @Test fun allEmptyInvalidPitchAndPendingCellsAreHighlightedInScoreOrder() {
        val state = state().copy(availablePitches = allowed,
            reviewIssues = listOf(SheetReviewIssue("a", "c0-j1", "LOW_CONFIDENCE"), SheetReviewIssue("b", "c0-j1", "ORNAMENT_DROPPED")))
            .select(2).erase()
        assertEquals(setOf(0, 1, 2), state.completionValidation.correctionCellIndices)
        assertEquals(0, state.completionValidation.firstCellIndex, "오류 종류와 무관하게 악보의 첫 수정 위치로 간다")
        assertEquals(2, state.completionValidation.pendingReviewCount)
    }

    @Test fun correctingTheStartPitchAlsoClearsItsTiedCellAndUndoRestoresBoth() {
        val state = state().copy(availablePitches = allowed)
        assertEquals(setOf(0, 1), state.completionValidation.correctionCellIndices)
        val fixed = state.selectUnit(0, 1).putYul(Yul.Taeju)
        assertTrue(fixed.completionValidation.correctionCellIndices.isEmpty())
        assertEquals(setOf(0, 1), fixed.undo().completionValidation.correctionCellIndices)
    }

    @Test fun reviewCellUsesItsOriginalIdAfterColumnsAreInserted() {
        val issue = SheetReviewIssue("a", "c1-j0", "LOW_CONFIDENCE")
        val shifted = state().copy(reviewIssues = listOf(issue)).select(0).addColumn()
        assertEquals("c1-j0", shifted.draft.sourceCellIds?.get(4))
        assertEquals(setOf(2, 3, 4), shifted.completionValidation.correctionCellIndices)
        assertEquals(setOf(2, 3), shifted.copy(checkedIssueIds = setOf(issue.id)).completionValidation.correctionCellIndices)
        assertEquals(setOf(2), shifted.undo().completionValidation.correctionCellIndices)
    }

    @Test fun allBrokenEventLinksAreHighlightedEvenWhenScoreValidationStopsAtTheFirst() {
        val score = smallScoreData().let { original -> original.copy(columns = original.columns.mapIndexed { index, column ->
            if (index != 0) column else column.copy(cells = column.cells.map { cell ->
                cell.copy(units = cell.units.map { it.copy(eventId = "missing-${cell.id}") })
            })
        }) }
        val state = SheetEditUiState(EditSource.Mine("31"), score.editingDraft("끊긴 연결", 60, null))
        assertTrue(state.completionValidation.hasInvalidStructure)
        assertEquals(setOf(0, 1), state.completionValidation.correctionCellIndices)
        assertEquals(0, state.completionValidation.firstCellIndex)
    }

    @Test fun anOverlongLastRestHighlightsItsCellEvenWhenOnlyTotalDurationValidationFails() {
        val score = smallScoreData().let { original -> original.copy(events = original.events.map { event ->
            if (event.kind == "REST") event.copy(durationTick = 8) else event
        }) }
        val state = SheetEditUiState(EditSource.Mine("31"), score.editingDraft("긴 쉼", 60, null))
        assertTrue(state.completionValidation.hasInvalidStructure)
        assertEquals(setOf(2), state.completionValidation.correctionCellIndices)
        assertEquals(2, state.completionValidation.firstCellIndex)
    }

    @Test fun aShortenedTiedNoteHighlightsItsOriginAndTiesWithoutBlamingTheFollowingRest() {
        val score = smallScoreData().let { original -> original.copy(events = original.events.map { event ->
            if (event.id == "c0-j0-r0-u1") event.copy(durationTick = 6) else event
        }) }
        val state = SheetEditUiState(EditSource.Mine("31"), score.editingDraft("짧은 이음", 60, null))
        assertTrue(state.completionValidation.hasInvalidStructure)
        assertEquals(setOf(0, 1), state.completionValidation.correctionCellIndices)
        assertEquals(0, state.completionValidation.firstCellIndex)
    }

    @Test fun blockedSaveFocusesTheFirstCorrectionAndAcknowledgesTheSelectedIssue() = runTest {
        val repository = RecordingDocuments(listOf(issue("last", "c1-j0"), issue("first", "c0-j0")))
        val model = open(repository)
        runCurrent()
        model.selectCell(2); model.selectView(EditView.Staff)
        model.requestSave(); runCurrent()
        assertTrue(repository.writes.isEmpty())
        assertEquals(0, model.uiState.value.selected)
        assertEquals(EditView.Jeongganbo, model.uiState.value.view)
        assertEquals("first", model.uiState.value.selectedReviewIssue?.id)
        assertTrue(model.uiState.value.canCheckSelectedReviewIssue)
        model.checkSelectedReviewIssue()
        assertEquals(setOf("first"), model.uiState.value.checkedIssueIds)
        assertEquals(setOf(2), model.uiState.value.completionValidation.correctionCellIndices)
        model.openCompletionIssue()
        assertEquals(2, model.uiState.value.selected)
        assertFalse(model.uiState.value.completionWarning)
        model.checkSelectedReviewIssue()
        assertTrue(model.uiState.value.completionValidation.correctionCellIndices.isEmpty())
        model.requestSave(); runCurrent()
        assertEquals(setOf("first", "last"), repository.writes.single().resolvedIssueIds.toSet())
        assertTrue(model.uiState.value.reviewIssues.isEmpty())
        assertTrue(model.uiState.value.completionValidation.correctionCellIndices.isEmpty())
    }

    @Test fun savedPitchCorrectionLosesItsHighlightAndUndoRevealsTheActualErrorAgain() = runTest {
        val repository = RecordingDocuments(listOf(issue("pitch", "c0-j0", "UNREGISTERED_PITCH")))
        val catalog = object : ReferenceCatalogRepository {
            override suspend fun allPitches() = allowed.map { (yul, octave) -> CatalogPitch("$yul-$octave", yul, octave, yul, yul, 0) }
            override suspend fun pitches(instrumentId: String): List<CatalogPitch> = error("unused")
            override suspend fun jangdans(): List<CatalogJangdanSummary> = error("unused")
            override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = error("unused")
            override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
            override suspend fun symbols(): List<CatalogNotationSymbol> = error("unused")
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog))
        runCurrent()
        model.selectUnit(0, 1); model.putYul(Yul.Taeju)
        assertEquals(setOf(0), model.uiState.value.completionValidation.correctionCellIndices, "수정 후 확인할 항목은 남는다")
        model.checkSelectedReviewIssue()
        model.requestSave(); runCurrent()
        assertTrue(model.uiState.value.completionValidation.correctionCellIndices.isEmpty())
        model.undo()
        assertEquals(setOf(0, 1), model.uiState.value.completionValidation.correctionCellIndices)
    }

    @Test fun tempoAndJangdanApplyAsOneUndoStepWithoutChangingTitleOrTiming() {
        val option = CatalogJangdanSummary("4", "굿거리", 80, "DOTTED_QUARTER", 3, 1, 1)
        val initial = state().let { it.copy(draft = it.draft.copy(jangdanId = "3"), availableJangdans = listOf(option)) }
        val changed = initial.updateSettings(120, "4")
        assertEquals(1, changed.past.size)
        assertEquals(120 to "4", changed.draft.bpm to changed.draft.jangdanId)
        assertEquals(initial.draft.title, changed.draft.title)
        assertEquals(initial.draft.toCommonScore().events, changed.draft.toCommonScore().events)
        assertEquals(initial.draft.toCommonScore().music.copy(jangdan = "굿거리"), changed.draft.toCommonScore().music)
        assertEquals(initial.draft, changed.undo().draft)
        val cleared = changed.updateSettings(90, null)
        assertEquals(90 to null, cleared.draft.bpm to cleared.draft.jangdanId)
        assertFalse(cleared.withJangdan)
        assertEquals(changed.draft, cleared.undo().draft)
    }

    @Test fun settingsKeepCurrentJangdanWithoutMetadataButRejectAnInvalidReplacementAtomically() {
        val initial = state().let { it.copy(draft = it.draft.copy(jangdanId = "3")) }
        assertEquals(120, initial.updateSettings(120, "3").draft.bpm)
        assertEquals(initial, initial.updateSettings(120, "unknown"))
        assertEquals(initial, initial.updateSettings(0, null))
    }

    @Test fun applyingSettingsCancelsPendingJangdanLoadSoALateResultCannotReopenThePicker() = runTest {
        for (selectedId in listOf(null, "3")) for (fails in listOf(false, true)) {
            val result = CompletableDeferred<List<CatalogJangdanSummary>>()
            val catalog = object : ReferenceCatalogRepository {
                override suspend fun allPitches() = (allowed + ("중" to 1)).map { (yul, octave) ->
                    CatalogPitch("$yul-$octave", yul, octave, yul, yul, 0)
                }
                override suspend fun pitches(instrumentId: String): List<CatalogPitch> = error("unused")
                override suspend fun jangdans() = result.await()
                override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = error("unused")
                override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
                override suspend fun symbols(): List<CatalogNotationSymbol> = error("unused")
            }
            val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
                SampleMySheetRepository(), documents = RecordingDocuments(emptyList(), jangdanId = "3"), pitchCatalog = catalog))
            runCurrent()
            model.changeJangdan(); runCurrent()
            assertIs<SheetEditJangdanPicker.Loading>(model.uiState.value.jangdanPicker)

            model.updateSettings(120, selectedId)
            val applied = model.uiState.value.draft
            assertEquals(120 to selectedId, applied.bpm to applied.jangdanId)
            assertEquals(1, model.uiState.value.past.size, "빠르기·장단을 한 번에 적용한다")
            assertNull(model.uiState.value.jangdanPicker)
            if (fails) result.completeExceptionally(IllegalStateException("late catalog failure"))
            else result.complete(listOf(CatalogJangdanSummary("3", "세마치", 80, "DOTTED_QUARTER", 3, 1, 1)))
            runCurrent()

            assertNull(model.uiState.value.jangdanPicker, "적용 후 늦은 조회 결과가 선택 창을 다시 열면 안 된다")
            assertEquals(applied, model.uiState.value.draft)
        }
    }

    private fun open(repository: SheetDocumentRepository) = keep(SheetEditViewModel(EditSource.Mine("31"),
        EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))

    private fun issue(id: String, cellId: String, kind: String = "LOW_CONFIDENCE") =
        Json.parseToJsonElement("""{"issueId":"$id","cellId":"$cellId","type":"$kind"}""") as JsonObject

    private class RecordingDocuments(private val issues: List<JsonObject>, private val jangdanId: String? = null) : SheetDocumentRepository {
        val writes = mutableListOf<SheetWorkingCopy>()
        override suspend fun get(sheetId: String) = SheetDocument("31", "검사", jangdanId, 60, "1",
            smallScoreData(), null, "NEEDS_REVIEW", "DONE", issues)
        override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
            if (!draft) writes += copy
            return if (draft) SheetWrite.Saved(copy.baseScoreVersion, copy.revision + 1)
            else SheetWrite.Saved("2", reviewStatus = "EVALUABLE")
        }
    }
}

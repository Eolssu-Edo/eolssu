package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetDocumentViewModelTest : CatalogViewModelTest() {
    private fun document(draft: SheetWorkingCopy? = null) = SheetDocument("31", "실제 악보", null, 60, "1", smallScoreData(), draft, "EVALUABLE", null, emptyList())

    @Test fun practiceAudioOpensTheConfirmedScoreAndIgnoresAnEditorDraft() = runTest {
        val draft = SheetWorkingCopy("이어 쓰기", null, 55, smallScoreData(), "1", 3)
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(draft)
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite =
                error("The audio screen must not write a score")
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31", practiceVersion = 1), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository))
        runCurrent()

        val state = model.uiState.value
        assertFalse(state.documentFailed)
        assertTrue(state.savedOnce)
        assertEquals("실제 악보", state.draft.title)
        assertEquals(60, state.draft.bpm)
    }

    @Test fun practiceAudioRejectsAScoreVersionDifferentFromThePracticeScreen() = runTest {
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document().copy(scoreVersion = "2")
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite =
                error("The audio screen must not write a score")
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31", practiceVersion = 1), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository))
        runCurrent()

        val state = model.uiState.value
        assertFalse(state.documentLoading)
        assertTrue(state.practiceVersionChanged)
        assertFalse(state.savedOnce)
    }

    @Test fun closeDoesNotSkipAnInFlightConfirmationEvenWhenDraftWasAlreadySaved() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        var closed = false
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean) = reply.await()
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent(); model.requestSave(); runCurrent()
        model.closeEditor { closed = true }; runCurrent()
        assertFalse(closed)
        reply.complete(SheetWrite.Conflict); runCurrent()
        assertFalse(closed)
        assertTrue(model.uiState.value.closeWarning)
    }

    @Test fun closingImmediatelyFlushesPendingEditAndWaitsForAcknowledgement() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        var closed = false
        var writes = 0
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                writes++; assertTrue(draft); return reply.await()
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent(); model.selectCell(2); model.put(ScoreNote.Pitch(Yul.Taeju))
        model.closeEditor { closed = true }; runCurrent()
        assertFalse(closed)
        assertEquals(1, writes, "1초 자동 저장 대기를 기다리지 않고 초안을 저장한다")
        reply.complete(SheetWrite.Saved("1", 1)); runCurrent()
        assertTrue(closed)
    }

    @Test fun failedExitSaveKeepsScreenAndAsksBeforeDiscardingEdits() = runTest {
        var closed = false
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean) = SheetWrite.Failed
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent(); model.selectCell(2); model.erase()
        model.closeEditor { closed = true }; runCurrent()
        assertFalse(closed)
        assertTrue(model.uiState.value.closeWarning)
        assertTrue(model.uiState.value.draft.cells[2].isEmpty())
        model.continueEditing()
        assertFalse(model.uiState.value.closeWarning)
    }

    @Test fun autoSaveRestoresAndAdvancesServerRevisionAndKeepsBlankCells() = runTest {
        val writes = mutableListOf<SheetWorkingCopy>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(SheetWorkingCopy("이어 쓰기", null, 55, smallScoreData(), "1", 3))
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft); writes += copy
                return SheetWrite.Saved(copy.baseScoreVersion, copy.revision + 1)
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        assertEquals("이어 쓰기", model.uiState.value.draft.title)
        assertEquals(55, model.uiState.value.draft.bpm)
        model.selectCell(2); model.erase()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(3, writes.single().revision)
        assertTrue(writes.single().score.columns[1].cells[0].units.isEmpty())
        assertNull(model.uiState.value.message, "자동 저장 성공 안내를 매번 띄우지 않는다")
        model.saveDraft(); runCurrent()
        assertEquals(4, writes.last().revision)
        assertEquals(SheetEditMessage.Draft, model.uiState.value.message)
    }

    @Test fun conflictKeepsEditsAndDoesNotAutomaticallyOverwriteNewerVersion() = runTest {
        var calls = 0
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite { calls++; return SheetWrite.Conflict }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent(); model.selectCell(2); model.put(ScoreNote.Pitch(Yul.Taeju))
        val edited = model.uiState.value.draft
        model.requestSave(); runCurrent()
        assertEquals(SheetEditMessage.SaveConflict, model.uiState.value.message)
        assertEquals(edited, model.uiState.value.draft)
        model.saveDraft(); advanceTimeBy(2_000); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun accountChangeClearsDocumentAndCancelsAnOldPendingLoad() = runTest {
        val member = SignedInMember(FakeKeyValueStore()).apply { set("member-a") }
        val result = CompletableDeferred<SheetDocument?>()
        var saves = 0
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = result.await()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite { saves++; return SheetWrite.Failed }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository, member = member))
        runCurrent(); member.set("member-b"); result.complete(document()); runCurrent()
        assertTrue(model.uiState.value.documentFailed)
        assertEquals("", model.uiState.value.draft.title)
        model.saveDraft(); runCurrent()
        assertEquals(0, saves)
    }

    @Test fun loadsRealScoreAndOnlyReportsSaveAfterServerSuccess() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        val writes = mutableListOf<SheetWorkingCopy>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = SheetDocument("31", "실제 악보", null, 60, "1", smallScoreData(), null, "NEEDS_REVIEW", "DONE", listOf(kotlinx.serialization.json.Json.parseToJsonElement("""{"issueId":"i1","cellId":"c0-j1","type":"OUT_OF_RANGE"}""") as kotlinx.serialization.json.JsonObject))
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                writes += copy
                assertFalse(draft)
                return reply.await()
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        assertTrue(model.uiState.value.documentLoading)
        runCurrent()
        assertEquals("실제 악보", model.uiState.value.draft.title)
        assertEquals(listOf(2, 1), model.uiState.value.draft.sizes)
        assertEquals(1, model.uiState.value.pendingChecks)
        model.openNextReviewCell()
        assertEquals(1, model.uiState.value.selected)
        model.checkReviewIssue()
        assertEquals(setOf("i1"), model.uiState.value.checkedIssueIds)
        model.selectView(EditView.Staff)
        assertEquals(EditView.Staff, model.uiState.value.view)
        model.selectView(EditView.Jeongganbo)
        assertEquals(1, model.uiState.value.selected, "보기 전환은 정간 위치를 유지한다")
        model.selectCell(2)
        model.put(ScoreNote.Pitch(Yul.Taeju))
        model.requestSave()
        runCurrent()
        assertNull(model.uiState.value.message)
        assertTrue(model.uiState.value.saving)
        reply.complete(SheetWrite.Saved("2", reviewStatus = "EVALUABLE"))
        runCurrent()
        assertEquals(SheetEditMessage.SavedForPractice, model.uiState.value.message)
        assertFalse(model.uiState.value.saving)
        assertEquals("1", writes.single().baseScoreVersion)
        assertEquals(listOf("i1"), writes.single().resolvedIssueIds)
        assertTrue(model.uiState.value.reviewIssues.isEmpty())
        assertEquals("태", writes.single().score.events.last().yul)
    }

    @Test fun detailsEditIsOneUndoStepAndPersistsBothValuesWithoutChangingTheScoreTiming() = runTest {
        val writes = mutableListOf<Pair<Boolean, SheetWorkingCopy>>()
        val original = document()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = original
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                writes += draft to copy
                return if (draft) SheetWrite.Saved("1", 1) else SheetWrite.Saved("2")
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.updateDetails("   ", 90)
        model.updateDetails("가".repeat(51), 90)
        model.updateDetails("잘못된 빠르기", 29)
        model.updateDetails("잘못된 빠르기", 161)
        assertFalse(model.uiState.value.canUndo)

        val title = "가".repeat(49) + "🎵"
        model.updateDetails(" $title ", 90)
        assertEquals(title to 90, model.uiState.value.draft.run { this.title to bpm })
        model.undo()
        assertEquals("실제 악보" to 60, model.uiState.value.draft.run { this.title to bpm })
        model.redo()
        advanceTimeBy(1_000); runCurrent()
        model.requestSave(); runCurrent()

        assertEquals(listOf(true, false), writes.map { it.first })
        writes.forEach { (_, copy) ->
            assertEquals(title to 90, copy.title to copy.bpm)
            assertEquals(title, copy.score.score.title)
            assertEquals(original.score?.music, copy.score.music)
            assertEquals(original.score?.events, copy.score.events)
        }
        assertEquals(1, writes.last().second.revision)
    }

    @Test fun finalSaveUsesReturnedReviewStatusWhenReviewIsStillRequired() = runTest {
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document().copy(reviewStatus = "NEEDS_REVIEW")
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertFalse(draft)
                return SheetWrite.Saved("2", reviewStatus = "NEEDS_REVIEW")
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.requestSave()
        runCurrent()

        assertEquals(SheetEditMessage.SavedNeedsReview, model.uiState.value.message)
    }
}

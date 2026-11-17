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
import kotlinx.serialization.json.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetDocumentViewModelTest : CatalogViewModelTest() {
    private fun document(draft: SheetWorkingCopy? = null) = SheetDocument("31", "실제 악보", null, 60, "1", smallScoreData(), draft, "EVALUABLE", null, emptyList())

    @Test fun tempoDurationSurvivesDraftSaveReopenAndFinalChangeDeletion() = runTest {
        var stored: SheetWorkingCopy? = null
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(stored)
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                stored = copy.copy(revision = copy.revision + 1)
                return SheetWrite.Saved(copy.baseScoreVersion, copy.revision + 1)
            }
        }
        fun open() = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        val model = open()
        runCurrent()
        model.updateTempoSettings(model.uiState.value.draft.withTempoChange(
            SheetTempoChange(1, com.ssafy.eolssu.core.model.HanbaeMark.Faster, 120.0, 3)).copy(bpm = 90))
        model.saveDraft(); runCurrent()
        assertNotNull(stored?.expressions)
        val reopened = open()
        runCurrent()
        assertTrue(reopened.uiState.value.hasTempoPlan)
        assertEquals(4.0 / 3, assertNotNull(reopened.uiState.value.draft.estimatedDurationSeconds(90)), 1e-9)
        reopened.updateTempoSettings(reopened.uiState.value.draft.withoutTempoChange(1))
        assertEquals(2.0, assertNotNull(reopened.uiState.value.draft.estimatedDurationSeconds(90)), 1e-9)
        reopened.saveDraft(); runCurrent()
        val withoutChanges = open()
        runCurrent()
        assertTrue(withoutChanges.uiState.value.draft.tempoChanges.isEmpty())
        assertEquals(2.0, assertNotNull(withoutChanges.uiState.value.draft.estimatedDurationSeconds(90)), 1e-9)
    }

    @Test fun adaptationCannotOverwriteTheDraftWithoutConfirmation() = runTest {
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val catalog = object : ReferenceCatalogRepository {
            override suspend fun authoringCapabilities(instrumentId: String) = capability
            override suspend fun pitches(instrumentId: String): List<CatalogPitch> = emptyList()
            override suspend fun allPitches(): List<CatalogPitch> = emptyList()
            override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
            override suspend fun jangdans(): List<CatalogJangdanSummary> = emptyList()
            override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = null
            override suspend fun symbols(): List<CatalogNotationSymbol> = emptyList()
        }
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean) = SheetWrite.Failed
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, pitchCatalog = catalog, loadAudioInstruments = { listOf(CatalogInstrument("1", "DANSO", "단소")) }))
        runCurrent()
        assertEquals(EditTab.Adaptation, model.uiState.value.tab, "처음에는 음역 맞추기 입력판을 연다")
        assertNotNull(model.uiState.value.adaptation)
        model.selectTab(EditTab.Adaptation); runCurrent(); model.selectAdaptationTranspose(4)
        assertEquals(EditTab.Adaptation, model.uiState.value.tab)
        val before = model.uiState.value.draft
        model.applyAdaptation()
        assertEquals(before, model.uiState.value.draft)
        model.requestApplyAdaptation()
        assertTrue(model.uiState.value.adaptation!!.confirmationRequested)
        model.cancelApplyAdaptation()
        assertEquals(before, model.uiState.value.draft)
        assertEquals(4, model.uiState.value.adaptation!!.selectedTranspose)
        assertFalse(model.uiState.value.adaptation!!.confirmationRequested)
        model.requestApplyAdaptation(); model.applyAdaptation()
        val applied = model.uiState.value.draft
        assertNotEquals(before, applied)
        assertFalse(assertNotNull(model.uiState.value.adaptation).loading)
        assertEquals(4, model.uiState.value.adaptation?.selectedTranspose)
        assertEquals(applied, model.uiState.value.adaptation?.baseline)
        assertEquals(EditTab.Adaptation, model.uiState.value.tab)
        model.undo()
        assertEquals(before, model.uiState.value.draft)
        model.redo()
        assertEquals(applied, model.uiState.value.draft)
        model.openAdaptation(); runCurrent(); model.requestApplyAdaptation()
        model.updateDetails("다른 편집", 100)
        model.applyAdaptation()
        assertEquals("다른 편집", model.uiState.value.draft.title)
        // 1042: 편집하면 높이 계산을 바뀐 악보로 다시 하고, 이전 적용 요청은 지운다.
        val refreshed = assertNotNull(model.uiState.value.adaptation)
        assertFalse(refreshed.confirmationRequested, "다른 편집 뒤에 이전 제안을 적용할 수 없다")
        assertEquals(model.uiState.value.draft, refreshed.baseline)
    }

    @Test fun closedAdaptationReplyCannotReplaceAReopenedPanel() = runTest {
        var delayed = false
        val pending = mutableListOf<CompletableDeferred<CatalogAuthoringCapabilities>>()
        fun capability(version: String, id: String = "1") = CatalogAuthoringCapabilities(id, if (id == "1") "DANSO" else "SOGEUM", version, 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), emptyList())
        val catalog = object : ReferenceCatalogRepository {
            override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities =
                if (!delayed || instrumentId == "1") capability("initial", instrumentId)
                else CompletableDeferred<CatalogAuthoringCapabilities>().also { pending += it }.await()
            override suspend fun pitches(instrumentId: String): List<CatalogPitch> = emptyList()
            override suspend fun allPitches(): List<CatalogPitch> = emptyList()
            override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
            override suspend fun jangdans(): List<CatalogJangdanSummary> = emptyList()
            override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = null
            override suspend fun symbols(): List<CatalogNotationSymbol> = emptyList()
        }
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean) = SheetWrite.Failed
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, pitchCatalog = catalog, loadAudioInstruments = {
                listOf(CatalogInstrument("1", "DANSO", "단소"), CatalogInstrument("2", "SOGEUM", "소금")) }))
        runCurrent(); delayed = true
        model.openAdaptation(); model.selectAdaptationInstrument("2"); runCurrent()
        model.closeAdaptation(); model.updateDetails("다른 편집본", 60)
        model.openAdaptation(); runCurrent()
        assertEquals(1, pending.size, "준비된 원래 악기는 다시 요청하지 않는다")
        pending.single().complete(capability("late", "2")); runCurrent()
        assertEquals("1", model.uiState.value.adaptation?.selectedId)
        assertEquals("initial", model.uiState.value.adaptation?.capabilities?.policyVersion)
        assertFalse(assertNotNull(model.uiState.value.adaptation).loading)
        assertEquals("다른 편집본", model.uiState.value.adaptation?.baseline?.title)
    }

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

    @Test fun closingWithUnsavedEditAsksFirstAndSaveDraftAndCloseWaitsForAcknowledgement() = runTest {
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
        assertEquals(0, writes, "닫기만으로는 저장하지 않는다")
        assertTrue(model.uiState.value.closeWarning, "저장하지 않은 변경이 있으면 먼저 묻는다")
        model.saveDraftAndClose { closed = true }; runCurrent()
        assertEquals(1, writes)
        assertFalse(closed, "임시저장 응답 전에는 닫지 않는다")
        assertTrue(model.uiState.value.closeWarning, "저장하는 동안 확인 창에 저장 중을 보인다")
        assertTrue(model.uiState.value.closeSaving)
        model.saveDraftAndClose { closed = true }; model.continueEditing(); runCurrent()
        assertEquals(1, writes, "저장 중 다시 눌러도 한 번만 보낸다")
        assertTrue(model.uiState.value.closeWarning, "저장 중에는 창을 닫지 않는다")
        model.discardChangesAndClose { closed = true }
        assertFalse(closed, "저장 중에는 버리고 닫기도 막는다")
        reply.complete(SheetWrite.Saved("1", 1)); runCurrent()
        assertTrue(closed)
        assertFalse(model.uiState.value.closeWarning)
        assertFalse(model.uiState.value.closeSaving)
    }

    @Test fun conflictDuringSaveDraftAndCloseReleasesTheDialogWithoutClosing() = runTest {
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite = SheetWrite.Conflict
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent(); model.updateDetails("충돌 초안", 60)
        var closed = false
        model.closeEditor { closed = true }; model.saveDraftAndClose { closed = true }; runCurrent()
        assertFalse(closed, "충돌이면 화면을 떠나지 않는다")
        assertTrue(model.uiState.value.saveConflict)
        assertTrue(model.uiState.value.closeWarning)
        assertFalse(model.uiState.value.closeSaving, "충돌 뒤에는 다른 버튼을 다시 누를 수 있다")
        model.saveDraftAndClose { closed = true }; runCurrent()
        assertFalse(closed)
        assertFalse(model.uiState.value.closeSaving, "보낼 수 없는 저장은 저장 중으로 남지 않는다")
        model.discardChangesAndClose { closed = true }
        assertTrue(closed)
    }

    @Test fun saveDraftAndCloseSendsTheDraftOffTheScreenThread() = runTest {
        val worker = StandardTestDispatcher(testScheduler, name = "save-worker")
        var saveThread: kotlin.coroutines.ContinuationInterceptor? = null
        var sent: SheetWorkingCopy? = null
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                saveThread = kotlinx.coroutines.currentCoroutineContext()[kotlin.coroutines.ContinuationInterceptor]
                sent = copy
                return SheetWrite.Saved("1", 1)
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, saveDispatcher = worker))
        runCurrent(); model.updateDetails("백그라운드 초안", 60)
        var closed = false
        model.closeEditor { closed = true }; model.saveDraftAndClose { closed = true }; advanceUntilIdle()
        assertSame(worker, saveThread, "직렬화·전송은 화면 스레드 밖에서 한다")
        assertEquals("백그라운드 초안", sent?.title)
        assertEquals(0, sent?.revision)
        assertTrue(closed)
    }

    @Test fun editsAreNotSavedUntilTheUserAsks() = runTest {
        var writes = 0
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite { writes++; return SheetWrite.Saved("1", 1) }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.selectCell(2); model.erase(); model.updateDetails("새 제목", 60)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, writes, "편집은 자동저장하지 않는다")
        assertNull(model.uiState.value.saveStatus)
        var closed = false
        model.closeEditor { closed = true }; runCurrent()
        assertTrue(model.uiState.value.closeWarning)
        model.continueEditing(); advanceTimeBy(60_000); runCurrent()
        assertFalse(model.uiState.value.closeWarning)
        assertEquals(0, writes, "계속 편집해도 저절로 저장하지 않는다")
        model.discardChangesAndClose { closed = true }
        assertTrue(closed)
        assertEquals(0, writes)
    }

    @Test fun failedSaveDraftAndCloseKeepsTheEditorOpenAndAsksAgain() = runTest {
        val writes = mutableListOf<SheetWorkingCopy>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite { writes += copy; return SheetWrite.Failed }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent(); model.updateDetails("남길 초안", 60)
        var closed = false
        model.closeEditor { closed = true }; model.saveDraftAndClose { closed = true }; runCurrent()
        assertEquals(1, writes.size)
        assertFalse(closed)
        assertTrue(model.uiState.value.closeWarning)
        assertFalse(model.uiState.value.closeSaving, "실패하면 다시 누를 수 있다")
        assertEquals(SheetSaveStatus.DraftFailed, model.uiState.value.saveStatus)
        assertEquals("남길 초안", model.uiState.value.draft.title)
    }

    @Test fun closeDuringDraftSaveWaitsWithoutSendingTheSameDraftTwice() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        var closed = false
        var writes = 0
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                writes++
                return reply.await()
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.selectCell(2)
        model.erase()
        model.saveDraft()
        runCurrent()
        model.closeEditor { closed = true }
        runCurrent()
        assertFalse(closed)

        reply.complete(SheetWrite.Saved("1", 1))
        runCurrent()
        assertEquals(1, writes)
        assertTrue(closed)
    }

    @Test fun closeDuringFailedDraftSaveKeepsTheEditorOpenWithoutRetrying() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        val writes = mutableListOf<SheetWorkingCopy>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                writes += copy
                return reply.await()
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.updateDetails("저장 중인 초안", 60)
        model.saveDraft()
        runCurrent()
        var closed = false
        model.closeEditor { closed = true }
        runCurrent()
        assertFalse(closed)

        reply.complete(SheetWrite.Failed)
        runCurrent()
        assertEquals(1, writes.size)
        assertFalse(closed)
        assertTrue(model.uiState.value.closeWarning)
        assertEquals("저장 중인 초안", model.uiState.value.draft.title)
        assertEquals(SheetSaveStatus.DraftFailed, model.uiState.value.saveStatus)
    }

    @Test fun closeWaitsForDraftSaveAfterUndoAndAsksOnFailure() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        val writes = mutableListOf<SheetWorkingCopy>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                writes += copy
                return if (writes.size == 1) reply.await() else SheetWrite.Saved("1", 1)
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.updateDetails("저장 중인 초안", 60)
        model.saveDraft()
        runCurrent()
        model.updateDetails("대기 중인 초안", 60)
        advanceTimeBy(1_000)
        runCurrent()
        model.undo()

        var closed = false
        model.closeEditor { closed = true }
        runCurrent()
        assertEquals(1, writes.size)
        assertFalse(closed)

        reply.complete(SheetWrite.Failed)
        runCurrent()
        assertEquals(listOf("저장 중인 초안"), writes.map { it.title })
        assertFalse(closed)
        assertTrue(model.uiState.value.closeWarning)
        assertEquals("저장 중인 초안", model.uiState.value.draft.title)
        assertEquals(SheetSaveStatus.DraftFailed, model.uiState.value.saveStatus)
    }

    @Test fun closeDoesNotRetryTheSameDraftAfterUndoAndRedoWhileWaiting() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        val writes = mutableListOf<SheetWorkingCopy>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                writes += copy
                return if (writes.size == 1) reply.await() else SheetWrite.Saved("1", 1)
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.updateDetails("저장 중인 초안", 60)
        model.saveDraft()
        runCurrent()

        var closed = false
        model.closeEditor { closed = true }
        model.undo()
        model.redo()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, writes.size)
        assertFalse(closed)

        reply.complete(SheetWrite.Failed)
        advanceUntilIdle()
        assertEquals(listOf("저장 중인 초안"), writes.map { it.title })
        assertFalse(closed)
        assertTrue(model.uiState.value.closeWarning)
        assertEquals("저장 중인 초안", model.uiState.value.draft.title)
        assertEquals(SheetSaveStatus.DraftFailed, model.uiState.value.saveStatus)
    }

    @Test fun undoAfterLostDraftAcknowledgementUsesTheServerRevision() = runTest {
        val writes = mutableListOf<SheetWorkingCopy>()
        var stored: SheetWorkingCopy? = null
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(stored)
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                writes += copy
                if (copy.revision != (stored?.revision ?: 0)) return SheetWrite.Conflict
                stored = copy.copy(revision = copy.revision + 1)
                return if (writes.size == 2) SheetWrite.Failed else SheetWrite.Saved("1", copy.revision + 1)
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.updateDetails("먼저 저장한 초안", 60)
        model.saveDraft()
        runCurrent()
        model.updateDetails("응답이 유실된 초안", 60)
        model.saveDraft()
        runCurrent()
        assertEquals(SheetSaveStatus.DraftFailed, model.uiState.value.saveStatus)
        assertEquals("응답이 유실된 초안", stored?.title)

        model.undo()
        model.saveDraft()
        runCurrent()
        assertEquals(listOf(0, 1, 2), writes.map { it.revision })
        assertEquals(SheetSaveStatus.DraftSaved, model.uiState.value.saveStatus)
        val reopened = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        assertEquals("먼저 저장한 초안", reopened.uiState.value.draft.title)
    }

    @Test fun lostDraftAcknowledgementAcceptsServerCapturedSourceMetadata() = runTest {
        verifyMetadataAcknowledgement(requestHasSource = false)
    }

    @Test fun lostDraftAcknowledgementRejectsDifferentRequestedSource() = runTest {
        verifyMetadataAcknowledgement(requestHasSource = true)
    }

    private suspend fun TestScope.verifyMetadataAcknowledgement(requestHasSource: Boolean) {
        fun metadata(hash: String) = buildJsonObject {
            put("schema", "sheet-authoring/v1")
            put("source", buildJsonObject { put("scoreSha256", hash) })
        }
        val requested = metadata("a".repeat(64)).takeIf { requestHasSource }
        val captured = metadata("b".repeat(64))
        val writes = mutableListOf<SheetWorkingCopy>()
        var stored: SheetWorkingCopy? = null
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(stored).copy(authoring = requested)
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                writes += copy
                if (copy.revision != (stored?.revision ?: 0)) return SheetWrite.Conflict
                stored = copy.copy(revision = copy.revision + 1, authoring = captured)
                return if (writes.size == 2) SheetWrite.Failed else SheetWrite.Saved("1", copy.revision + 1)
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.updateDetails("첫 초안", 60)
        model.saveDraft(); runCurrent()
        model.updateDetails("응답 유실", 60)
        model.saveDraft(); runCurrent()
        model.undo()
        model.saveDraft(); runCurrent()
        assertEquals(requestHasSource, model.uiState.value.saveConflict)
        assertEquals(if (requestHasSource) listOf(0, 1) else listOf(0, 1, 2), writes.map { it.revision })
        if (!requestHasSource) assertEquals(SheetSaveStatus.DraftSaved, model.uiState.value.saveStatus)
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

    @Test fun draftSaveRestoresAndAdvancesServerRevisionAndKeepsBlankCells() = runTest {
        val writes = mutableListOf<SheetWorkingCopy>()
        var stored = SheetWorkingCopy("이어 쓰기", null, 55, smallScoreData(), "1", 3)
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(stored)
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                writes += copy
                stored = copy.copy(revision = copy.revision + 1)
                return SheetWrite.Saved(copy.baseScoreVersion, stored.revision)
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        assertEquals("이어 쓰기", model.uiState.value.draft.title)
        assertEquals(55, model.uiState.value.draft.bpm)
        model.selectCell(2); model.erase()
        model.saveDraft(); runCurrent()
        assertEquals(3, writes.single().revision)
        assertTrue(writes.single().score.columns[1].cells[0].units.isEmpty())
        assertEquals(SheetEditMessage.Draft, model.uiState.value.message)
        assertEquals(SheetSaveStatus.DraftSaved, model.uiState.value.saveStatus)
        model.saveDraft(); runCurrent()
        assertEquals(1, writes.size, "같은 편집 내용은 다시 전송하지 않는다")
        assertEquals(4, stored.revision)
        assertEquals(SheetEditMessage.Draft, model.uiState.value.message)

        val reopened = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        assertEquals("이어 쓰기", reopened.uiState.value.draft.title)
        assertEquals(55, reopened.uiState.value.draft.bpm)
        assertTrue(reopened.uiState.value.draft.cells[2].isEmpty())
        assertNull(reopened.uiState.value.saveStatus)
    }

    @Test fun draftSaveKeepsLatestEditPendingAndRetriesOnlyAfterAnError() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        val writes = mutableListOf<SheetWorkingCopy>()
        var stored: SheetWorkingCopy? = null
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(stored)
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                assertTrue(draft)
                writes += copy
                val result = when (writes.size) {
                    1 -> reply.await()
                    2 -> SheetWrite.Failed
                    else -> SheetWrite.Saved("1", 2)
                }
                if (result is SheetWrite.Saved) stored = copy.copy(revision = result.revision)
                return result
            }
        }
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(), documents = repository))
        runCurrent()
        model.updateDetails("첫 편집", 60)
        model.saveDraft()
        runCurrent()
        assertTrue(model.uiState.value.saving)
        model.updateDetails("다음 편집", 60)
        reply.complete(SheetWrite.Saved("1", 1))
        runCurrent()
        assertNull(model.uiState.value.saveStatus, "이전 요청의 성공을 최신 제목의 저장 성공으로 표시하지 않는다")

        model.saveDraft()
        runCurrent()
        assertEquals(SheetSaveStatus.DraftFailed, model.uiState.value.saveStatus)
        assertEquals("다음 편집", model.uiState.value.draft.title)
        model.retryDraftSave()
        model.retryDraftSave()
        runCurrent()
        assertEquals(listOf("첫 편집", "다음 편집", "다음 편집"), writes.map { it.title })
        assertEquals(listOf(0, 1, 1), writes.map { it.revision })
        assertEquals(SheetSaveStatus.DraftSaved, model.uiState.value.saveStatus)
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
            override suspend fun get(sheetId: String) = SheetDocument("31", "실제 악보", null, 60, "1", smallScoreData(), null, "NEEDS_REVIEW", "DONE", listOf(kotlinx.serialization.json.Json.parseToJsonElement("""{"issueId":"i1","cellId":"c0-j1","type":"LOW_CONFIDENCE"}""") as kotlinx.serialization.json.JsonObject))
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
        model.updateDetails("잘못된 빠르기", 0)
        model.updateDetails("잘못된 빠르기", -1)
        assertFalse(model.uiState.value.canUndo)

        val title = "가".repeat(49) + "🎵"
        model.updateDetails(" $title ", 90)
        assertEquals(title to 90, model.uiState.value.draft.run { this.title to bpm })
        model.undo()
        assertEquals("실제 악보" to 60, model.uiState.value.draft.run { this.title to bpm })
        model.redo()
        model.saveDraft(); runCurrent()
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

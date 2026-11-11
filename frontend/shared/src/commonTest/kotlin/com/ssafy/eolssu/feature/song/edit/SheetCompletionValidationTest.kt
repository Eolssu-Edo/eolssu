package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetCompletionValidationTest : CatalogViewModelTest() {
    @Test fun invalidPitchCannotBeConfirmedAndUndoRechecksTheCurrentScoreEvenAfterSaving() = runTest {
        val repository = RecordingDocuments(document(issues = listOf(issue("UNREGISTERED_PITCH"))))
        val model = open(repository, CompletionCatalog(registered = listOf(pitch("황", 0), pitch("태", 0))))
        runCurrent()
        model.openNextReviewCell()
        model.checkReviewIssue()
        assertTrue(model.uiState.value.checkedIssueIds.isEmpty())
        assertFalse(model.uiState.value.canCheckNextReviewIssue)
        model.requestSave(); runCurrent()
        assertTrue(repository.writes.isEmpty())
        model.saveDraft(); runCurrent()
        assertTrue(repository.writes.single().first, "잘못된 음도 편집 중인 초안에는 보존한다")

        model.selectUnit(0, 1); model.putYul(Yul.Taeju)
        model.checkReviewIssue()
        assertTrue(model.uiState.value.completionValidation.canComplete)
        model.undo()
        assertFalse(model.uiState.value.completionValidation.canComplete)
        assertTrue(model.uiState.value.checkedIssueIds.isEmpty())
        model.redo()
        assertEquals(1, model.uiState.value.pendingChecks, "고친 칸을 다시 확인해야 한다")
        model.checkReviewIssue()
        model.requestSave(); runCurrent()
        assertEquals(listOf("i1"), repository.writes.last().second.resolvedIssueIds)
        assertFalse(repository.writes.last().first)
        assertTrue(model.uiState.value.reviewIssues.isEmpty())

        model.undo()
        assertTrue(model.uiState.value.completionValidation.invalidPitchCount > 0)
        val writes = repository.writes.size
        model.requestSave(); runCurrent()
        assertEquals(writes, repository.writes.size, "서버에서 확인 항목을 지웠어도 되살아난 실제 오류를 막는다")
    }

    @Test fun outOfRangeUsesTheScoresInstrumentIdRatherThanTheGlobalPitchCatalog() = runTest {
        val catalog = CompletionCatalog(instrument = listOf(pitch("황", 0), pitch("태", 0)))
        val repository = RecordingDocuments(document(issues = listOf(issue("OUT_OF_RANGE"))))
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog,
            loadAudioInstruments = { listOf(CatalogInstrument("22", "DAEGEUM", "대금"), CatalogInstrument("11", "DANSO", "단소")) }))
        runCurrent()
        assertEquals(listOf("11"), catalog.instrumentReads)
        assertTrue(("중" to 1) in model.uiState.value.availablePitches.orEmpty())
        model.openNextReviewCell(); model.checkReviewIssue(); model.requestSave(); runCurrent()
        assertTrue(model.uiState.value.checkedIssueIds.isEmpty())
        assertTrue(repository.writes.isEmpty())
        model.selectUnit(0, 1); model.putYul(Yul.Taeju); model.checkReviewIssue()
        model.requestSave(); runCurrent()
        assertFalse(repository.writes.single().first)
    }

    @Test fun unavailableRangeMetadataKeepsConcreteIssuePendingButAllowsManualReviewKinds() = runTest {
        val range = open(RecordingDocuments(document(issues = listOf(issue("OUT_OF_RANGE")))))
        runCurrent()
        range.openNextReviewCell(); range.checkReviewIssue()
        assertFalse(range.uiState.value.completionValidation.canComplete)
        assertTrue(range.uiState.value.checkedIssueIds.isEmpty())
        for (kind in listOf("LOW_CONFIDENCE", "ORNAMENT_DROPPED")) {
            val repository = RecordingDocuments(document(issues = listOf(issue(kind))))
            val model = open(repository)
            runCurrent()
            model.requestSave(); runCurrent()
            assertTrue(repository.writes.isEmpty())
            model.openNextReviewCell(); model.checkReviewIssue()
            assertTrue(model.uiState.value.completionValidation.canComplete)
            model.selectUnit(0, 1); model.putYul(Yul.Taeju)
            assertFalse(model.uiState.value.completionValidation.canComplete, "재편집은 이전 확인을 무효화한다")
        }
    }

    @Test fun jangdanMismatchBlocksFinalSaveAndExplicitClearPreservesTimingThroughUndoAndReload() = runTest {
        val original = document(jangdanId = "3", notices = listOf(notice("JANGDAN_MISMATCH")))
        val repository = RecordingDocuments(original)
        val model = open(repository)
        runCurrent()
        assertTrue(model.uiState.value.completionValidation.hasJangdanMismatch)
        model.requestSave(); runCurrent()
        assertTrue(repository.writes.isEmpty())
        model.saveDraft(); runCurrent()
        assertTrue(repository.writes.single().first)

        model.clearJangdan()
        val cleared = model.uiState.value.draft.toCommonScore()
        assertNull(model.uiState.value.draft.jangdanId)
        assertEquals("", model.uiState.value.draft.jangdanName)
        assertFalse(model.uiState.value.withJangdan)
        assertEquals(original.score?.columns, cleared.columns)
        assertEquals(original.score?.events, cleared.events)
        assertEquals(original.score?.music?.copy(jangdan = ""), cleared.music)
        assertTrue(model.uiState.value.completionValidation.canComplete)
        model.undo()
        assertEquals("3", model.uiState.value.draft.jangdanId)
        assertTrue(model.uiState.value.withJangdan)
        assertTrue(model.uiState.value.completionValidation.hasJangdanMismatch)
        model.redo()
        assertFalse(model.uiState.value.withJangdan)
        model.saveDraft(); runCurrent()
        val restored = open(repository)
        runCurrent()
        assertFalse(restored.uiState.value.completionValidation.hasJangdanMismatch)
        assertEquals("", restored.uiState.value.draft.jangdanName)
        restored.requestSave(); runCurrent()
        assertFalse(repository.writes.last().first)
        val confirmed = open(repository)
        runCurrent()
        assertNull(confirmed.uiState.value.draft.jangdanId)
        assertTrue(confirmed.uiState.value.completionValidation.canComplete)
        assertEquals(original.score?.events, confirmed.uiState.value.draft.toCommonScore().events)
    }

    @Test fun compatibleJangdanUsesDottedQuarterPatternLengthAndAllowsAShortLastColumn() {
        val draft = smallScoreData().editingDraft("검사", 60, null).copy(jangdanId = "3")
        val compatible = CatalogJangdanSummary("3", "검사 장단", 60, "DOTTED_QUARTER", 6, 2)
        val state = SheetEditUiState(EditSource.Mine("31"), draft,
            availableJangdans = listOf(compatible), reviewNotices = listOf(SheetReviewNotice("JANGDAN_MISMATCH", null, null, null, null)))
        assertTrue(state.completionValidation.canComplete, "2박 각과 마지막 1박 각은 6개 패턴의 점4분음표 장단과 맞는다")
        assertFalse(draft.isCompatibleWith(compatible.copy(beatCount = 12)))
        val unsupportedRatio = draft.copy(original = draft.original?.let { it.copy(music = it.music.copy(ticksPerBeat = 6)) })
        assertFalse(unsupportedRatio.isCompatibleWith(compatible.copy(beatCount = 4)), "각 길이만 맞아도 정간 2/3박은 허용하지 않는다")
        assertFalse(state.copy(availableJangdans = null).completionValidation.canComplete)
    }

    @Test fun informationalConversionNoticesDoNotBlockCompletion() = runTest {
        val repository = RecordingDocuments(document(notices = listOf("TRANSPOSED", "JANGDAN_GUESSED", "UNREADABLE").map(::notice)))
        val model = open(repository)
        runCurrent()
        assertTrue(model.uiState.value.completionValidation.canComplete)
        model.requestSave(); runCurrent()
        assertFalse(repository.writes.single().first)
    }

    @Test fun incompleteHoldBlocksFinalSaveButRemainsDraftSaveable() = runTest {
        val repository = RecordingDocuments(document())
        val model = open(repository)
        runCurrent()
        model.selectCell(0); model.put(ScoreNote.Hold)
        assertTrue(model.uiState.value.completionValidation.hasInvalidStructure)
        model.requestSave(); runCurrent()
        assertTrue(repository.writes.isEmpty())
        model.saveDraft(); runCurrent()
        assertTrue(repository.writes.single().first)
    }

    @Test fun finalSaveRevalidatesEditsMadeWhileWaitingForAnEarlierDraftWrite() = runTest {
        val draftReply = CompletableDeferred<SheetWrite>()
        val calls = mutableListOf<Boolean>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document()
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                calls += draft
                return if (draft) draftReply.await() else SheetWrite.Saved("2")
            }
        }
        val model = open(repository)
        runCurrent()
        model.saveDraft(); runCurrent()
        assertTrue(model.uiState.value.saving)
        assertFalse(model.uiState.value.confirming)
        model.requestSave(); runCurrent()
        model.selectCell(0); model.put(ScoreNote.Hold)
        draftReply.complete(SheetWrite.Saved("1", 1)); runCurrent()
        assertEquals(listOf(true), calls)
        assertTrue(model.uiState.value.completionWarning)
    }

    @Test fun finalNetworkSaveFreezesDraftAndReviewChangesUntilTheResponse() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        val writes = mutableListOf<Pair<Boolean, SheetWorkingCopy>>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(issues = listOf(issue("LOW_CONFIDENCE")))
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
                writes += draft to copy
                if (draft) {
                    assertTrue(reply.isCompleted, "최종 저장 응답 전에는 새 편집의 자동 저장이 없어야 한다")
                    return SheetWrite.Saved(copy.baseScoreVersion, copy.revision + 1)
                }
                return reply.await()
            }
        }
        val model = open(repository)
        runCurrent()
        model.openNextReviewCell(); model.checkReviewIssue()
        val snapshot = model.uiState.value.draft
        model.requestSave(); runCurrent()
        assertTrue(model.uiState.value.confirming)
        model.selectCell(0); model.erase()
        model.updateDetails("저장 중 수정", 90)
        model.clearJangdan()
        model.undo()
        model.checkReviewIssue()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(snapshot, model.uiState.value.draft)
        assertEquals(setOf("i1"), model.uiState.value.checkedIssueIds)
        assertEquals(listOf(false), writes.map { it.first }, "최종 저장 중에는 최초 완료 요청 하나만 보낸다")
        assertEquals(snapshot.toCommonScore(), writes.single().second.score)
        reply.complete(SheetWrite.Saved("2", reviewStatus = "EVALUABLE")); runCurrent()
        assertFalse(model.uiState.value.confirming)
        assertTrue(model.uiState.value.reviewIssues.isEmpty())
        model.erase()
        assertNotEquals(snapshot, model.uiState.value.draft, "응답 후 편집을 다시 허용한다")
        val edited = model.uiState.value.draft
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(false, true), writes.map { it.first }, "응답 후 새 편집은 정상적으로 자동 저장한다")
        assertEquals(edited.toCommonScore(), writes.last().second.score)
        assertEquals("2", writes.last().second.baseScoreVersion)
    }

    @Test fun failedFinalNetworkSaveReleasesTheEditLockAndKeepsChecks() = runTest {
        val reply = CompletableDeferred<SheetWrite>()
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = document(issues = listOf(issue("LOW_CONFIDENCE")))
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean) = reply.await()
        }
        val model = open(repository)
        runCurrent()
        model.openNextReviewCell(); model.checkReviewIssue(); model.requestSave(); runCurrent()
        assertTrue(model.uiState.value.confirming)
        reply.complete(SheetWrite.Failed); runCurrent()
        assertFalse(model.uiState.value.confirming)
        assertEquals(setOf("i1"), model.uiState.value.checkedIssueIds)
        model.erase()
        assertTrue(model.uiState.value.draft.unfilledCells.isNotEmpty())
    }

    @Test fun newEditsCannotCompleteWhileInstrumentRangeIsStillLoading() = runTest {
        val instruments = CompletableDeferred<List<CatalogInstrument>>()
        val repository = RecordingDocuments(document())
        val catalog = CompletionCatalog(
            registered = listOf(pitch("황", 0), pitch("중", 1), pitch("태", 0), pitch("임", 0)),
            instrument = listOf(pitch("황", 0), pitch("중", 1), pitch("태", 0)),
        )
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog,
            loadAudioInstruments = { instruments.await() }))
        runCurrent()
        assertTrue(model.uiState.value.completionValidation.catalogLoading)
        model.selectCell(2); model.put(ScoreNote.SourcePitch("임", 0))
        model.requestSave(); runCurrent()
        assertTrue(repository.writes.isEmpty())
        model.saveDraft(); runCurrent()
        assertTrue(repository.writes.single().first)
        instruments.complete(listOf(CatalogInstrument("11", "DANSO", "단소"))); runCurrent()
        assertEquals(SheetCompletionCatalogState.Ready, model.uiState.value.completionCatalogState)
        assertEquals(1, model.uiState.value.completionValidation.invalidPitchCount)
        model.requestSave(); runCurrent()
        assertEquals(1, repository.writes.size)
        model.putYul(Yul.Taeju)
        model.requestSave(); runCurrent()
        assertFalse(repository.writes.last().first)
    }

    @Test fun failedCatalogCanBeRetriedAndClearingJangdanDoesNotBypassRequiredPitchChecks() = runTest {
        var fail = true
        var rangeReads = 0
        val base = CompletionCatalog()
        val catalog = object : ReferenceCatalogRepository by base {
            override suspend fun pitches(instrumentId: String): List<CatalogPitch> {
                rangeReads++
                if (fail) error("catalog unavailable")
                return base.pitches(instrumentId)
            }
        }
        val repository = RecordingDocuments(document(jangdanId = "3", notices = listOf(notice("JANGDAN_MISMATCH"))))
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog,
            loadAudioInstruments = { listOf(CatalogInstrument("11", "DANSO", "단소")) }))
        runCurrent()
        assertTrue(model.uiState.value.completionValidation.catalogFailed)
        model.clearJangdan()
        assertFalse(model.uiState.value.completionValidation.hasJangdanMismatch)
        assertFalse(model.uiState.value.completionValidation.canComplete)
        model.requestSave(); runCurrent()
        assertTrue(repository.writes.isEmpty())
        fail = false
        model.retryCompletionCatalog()
        assertTrue(model.uiState.value.completionValidation.catalogLoading)
        runCurrent()
        assertEquals(2, rangeReads)
        assertTrue(model.uiState.value.completionValidation.canComplete)
        model.requestSave(); runCurrent()
        assertFalse(repository.writes.single().first)
    }

    @Test fun missingInstrumentMappingOrEmptyRangeIsAFailedCatalogRatherThanAnEmptyAllowedSet() = runTest {
        for (missingMapping in listOf(true, false)) {
            val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
                SampleMySheetRepository(), documents = RecordingDocuments(document()),
                pitchCatalog = CompletionCatalog(instrument = emptyList()),
                loadAudioInstruments = {
                    if (missingMapping) emptyList() else listOf(CatalogInstrument("11", "DANSO", "단소"))
                }))
            runCurrent()
            assertTrue(model.uiState.value.completionValidation.catalogFailed)
            assertFalse(model.uiState.value.completionValidation.canComplete)
        }
    }

    private fun open(repository: SheetDocumentRepository, catalog: ReferenceCatalogRepository? = null) =
        keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog))

    private fun document(jangdanId: String? = null, issues: List<JsonObject> = emptyList(), notices: List<JsonObject> = emptyList()) =
        SheetDocument("31", "검사 악보", jangdanId, 60, "1", smallScoreData(), null, "NEEDS_REVIEW", "DONE", issues, notices)

    private fun issue(kind: String) = json("""{"issueId":"i1","cellId":"c0-j0","type":"$kind"}""")
    private fun notice(kind: String) = json("""{"type":"$kind"}""")
    private fun json(value: String) = Json.parseToJsonElement(value) as JsonObject

    private class RecordingDocuments(var document: SheetDocument) : SheetDocumentRepository {
        val writes = mutableListOf<Pair<Boolean, SheetWorkingCopy>>()
        override suspend fun get(sheetId: String) = document
        override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
            writes += draft to copy
            return if (draft) {
                document = document.copy(draft = copy.copy(revision = copy.revision + 1))
                SheetWrite.Saved(copy.baseScoreVersion, copy.revision + 1)
            } else {
                val version = (copy.baseScoreVersion.toInt() + 1).toString()
                document = document.copy(scoreVersion = version, score = copy.score, jangdanId = copy.jangdanId, draft = null,
                    issues = document.issues.filterNot { it["issueId"]?.toString()?.trim('"') in copy.resolvedIssueIds })
                SheetWrite.Saved(version, reviewStatus = "EVALUABLE")
            }
        }
    }

    private class CompletionCatalog(
        private val registered: List<CatalogPitch> = listOf(pitch("황", 0), pitch("중", 1), pitch("태", 0)),
        private val instrument: List<CatalogPitch> = registered,
    ) : ReferenceCatalogRepository {
        val instrumentReads = mutableListOf<String>()
        override suspend fun allPitches() = registered
        override suspend fun pitches(instrumentId: String): List<CatalogPitch> { instrumentReads += instrumentId; return instrument }
        override suspend fun jangdans() = emptyList<CatalogJangdanSummary>()
        override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = error("unused")
        override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
        override suspend fun symbols(): List<CatalogNotationSymbol> = error("unused")
    }

    private companion object {
        fun pitch(yul: String, octave: Int) = CatalogPitch("$yul-$octave", yul, octave, yul, yul, 0)
    }
}

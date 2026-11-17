package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetAdaptationReadinessTest : CatalogViewModelTest() {
    @Test fun theFirstSelectionKeepsTheOriginalKeyEvenWhenAnotherTransposeIsRecommended() = runTest {
        val base = ReadinessCatalog()
        val catalog = object : ReferenceCatalogRepository by base {
            override suspend fun authoringCapabilities(instrumentId: String) = base.authoringCapabilities(instrumentId).let { capability ->
                // 원본 황/청중(MIDI63/80)에 +6에서 옥타브 이동 없이 모두 맞는 지원음이다.
                capability.copy(items = capability.items.filter { it.midi == 69 || it.midi == 86 })
            }
        }
        val model = open(catalog)
        runCurrent()

        val panel = assertNotNull(model.uiState.value.adaptation)
        assertEquals(6, panel.plans.first().transpose, "비영점 추천이 있는 입력이어야 한다")
        assertEquals(0, panel.selectedTranspose, "추천과 최초 선택은 별개다")
    }

    @Test fun reopeningTheSameScoreKeepsTheChosenTransposeWithoutLoadingAgain() = runTest {
        val catalog = ReadinessCatalog()
        val model = open(catalog)
        runCurrent()
        model.selectAdaptationTranspose(3)
        val requests = catalog.capabilityCalls

        model.selectTab(EditTab.Yul)
        model.selectTab(EditTab.Adaptation)

        assertFalse(assertNotNull(model.uiState.value.adaptation).loading)
        assertEquals(3, model.uiState.value.adaptation?.selectedTranspose)
        runCurrent()
        assertEquals(requests, catalog.capabilityCalls)
    }

    @Test fun selectingTheReadyInstrumentAgainDoesNotResetTheRecommendation() = runTest {
        val catalog = ReadinessCatalog()
        val model = open(catalog)
        runCurrent()
        model.selectAdaptationTranspose(-2)
        val requests = catalog.capabilityCalls

        model.selectAdaptationInstrument("1")

        assertFalse(assertNotNull(model.uiState.value.adaptation).loading)
        assertEquals(-2, model.uiState.value.adaptation?.selectedTranspose)
        runCurrent()
        assertEquals(requests, catalog.capabilityCalls)
    }

    @Test fun applyingKeepsThePitchToolAndSelectionReadyForAnotherAdjustment() = runTest {
        val model = open(ReadinessCatalog())
        runCurrent()
        val before = model.uiState.value.draft
        model.selectAdaptationTranspose(3)
        model.requestApplyAdaptation()
        model.applyAdaptation()

        assertEquals(EditTab.Adaptation, model.uiState.value.tab)
        val panel = assertNotNull(model.uiState.value.adaptation)
        assertFalse(panel.loading)
        assertFalse(panel.confirmationRequested)
        assertEquals(3, panel.selectedTranspose)
        assertEquals(model.uiState.value.draft, panel.baseline)
        val after = model.uiState.value.draft
        assertNotEquals(before, after)
        model.undo()
        assertEquals(before, model.uiState.value.draft)
        model.redo()
        assertEquals(after, model.uiState.value.draft)
    }

    @Test fun theAppliedHeightCannotBeAppliedAgainAndApplyingSaysWhatChanged() = runTest {
        val model = open(ReadinessCatalog())
        runCurrent()
        model.selectAdaptationTranspose(2)
        assertFalse(assertNotNull(model.uiState.value.adaptation).selectionApplied)
        model.requestApplyAdaptation()
        model.applyAdaptation()
        assertEquals(SheetEditMessage.AdaptationApplied, model.uiState.value.message)

        // 이미 적용한 높이는 다시 적용해도 바뀌지 않으므로 적용을 막는다(S15P31E102-1096).
        val panel = assertNotNull(model.uiState.value.adaptation)
        assertTrue(panel.selectionApplied)
        model.requestApplyAdaptation()
        assertFalse(assertNotNull(model.uiState.value.adaptation).confirmationRequested)
        model.selectAdaptationTranspose(3)
        assertFalse(assertNotNull(model.uiState.value.adaptation).selectionApplied)

        // 적용한 뒤 음을 직접 고치면 같은 높이로 다시 맞출 수 있다.
        model.selectAdaptationTranspose(2)
        model.selectTab(EditTab.Yul)
        model.put(ScoreNote.Rest)
        model.openAdaptation()
        assertFalse(assertNotNull(model.uiState.value.adaptation).selectionApplied)
    }

    @Test fun closingThePitchToolWithBackKeepsTheComputedHeights() = runTest {
        val catalog = ReadinessCatalog()
        val model = open(catalog)
        runCurrent()
        model.selectTab(EditTab.Adaptation)
        val requests = catalog.capabilityCalls

        model.closeAdaptation()

        assertEquals(EditTab.Yul, model.uiState.value.tab)
        val kept = assertNotNull(model.uiState.value.adaptation, "확인 안내가 악기 이름과 바로 고치기를 잃지 않는다")
        assertFalse(kept.loading)
        model.openAdaptation()
        assertEquals(EditTab.Adaptation, model.uiState.value.tab)
        runCurrent()
        assertEquals(requests, catalog.capabilityCalls)
    }

    @Test fun editingTheScoreRebuildsThePlanWithoutAnotherCatalogRequest() = runTest {
        val catalog = ReadinessCatalog()
        val model = open(catalog)
        runCurrent()
        val original = model.uiState.value.draft
        val requests = catalog.capabilityCalls
        model.selectTab(EditTab.Yul)
        model.put(ScoreNote.Rest)
        // 1042: 편집하면 받아 둔 음 목록으로 바로 다시 계산한다.
        assertEquals(model.uiState.value.draft, model.uiState.value.adaptation?.baseline)
        model.openAdaptation()
        val panel = assertNotNull(model.uiState.value.adaptation)
        assertFalse(panel.loading)
        assertNotEquals(original, panel.baseline)
        assertEquals(model.uiState.value.draft, panel.baseline)
        assertEquals(requests, catalog.capabilityCalls)
    }

    @Test fun aColdEditorPreparesItsFirstRecommendationBeforeShowingTheDocument() = runTest {
        val base = ReadinessCatalog()
        val reply = CompletableDeferred<Unit>()
        val references = object : ReferenceCatalogRepository by base {
            override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities {
                reply.await(); return base.authoringCapabilities(instrumentId)
            }
        }
        val prepared = SheetAuthoringCatalog({ listOf(CatalogInstrument("1", "DANSO", "단소")) }, references, backgroundScope)
        val model = open(base, prepared)
        runCurrent()
        assertTrue(model.uiState.value.documentLoading)
        assertNull(model.uiState.value.adaptation)
        reply.complete(Unit)
        runCurrent()
        assertFalse(model.uiState.value.documentLoading)
        assertFalse(assertNotNull(model.uiState.value.adaptation).loading)
    }

    @Test fun uploadPreparesEveryInstrumentAndTheEditorUsesItAfterTheUploadScreenCloses() = runTest {
        val base = ReadinessCatalog()
        val pitchReply = CompletableDeferred<Unit>()
        val instruments = listOf(CatalogInstrument("1", "DANSO", "단소"), CatalogInstrument("2", "SOGEUM", "소금"))
        var instrumentCalls = 0
        val calls = mutableListOf<String>()
        val references = object : ReferenceCatalogRepository by base {
            override suspend fun allPitches(): List<CatalogPitch> { pitchReply.await(); return base.pitches }
            override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities {
                calls += instrumentId
                return base.authoringCapabilities(instrumentId)
            }
        }
        val prepared = SheetAuthoringCatalog({ instrumentCalls++; instruments }, references, backgroundScope)
        val upload = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Default,
            object : SheetImportRepository {
                override suspend fun upload(input: SheetImportInput, key: String) = SheetImportResult.Accepted("31")
            }, { error("작성 준비 목록을 재사용해야 한다") }, { emptyList() }, authoringCatalog = prepared))
        runCurrent()
        upload.viewModelScope.cancel()
        pitchReply.complete(Unit)
        runCurrent()
        assertEquals(listOf("1", "2"), calls)

        val editor = open(base, prepared)
        runCurrent()
        assertFalse(editor.uiState.value.documentLoading)
        assertFalse(assertNotNull(editor.uiState.value.adaptation).loading)
        editor.selectAdaptationInstrument("2")
        assertEquals("2", editor.uiState.value.adaptation?.selectedId)
        assertFalse(assertNotNull(editor.uiState.value.adaptation).loading)
        assertEquals(1, instrumentCalls)
        assertEquals(listOf("1", "2"), calls, "편집 진입·저장 검증·악기 변경이 같은 응답을 사용한다")
    }

    @Test fun concurrentReadersShareTheReplyAndCancellingOneWaiterDoesNotCancelTheOther() = runTest {
        val base = ReadinessCatalog()
        val reply = CompletableDeferred<Unit>()
        var calls = 0
        val references = object : ReferenceCatalogRepository by base {
            override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities {
                calls++; reply.await(); return base.authoringCapabilities(instrumentId)
            }
        }
        val prepared = SheetAuthoringCatalog({ emptyList() }, references, backgroundScope)
        val first = async { prepared.references.authoringCapabilities("1") }
        val cancelled = async { prepared.references.authoringCapabilities("1") }
        val last = async { prepared.references.authoringCapabilities("1") }
        runCurrent()
        cancelled.cancel()
        reply.complete(Unit)
        runCurrent()
        assertEquals(first.await(), last.await())
        assertEquals(1, calls)
    }

    @Test fun aFailedInstrumentCanRetryWithoutInvalidatingAReadyInstrument() = runTest {
        val base = ReadinessCatalog()
        var unavailable = true
        val calls = mutableListOf<String>()
        val references = object : ReferenceCatalogRepository by base {
            override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities {
                calls += instrumentId
                if (instrumentId == "2" && unavailable) error("offline")
                return base.authoringCapabilities(instrumentId)
            }
        }
        val prepared = SheetAuthoringCatalog({ listOf(CatalogInstrument("1", "DANSO", "단소"), CatalogInstrument("2", "SOGEUM", "소금")) },
            references, backgroundScope)
        prepared.warmUp(); runCurrent()
        assertNotNull(prepared.cachedCapabilities("1"))
        assertNull(prepared.cachedCapabilities("2"))
        unavailable = false
        prepared.references.authoringCapabilities("2")
        prepared.references.authoringCapabilities("1")
        assertEquals(listOf("1", "2", "2"), calls)
    }

    @Test fun retryingThePanelKeepsAndRetriesTheInstrumentThatFailed() = runTest {
        val base = ReadinessCatalog()
        var unavailable = true
        val calls = mutableListOf<String>()
        val references = object : ReferenceCatalogRepository by base {
            override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities {
                calls += instrumentId
                if (instrumentId == "2" && unavailable) error("offline")
                return base.authoringCapabilities(instrumentId)
            }
        }
        val prepared = SheetAuthoringCatalog({ listOf(CatalogInstrument("1", "DANSO", "단소"), CatalogInstrument("2", "SOGEUM", "소금")) },
            references, backgroundScope)
        val model = open(base, prepared)
        runCurrent()
        model.selectAdaptationInstrument("2"); runCurrent()
        assertTrue(assertNotNull(model.uiState.value.adaptation).failed)
        assertEquals("2", model.uiState.value.adaptation?.selectedId)
        val requests = calls.size
        unavailable = false

        model.openAdaptation() // Route의 오류 화면 '다시 시도' 콜백.
        runCurrent()

        assertEquals("2", model.uiState.value.adaptation?.selectedId)
        assertFalse(assertNotNull(model.uiState.value.adaptation).failed)
        assertFalse(assertNotNull(model.uiState.value.adaptation).loading)
        assertEquals(requests + 1, calls.size)
        assertEquals("2", calls.last())
    }

    @Test fun aLateReplyFromThePreviousLoginCannotPopulateTheNewLogin() = runTest {
        val member = SignedInMember(FakeKeyValueStore().apply { putString("auth.member_id", "1") })
        val base = ReadinessCatalog()
        val reply = CompletableDeferred<Unit>()
        var calls = 0
        val references = object : ReferenceCatalogRepository by base {
            override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities {
                calls++
                if (calls == 1) withContext(NonCancellable) { reply.await() }
                return base.authoringCapabilities(instrumentId)
            }
        }
        val prepared = SheetAuthoringCatalog({ listOf(CatalogInstrument("1", "DANSO", "단소")) }, references, backgroundScope, member)
        prepared.warmUp(); runCurrent()
        member.set("1") // 같은 회원의 재로그인도 새 세대다.
        reply.complete(Unit); runCurrent()
        assertNull(prepared.cachedCapabilities("1"))
        prepared.references.authoringCapabilities("1")
        assertEquals(2, calls)
    }

    private fun open(catalog: ReferenceCatalogRepository, prepared: SheetAuthoringCatalog? = null): SheetEditViewModel {
        val score = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = SheetDocument("31", "완료 악보", null, 60, "1", score,
                null, "NEEDS_REVIEW", "DONE", emptyList())
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite = SheetWrite.Failed
        }
        return keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, pitchCatalog = catalog,
            loadAudioInstruments = { listOf(CatalogInstrument("1", "DANSO", "단소")) }, authoringCatalog = prepared))
    }
}

internal class ReadinessCatalog : ReferenceCatalogRepository {
    var capabilityCalls = 0
    val pitches = (-2..2).flatMap { octave -> "황대태협고중유임이남무응".mapIndexed { index, yul ->
        CatalogPitch("$yul-$octave", yul.toString(), octave, yul.toString(),
            ScoreNote.SourcePitch(yul.toString(), octave).hangul, index + 12 * octave)
    } }
    override suspend fun authoringCapabilities(instrumentId: String): CatalogAuthoringCapabilities {
        capabilityCalls++
        return CatalogAuthoringCapabilities(instrumentId, "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), pitches.map {
                AuthoringPitchCapability(it.pitchId, it.yul, it.octave, it.hanja, it.hangul, it.semitone,
                    63 + it.semitone, "UNKNOWN", fingering = "REGISTERED", notation = "SUPPORTED",
                    synthesis = "SUPPORTED", selectable = true, reasonCodes = emptyList())
            })
    }
    override suspend fun allPitches() = pitches
    override suspend fun pitches(instrumentId: String) = pitches
    override suspend fun jangdans() = emptyList<CatalogJangdanSummary>()
    override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = null
    override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
    override suspend fun symbols() = emptyList<CatalogNotationSymbol>()
}

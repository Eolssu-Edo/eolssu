package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetAdaptationReviewTest : CatalogViewModelTest() {
    @Test fun adaptationInvalidatesChangedCellsButKeepsUnchangedCellChecks() = runTest {
        val model = open()
        runCurrent()
        model.closeAdaptation()
        repeat(3) { model.openNextReviewCell(); model.checkReviewIssue() }
        assertEquals(setOf("changed", "tied", "rest"), model.uiState.value.checkedIssueIds)
        val before = model.uiState.value.draft

        model.openAdaptation(); runCurrent(); model.selectAdaptationTranspose(1)
        model.requestApplyAdaptation(); model.applyAdaptation(); runCurrent()

        assertEquals("대", model.uiState.value.draft.toCommonScore().events.first().yul)
        assertEquals(setOf("rest"), model.uiState.value.checkedIssueIds)
        assertEquals(2, model.uiState.value.completionValidation.pendingReviewCount)
        assertFalse(model.uiState.value.completionValidation.canComplete)
        model.undo()
        assertEquals(before, model.uiState.value.draft)
        assertEquals(setOf("rest"), model.uiState.value.checkedIssueIds, "되돌리기도 이전 확인을 되살리지 않는다")
        model.redo()
        assertEquals(setOf("rest"), model.uiState.value.checkedIssueIds)
    }

    @Test fun adaptationThatDoesNotChangeCellContentsKeepsItsChecks() = runTest {
        val model = open()
        runCurrent()
        model.closeAdaptation()
        repeat(3) { model.openNextReviewCell(); model.checkReviewIssue() }
        model.openAdaptation(); runCurrent(); model.selectAdaptationTranspose(0)
        model.requestApplyAdaptation(); model.applyAdaptation(); runCurrent()
        assertEquals(setOf("changed", "tied", "rest"), model.uiState.value.checkedIssueIds)
        assertTrue(model.uiState.value.completionValidation.canComplete)
    }

    @Test fun differentInstrumentTuningInvalidatesSoundingNotesEvenWithTheSameWrittenPitches() = runTest {
        val model = open()
        runCurrent(); model.closeAdaptation()
        repeat(3) { model.openNextReviewCell(); model.checkReviewIssue() }
        val before = model.uiState.value.draft
        model.openAdaptation(); runCurrent()
        model.selectAdaptationInstrument("2"); runCurrent()
        model.selectAdaptationTranspose(2)
        model.requestApplyAdaptation(); model.applyAdaptation(); runCurrent()

        assertEquals(before.cells, model.uiState.value.draft.cells, "기준음과 전조가 같게 이동하여 표기는 유지된다")
        assertEquals("DAEGEUM_SANJO", model.uiState.value.draft.original?.score?.instrument)
        assertEquals(setOf("rest"), model.uiState.value.checkedIssueIds, "실음이 달라진 시작 음과 이음 칸은 다시 확인한다")
        assertFalse(model.uiState.value.completionValidation.canComplete)
    }

    private fun open(): SheetEditViewModel {
        val yuls = "황대태협고중유임이남무응"
        val pitches = (-2..2).flatMap { octave -> yuls.mapIndexed { index, yul ->
            CatalogPitch("$yul-$octave", yul.toString(), octave, yul.toString(),
                ScoreNote.SourcePitch(yul.toString(), octave).hangul, index + 12 * octave)
        } }
        val capability = CatalogAuthoringCapabilities("1", "DANSO", "v1", 63,
            AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), pitches.map { pitch ->
                AuthoringPitchCapability(pitch.pitchId, pitch.yul, pitch.octave, pitch.hanja, pitch.hangul, pitch.semitone,
                    63 + pitch.semitone, "UNKNOWN", fingering = "REGISTERED", notation = "SUPPORTED",
                    synthesis = "SUPPORTED", selectable = true, reasonCodes = emptyList())
            })
        val catalog = object : ReferenceCatalogRepository {
            override suspend fun authoringCapabilities(instrumentId: String) = if (instrumentId == "1") capability else capability.copy(
                instrumentId = "2", instrumentCode = "DAEGEUM_SANJO", nominalHwangMidi = 65,
                items = capability.items.map { it.copy(midi = it.midi + 2) },
            )
            override suspend fun allPitches() = pitches
            override suspend fun pitches(instrumentId: String) = pitches
            override suspend fun jangdans() = emptyList<CatalogJangdanSummary>()
            override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = null
            override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
            override suspend fun symbols() = emptyList<CatalogNotationSymbol>()
        }
        val score = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val issues = listOf("changed" to "c0-j0", "tied" to "c0-j1", "rest" to "c1-j0").map { (id, cell) ->
            buildJsonObject { put("issueId", id); put("cellId", cell); put("type", "LOW_CONFIDENCE") }
        }
        val repository = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) =
                SheetDocument("31", "검토 악보", null, 60, "1", score, null, "NEEDS_REVIEW", "DONE", issues)
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite = SheetWrite.Failed
        }
        return keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo, SampleMySheetRepository(),
            documents = repository, pitchCatalog = catalog,
            loadAudioInstruments = { listOf(CatalogInstrument("1", "DANSO", "단소"), CatalogInstrument("2", "DAEGEUM_SANJO", "산조대금")) }))
    }
}

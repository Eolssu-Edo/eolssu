package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetJangdanViewModelTest : CatalogViewModelTest() {
    private val compatible = CatalogJangdanSummary("4", "굿거리", 80, "DOTTED_QUARTER", 3, 1, 1)
    private val incompatible = CatalogJangdanSummary("5", "중모리", 90, "QUARTER", 12, 4, 2)

    @Test fun basicPresetSurvivesDraftReloadAndConfirmationWithMelodyOnlyAudio() = runTest {
        val original = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val metadata = SheetJangdanCorrectionTest.sourceMetadata(original)
        val repository = RecordingDocuments(original).also { it.document = it.document.copy(authoring = metadata) }
        val catalog = JangdanCatalog(emptyList())
        val audio = RecordingAudio()
        fun open() = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog, sheetAudio = audio))
        val model = open()
        runCurrent()
        model.selectBasicAccompaniment(com.ssafy.eolssu.core.model.BasicAccompanimentPreset.FiveTwoThree, 1)
        model.saveDraft(); runCurrent()
        val savedDraft = repository.writes.single().second
        assertEquals(original.copy(score = original.score.copy(title = repository.document.title)), savedDraft.score)
        assertEquals(4L, savedDraft.authoring?.basicAccompaniment(original)?.startTick)
        val restored = open()
        runCurrent()
        assertEquals(model.uiState.value.draft.basicAccompaniment, restored.uiState.value.draft.basicAccompaniment)
        restored.requestSave(); runCurrent()
        assertFalse(repository.writes.last().first)
        assertNull(repository.document.jangdanId)
        val confirmed = open()
        runCurrent()
        assertEquals(listOf(2, 3), confirmed.uiState.value.draft.basicAccompaniment?.groups)
        confirmed.requestAudio(); runCurrent()
        assertEquals(SheetAudioSettings(withJangdan = false, bpm = 60), audio.requests.single())
    }

    @Test fun recognizedJangdanCorrectionSurvivesDraftReloadAndFinalSave() = runTest {
        val original = SheetJangdanCorrectionTest.recognized("QUARTER", 12, 36, "중모리")
        val metadata = SheetJangdanCorrectionTest.sourceMetadata(original)
        val gutgeori = CatalogJangdanSummary("2", "굿거리", 80, "DOTTED_QUARTER", 12, 4, 1)
        val danmori = CatalogJangdanSummary("7", "단모리", 120, "QUARTER", 8, 4, 1)
        val repository = RecordingDocuments(original, jangdanId = "3").also {
            it.document = it.document.copy(authoring = metadata, conversionStatus = "DONE")
        }
        val catalog = JangdanCatalog(listOf(gutgeori, danmori))
        fun open() = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog))
        val model = open()
        runCurrent()
        model.changeJangdan()
        runCurrent()
        model.updateSettings(100, gutgeori.jangdanId)
        assertEquals("2", model.uiState.value.draft.jangdanId)
        model.saveDraft()
        runCurrent()
        val copy = repository.writes.single().second
        assertEquals(original.columns, copy.score.columns)
        assertEquals(original.events, copy.score.events)
        assertEquals("DOTTED_QUARTER", copy.score.music.bpmUnit)
        assertEquals(copy.score.music.ticksPerJeonggan, copy.score.music.ticksPerBeat)
        assertEquals(metadata, copy.authoring)

        val restored = open()
        runCurrent()
        restored.changeJangdan()
        runCurrent()
        restored.selectJangdan(danmori.jangdanId)
        restored.requestSave()
        runCurrent()
        assertFalse(repository.writes.last().first)
        assertEquals("7", repository.document.jangdanId)
        assertEquals("QUARTER", repository.document.score!!.music.bpmUnit)
        assertEquals(original.columns, repository.document.score!!.columns)
        assertEquals(original.events, repository.document.score!!.events)
        assertEquals(metadata, repository.document.authoring)
        assertEquals(100, restored.uiState.value.draft.bpm)
    }

    @Test fun changingJangdanLoadsChoicesAndPreservesTimingThroughDraftAndFinalSave() = runTest {
        val original = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val repository = RecordingDocuments(original)
        val catalog = JangdanCatalog(listOf(compatible, incompatible))
        val audio = RecordingAudio()

        fun open() = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), sheetAudio = audio, documents = repository, pitchCatalog = catalog))

        val model = open()
        runCurrent()
        assertFalse(model.uiState.value.canIncludeJangdan)
        model.changeJangdan()
        runCurrent()
        assertEquals(1, catalog.reads)
        assertEquals(listOf(compatible, incompatible), assertIs<SheetEditJangdanPicker.Ready>(model.uiState.value.jangdanPicker).options)

        model.selectJangdan(incompatible.jangdanId)
        assertNull(model.uiState.value.draft.jangdanId)
        assertIs<SheetEditJangdanPicker.Ready>(model.uiState.value.jangdanPicker)
        model.selectJangdan(compatible.jangdanId)
        assertNull(model.uiState.value.jangdanPicker)
        assertEquals("굿거리", model.uiState.value.draft.jangdanName)
        assertTrue(model.uiState.value.withJangdan)
        model.requestAudio()
        runCurrent()
        assertEquals(SheetEditMessage.AudioNeedsSave, model.uiState.value.message)
        assertTrue(audio.requests.isEmpty())

        model.saveDraft()
        runCurrent()
        val autoSaved = repository.writes.single()
        assertTrue(autoSaved.first)
        val copy = autoSaved.second
        assertEquals("4", copy.jangdanId)
        assertEquals(60, copy.bpm)
        assertEquals(original.music.copy(jangdan = "굿거리"), copy.score.music)
        assertEquals(original.columns, copy.score.columns)
        assertEquals(original.events, copy.score.events)

        val restored = open()
        runCurrent()
        assertEquals("4", restored.uiState.value.draft.jangdanId)
        assertEquals("굿거리", restored.uiState.value.draft.jangdanName)
        restored.requestAudio()
        runCurrent()
        assertTrue(audio.requests.isEmpty(), "초안만 저장한 장단으로는 음원을 만들 수 없다")
        restored.requestSave()
        runCurrent()
        val saved = repository.writes.last()
        assertFalse(saved.first)
        assertEquals(copy.copy(revision = 1), saved.second)
        assertEquals("4", repository.document.jangdanId)
        assertNull(repository.document.draft)
        restored.requestAudio()
        runCurrent()
        assertEquals(SheetAudioSettings(withJangdan = true, bpm = 60), audio.requests.single())
    }

    @Test fun choosingTheCurrentJangdanDoesNotWriteAndUndoRestoresBothIdAndName() = runTest {
        val repository = RecordingDocuments(smallScoreData(), jangdanId = "3")
        val initial = CatalogJangdanSummary("3", "세마치", 80, "DOTTED_QUARTER", 3, 1, 1)
        val catalog = JangdanCatalog(listOf(initial, compatible))
        val model = keep(SheetEditViewModel(EditSource.Mine("31"), EditView.Jeongganbo,
            SampleMySheetRepository(), documents = repository, pitchCatalog = catalog))
        runCurrent()
        model.changeJangdan()
        runCurrent()
        model.selectJangdan(initial.jangdanId)
        advanceTimeBy(1_100)
        runCurrent()
        assertFalse(model.uiState.value.canUndo)
        assertTrue(repository.writes.isEmpty())

        model.changeJangdan()
        runCurrent()
        model.selectJangdan(compatible.jangdanId)
        model.undo()
        assertEquals("3", model.uiState.value.draft.jangdanId)
        assertEquals("세마치", model.uiState.value.draft.jangdanName)
        model.redo()
        assertEquals("4", model.uiState.value.draft.jangdanId)
        assertEquals("굿거리", model.uiState.value.draft.jangdanName)
    }

    private class RecordingDocuments(score: CommonScoreData, jangdanId: String? = null) : SheetDocumentRepository {
        var document = SheetDocument("31", "실제 악보", jangdanId, 60, "1", score, null, "EVALUABLE", null, emptyList())
        val writes = mutableListOf<Pair<Boolean, SheetWorkingCopy>>()

        override suspend fun get(sheetId: String): SheetDocument = document

        override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean): SheetWrite {
            writes += draft to copy
            if (draft) {
                val revision = copy.revision + 1
                document = document.copy(draft = copy.copy(revision = revision))
                return SheetWrite.Saved(copy.baseScoreVersion, revision)
            }
            val version = copy.baseScoreVersion.toInt() + 1
            document = document.copy(jangdanId = copy.jangdanId, scoreVersion = version.toString(),
                score = copy.score.copy(score = copy.score.score.copy(scoreVersion = version)), draft = null, authoring = copy.authoring)
            return SheetWrite.Saved(version.toString())
        }
    }

    private class RecordingAudio : SheetAudioRepository {
        val requests = mutableListOf<SheetAudioSettings>()
        override suspend fun request(sheetId: String, settings: SheetAudioSettings): SheetAudioRequest {
            requests += settings
            return SheetAudioRequest.Unavailable
        }
        override suspend fun status(sheetId: String) = SheetAudioLookup.None
    }

    private class JangdanCatalog(private val options: List<CatalogJangdanSummary>) : ReferenceCatalogRepository {
        var reads = 0
        override suspend fun allPitches() = listOf("황" to 0, "중" to 1).map { (yul, octave) ->
            CatalogPitch("$yul-$octave", yul, octave, yul, yul, 0)
        }
        override suspend fun pitches(instrumentId: String): List<CatalogPitch> = error("unused")
        override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
        override suspend fun jangdans(): List<CatalogJangdanSummary> {
            reads++
            return options
        }
        override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = error("unused")
        override suspend fun symbols(): List<CatalogNotationSymbol> = error("unused")
    }
}

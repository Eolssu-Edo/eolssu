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
    private val compatible = CatalogJangdanSummary("4", "굿거리", 80, "DOTTED_QUARTER", 4, 4)
    private val incompatible = CatalogJangdanSummary("5", "중모리", 90, "QUARTER", 12, 4)

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

        advanceTimeBy(1_000)
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
        val initial = CatalogJangdanSummary("3", "세마치", 80, "DOTTED_QUARTER", 3, 3)
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
                score = copy.score.copy(score = copy.score.score.copy(scoreVersion = version)), draft = null)
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
        override suspend fun allPitches() = emptyList<CatalogPitch>()
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

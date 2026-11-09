package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.CatalogFingerings
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.CatalogJangdanDetail
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.CatalogNotationSymbol
import com.ssafy.eolssu.data.song.CatalogPitch
import com.ssafy.eolssu.data.song.EditableSong
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.ReferenceCatalogRepository
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.data.song.SheetWorkingCopy
import com.ssafy.eolssu.data.song.SheetWrite
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class EditPickViewModelTest : CatalogViewModelTest() {
    private fun sheet(id: String) = MySheet(id, MySheetOrigin.Imported, "악보 $id", updatedAt = KoreanDateTime(2026, 11, 1, 10, 0))

    private val mySheets = object : MySheetRepository {
        override val sheets = MutableStateFlow(listOf(sheet("31"), sheet("32"), sheet("33")))
        override suspend fun delete(sheetId: String) = Unit
    }

    private fun document(id: String, code: String) = smallScoreData().let { score ->
        SheetDocument(id, "악보 $id", null, 60, "1", score.copy(score = score.score.copy(instrument = code)), null, "EVALUABLE", null, emptyList())
    }

    /** 31은 단소, 32는 소금, 33은 읽지 못하는 악보. */
    private val documents = object : SheetDocumentRepository {
        override suspend fun get(sheetId: String): SheetDocument? = when (sheetId) {
            "31" -> document(sheetId, "DANSO")
            "32" -> document(sheetId, "SOGEUM")
            else -> null
        }
        override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean) = SheetWrite.Failed
    }

    private val songs = object : SongRepository {
        val asked = mutableListOf<String?>()
        override suspend fun getInstruments() = listOf(CatalogInstrument("1", "DANSO", "단소"), CatalogInstrument("2", "SOGEUM", "소금"))
        override suspend fun getSongs(instrumentId: String?): List<SongSummary> {
            asked += instrumentId
            return listOf(SongSummary("10$instrumentId", "곡 $instrumentId", 1, "세마치", 60, true, instrumentId))
        }
        override suspend fun getSong(songId: String): com.ssafy.eolssu.core.model.Song? = null
    }

    @Test
    fun showsOnlyTheChosenInstrumentsSheetsAndSongs() = runTest {
        val model = keep(EditPickViewModel(mySheets, songs, documents))
        runCurrent()
        assertEquals(EditPickLoad.NeedsInstrument, model.uiState.value.load, "악기를 고르기 전에는 악보를 받지 않는다")
        assertTrue(songs.asked.isEmpty())

        model.selectInstrument(Instrument.Danso); runCurrent()
        val danso = model.uiState.value
        assertEquals(EditPickLoad.Ready, danso.load)
        assertEquals(listOf<String?>("1"), songs.asked.toList(), "제공 곡은 서버 악기 ID로 거른다")
        assertEquals(listOf("101"), danso.songs.map { it.id })
        assertEquals(listOf("31"), danso.sheets.map { it.id }, "내 악보는 공통 악보의 악기로 거른다")
        assertEquals(1, danso.uncheckedSheets, "읽지 못한 악보는 어느 악기에도 넣지 않는다")

        model.toggle(EditPickTarget.Sheet("31"))
        model.selectInstrument(Instrument.Sogeum); runCurrent()
        val sogeum = model.uiState.value
        assertNull(sogeum.picked)
        assertEquals(listOf("102"), sogeum.songs.map { it.id })
        assertEquals(listOf("32"), sogeum.sheets.map { it.id })
    }

    @Test
    fun songCopyOpensTheRealScoreAndAsksBeforeDroppingEdits() = runTest {
        val repository = object : SongRepository {
            override suspend fun getInstruments() = emptyList<CatalogInstrument>()
            override suspend fun getSongs(instrumentId: String?) = emptyList<SongSummary>()
            override suspend fun getSong(songId: String): com.ssafy.eolssu.core.model.Song? = null
            override suspend fun getEditableSong(songId: String) = EditableSong(SampleSongs.first { it.id == "arirang" }, smallScoreData())
        }
        val model = keep(SheetEditViewModel(EditSource.CopySong("7"), EditView.Jeongganbo, SampleMySheetRepository(), songs = repository))
        assertTrue(model.uiState.value.documentLoading, "원본을 받기 전에는 예시 악보를 보이지 않는다")
        runCurrent()
        val draft = model.uiState.value.draft
        assertEquals(smallScoreData(), draft.original, "고른 곡의 공통 악보를 그대로 연다")
        assertEquals("아리랑 (내 편곡)", draft.title)

        var closed = false
        model.selectCell(2); model.put(ScoreNote.Pitch(Yul.Taeju))
        model.closeEditor { closed = true }; runCurrent()
        assertFalse(closed, "저장할 수 없는 복사본을 고쳤으면 닫기 전에 묻는다")
        assertTrue(model.uiState.value.closeWarning)
        model.discardChangesAndClose { closed = true }
        assertTrue(closed)
    }

    @Test
    fun registeredPitchListLimitsYulKeys() = runTest {
        val catalog = object : ReferenceCatalogRepository {
            override suspend fun pitches(instrumentId: String) = error("악기별 목록은 쓰지 않는다")
            override suspend fun allPitches() = listOf(CatalogPitch("1", "태", 2, "㶃", "중청태", 26))
            override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings = error("unused")
            override suspend fun jangdans(): List<CatalogJangdanSummary> = error("unused")
            override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = error("unused")
            override suspend fun symbols(): List<CatalogNotationSymbol> = error("unused")
        }
        val model = keep(SheetEditViewModel(EditSource.CopySheet("x"), EditView.Jeongganbo, SampleMySheetRepository(), pitchCatalog = catalog))
        runCurrent()
        assertEquals(setOf("태" to 2), model.uiState.value.availablePitches)
        val before = model.uiState.value.draft
        model.putYul(Yul.Taeju, 1)
        assertEquals(before, model.uiState.value.draft, "등록되지 않은 청태는 적지 않는다")
        model.raiseRegister(); model.raiseRegister(); model.putYul(Yul.Taeju)
        assertEquals(ScoreNote.SourcePitch("태", 2), model.uiState.value.draft.cells[0].single())
    }
}

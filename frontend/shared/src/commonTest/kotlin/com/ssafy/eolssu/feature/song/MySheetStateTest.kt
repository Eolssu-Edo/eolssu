package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class MySheetStateTest : CatalogViewModelTest() {

    private fun sheet(id: String) = MySheet(id, MySheetOrigin.Imported, id, updatedAt = KoreanDateTime(2026, 10, 17, 19, 0))

    private fun sheet(id: String, code: String?) = sheet(id).copy(instrumentCode = code)

    @Test
    fun titleSearchKeepsEveryInstrumentAndConversionRowInOrder() {
        val state = MySongsUiState(query = " note ", practiceInstrument = Instrument.Danso).receiveSheets(listOf(
            sheet("first note").copy(instrumentCode = "DANSO", conversionStatus = "FAILED"),
            sheet("second note").copy(instrumentCode = "SOGEUM"),
            sheet("third note").copy(instrumentCode = "DANSO", conversionStatus = "QUEUED"),
            sheet("other").copy(instrumentCode = "DANSO"),
            sheet("legacy note"),
        ))

        assertEquals(listOf("first note", "second note", "third note", "legacy note"), state.visibleSheets.map { it.id })
        assertEquals(listOf("first note", "third note"), state.ownSheets.map { it.id }, "연습 악기 악보는 검색과 서버 순서를 함께 따른다")
    }

    @Test
    fun practiceInstrumentSheetsComeFirstAndOthersAreGroupedInInstrumentOrder() {
        val state = MySongsUiState(practiceInstrument = Instrument.Sogeum).receiveSheets(listOf(
            sheet("a", "DANSO"), sheet("b", "SOGEUM"), sheet("c", "JEONGAK_DAEGEUM"),
            sheet("d", null), sheet("e", "PIRI"), sheet("f", "DANSO"),
        ))

        assertEquals(listOf("b"), state.ownSheets.map { it.id })
        assertEquals(
            listOf(Instrument.Danso to listOf("a", "f"), Instrument.DaegeumJeongak to listOf("c"), null to listOf("d", "e")),
            state.otherSheetGroups.map { it.instrument to it.sheets.map { sheet -> sheet.id } },
            "단소 · 소금 · 산조대금 · 정악대금 순서로 묶고, 모르는 악기와 악기 없는 악보는 맨 뒤 한 묶음에 모은다",
        )
        assertEquals(5, state.otherSheetCount)
        assertFalse(state.otherSheetsShown, "연습 악기 악보가 있으면 다른 악기 묶음은 접어 둔다")
        assertTrue(state.copy(otherSheetsOpen = true).otherSheetsShown)
        assertEquals(6, state.sheetCount, "탭의 악보 개수는 악기와 관계없이 전체 개수다")
    }

    @Test
    fun otherInstrumentGroupsShowWithoutOpeningWhenNoPracticeInstrumentSheetIsVisible() {
        val sheets = listOf(sheet("도라지", "DANSO"), sheet("아리랑", "SOGEUM"))

        val noneOfMine = MySongsUiState(practiceInstrument = Instrument.DaegeumSanjo).receiveSheets(sheets)
        assertTrue(noneOfMine.ownSheets.isEmpty())
        assertFalse(noneOfMine.hasOwnSheets, "'아직 없어요' 안내를 쓴다")
        assertTrue(noneOfMine.otherSheetsShown)

        val searchedAway = MySongsUiState(practiceInstrument = Instrument.Sogeum, query = "도라지").receiveSheets(sheets)
        assertTrue(searchedAway.ownSheets.isEmpty())
        assertTrue(searchedAway.hasOwnSheets, "검색에 걸러졌을 뿐이라 '찾는 곡이 없어요' 안내를 쓴다")
        assertTrue(searchedAway.otherSheetsShown)
        assertEquals(listOf("도라지"), searchedAway.otherSheetGroups.flatMap { it.sheets }.map { it.id })
    }

    @Test
    fun unknownPracticeInstrumentKeepsOneUndividedList() {
        val state = MySongsUiState().receiveSheets(listOf(sheet("a", "DANSO"), sheet("b", "SOGEUM")))
        assertEquals(listOf("a", "b"), state.ownSheets.map { it.id })
        assertTrue(state.otherSheetGroups.isEmpty())
        assertFalse(state.otherSheetsShown)
    }

    @Test
    fun changingPracticeInstrumentRegroupsSheetsAndFoldsOtherInstruments() = runTest {
        val instruments = InstrumentRepository(FakeKeyValueStore())
        val model = keep(MySongsViewModel(CatalogSongs(), SampleMySheetRepository(), instruments, SampleUserRepository()))
        advanceUntilIdle()
        assertEquals(Instrument.Danso, model.uiState.value.practiceInstrument)

        model.toggleOtherSheets()
        assertTrue(model.uiState.value.otherSheetsOpen)

        instruments.select(Instrument.Sogeum)
        advanceUntilIdle()
        assertEquals(Instrument.Sogeum, model.uiState.value.practiceInstrument)
        assertFalse(model.uiState.value.otherSheetsOpen, "악기를 바꾸면 다른 악기 묶음을 다시 접는다")
    }

    @Test
    fun startsOnProvidedSongsWithReceivedSheets() {
        val state = MySongsUiState().receiveSheets(listOf(sheet("a"), sheet("b")))
        assertEquals(MySongsTab.Songs, state.tab)
        assertEquals(2, state.sheetCount)
        assertNull(state.deleting)
    }

    @Test
    fun deleteDialogClosesWhenAskedSheetIsGone() {
        val asking = MySongsUiState(deleting = sheet("a")).receiveSheets(listOf(sheet("a"), sheet("b")))
        assertEquals("a", asking.deleting?.id, "목록에 남아 있으면 창을 그대로 둔다")

        val deleted = asking.receiveSheets(listOf(sheet("b")))
        assertNull(deleted.deleting)
        assertEquals(1, deleted.sheetCount)
    }

    @Test
    fun deleteAsksFirstThenRemovesSheetFromList() = runTest {
        val repository = SampleMySheetRepository()
        val model = keep(MySongsViewModel(CatalogSongs(), repository, InstrumentRepository(FakeKeyValueStore()).apply { clear() }, SampleUserRepository()))
        advanceUntilIdle()
        val arirang = model.uiState.value.sheets.orEmpty().first()

        model.askDelete(arirang)
        model.cancelDelete()
        advanceUntilIdle()
        assertNull(model.uiState.value.deleting)
        assertEquals(2, model.uiState.value.sheetCount, "취소하면 지우지 않는다")

        model.askDelete(arirang)
        assertEquals(arirang, model.uiState.value.deleting)
        model.confirmDelete()
        advanceUntilIdle()
        assertNull(model.uiState.value.deleting)
        assertEquals(listOf("상령산"), model.uiState.value.sheets?.map { it.title })
    }
}

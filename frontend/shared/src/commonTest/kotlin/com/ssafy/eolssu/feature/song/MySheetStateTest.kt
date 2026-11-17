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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class MySheetStateTest : CatalogViewModelTest() {

    private fun sheet(id: String) = MySheet(id, MySheetOrigin.Imported, id, updatedAt = KoreanDateTime(2026, 10, 17, 19, 0))

    @Test
    fun instrumentAndTitleFilterRetainsMatchingConversionRowsInOrder() {
        val state = MySongsUiState(query = " note ").receiveSheets(listOf(
            sheet("first note").copy(instrumentCode = "DANSO", conversionStatus = "FAILED"),
            sheet("second note").copy(instrumentCode = "SOGEUM"),
            sheet("third note").copy(instrumentCode = "DANSO", conversionStatus = "QUEUED"),
            sheet("other").copy(instrumentCode = "DANSO"),
            sheet("legacy note"),
        ))

        assertEquals(listOf("first note", "third note"),
            state.copy(sheetInstrument = Instrument.Danso).visibleSheets.map { it.id })
        assertEquals(listOf("first note", "second note", "third note", "legacy note"), state.visibleSheets.map { it.id })
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

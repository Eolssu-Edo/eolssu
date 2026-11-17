package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionLookup
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionRetry
import com.ssafy.eolssu.data.song.SheetConversionStage
import com.ssafy.eolssu.data.song.SheetConversionStatus
import com.ssafy.eolssu.feature.song.SongLoadState
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class EditNewViewModelTest : CatalogViewModelTest() {
    private fun sheet(id: String, conversionStatus: String? = null) =
        MySheet(id, MySheetOrigin.Imported, "악보 $id", updatedAt = KoreanDateTime(2026, 11, 13, 9, 0), conversionStatus = conversionStatus)

    private class Sheets(initial: List<MySheet>) : MySheetRepository {
        override val sheets = MutableStateFlow(initial)
        var refreshes = 0
        var next: List<MySheet>? = null
        override suspend fun refresh(): Boolean { refreshes++; next?.let { sheets.value = it }; return true }
        override suspend fun delete(sheetId: String) { sheets.value = sheets.value.filterNot { it.id == sheetId } }
    }

    private class Conversions : SheetConversionRepository {
        val asked = mutableListOf<String>()
        var answers = ArrayDeque<SheetConversionLookup>()
        override suspend fun status(sheetId: String): SheetConversionLookup {
            asked += sheetId
            return answers.removeFirstOrNull() ?: SheetConversionLookup.Unavailable
        }
        override suspend fun retry(sheetId: String): SheetConversionRetry = SheetConversionRetry.Unavailable
    }

    private fun running(processed: Int) =
        SheetConversionLookup.Found(SheetConversion("91", SheetConversionStatus.Running, SheetConversionStage.Recognizing, processed, 3, pollAfterMs = 2_000))

    private fun TestScope.model(sheets: Sheets, conversions: Conversions = Conversions()) =
        keep(EditNewViewModel(sheets, conversions)).also { runCurrent() }

    @Test fun showsMySheetsAndUpdatesConversionProgressOnlyWhileVisible() = runTest {
        val sheets = Sheets(listOf(sheet("91", "RUNNING"), sheet("92", "DONE")))
        val conversions = Conversions().apply {
            answers = ArrayDeque(listOf(running(1), running(2), SheetConversionLookup.Found(SheetConversion("91", SheetConversionStatus.Done))))
        }
        val model = model(sheets, conversions)
        try {
            model.refreshSheets(); model.watchConversions(); runCurrent()
            val state = model.uiState.value
            assertEquals(SongLoadState.Ready, state.sheetLoad)
            assertEquals(listOf("91", "92"), state.mine.sheets?.map { it.id })
            assertEquals(1, state.mine.conversions["91"]?.processedPageCount, "보이는 동안 변환 중인 악보를 묻는다")

            advanceTimeBy(2_000); runCurrent()
            assertEquals(2, model.uiState.value.mine.conversions["91"]?.processedPageCount)

            model.stopWatchingConversions()
            advanceTimeBy(10_000); runCurrent()
            assertEquals(2, conversions.asked.size, "가려진 동안 묻지 않는다")

            sheets.next = listOf(sheet("91", "DONE"), sheet("92", "DONE"))
            model.watchConversions(); runCurrent()
            assertEquals(2, sheets.refreshes, "처음 한 번 받고, 변환이 끝나면 목록을 다시 받는다")
            assertTrue(model.uiState.value.mine.convertingIds.isEmpty())
        } finally {
            model.stopWatchingConversions()
        }
    }

    @Test fun deleteAsksFirstThenRemovesTheSheet() = runTest {
        val sheets = Sheets(listOf(sheet("91"), sheet("92")))
        val model = model(sheets)
        model.askDelete(sheet("91"))
        assertEquals("91", model.uiState.value.deleting?.id)
        model.cancelDelete()
        assertNull(model.uiState.value.deleting)
        assertEquals(2, sheets.sheets.value.size)

        model.askDelete(sheet("91")); model.confirmDelete(); runCurrent()
        assertEquals(listOf("92"), model.uiState.value.mine.sheets?.map { it.id })
        assertNull(model.uiState.value.deleting)
    }
}

package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionFailure
import com.ssafy.eolssu.data.song.SheetConversionLookup
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionRetry
import com.ssafy.eolssu.data.song.SheetConversionStage
import com.ssafy.eolssu.data.song.SheetConversionStatus
import com.ssafy.eolssu.data.user.InstrumentRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class MySheetConversionTest : CatalogViewModelTest() {

    private fun sheet(id: String, conversionStatus: String? = null) =
        MySheet(id, MySheetOrigin.Imported, "악보 $id", updatedAt = KoreanDateTime(2026, 11, 9, 9, 0), conversionStatus = conversionStatus)

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
        var retryAnswer: SheetConversionRetry = SheetConversionRetry.Unavailable
        var retries = 0
        override suspend fun status(sheetId: String): SheetConversionLookup { asked += sheetId; return answers.removeFirstOrNull() ?: SheetConversionLookup.Unavailable }
        override suspend fun retry(sheetId: String): SheetConversionRetry { retries++; return retryAnswer }
    }

    private val watched = mutableListOf<MySongsViewModel>()

    /**
     * 변환 중인 악보가 남으면 상태 조회가 끝없이 이어진다. runTest는 남은 작업을 모두 돌린 뒤에 끝나므로,
     * 단언이 실패해도 테스트가 멈추지 않게 끝에서 반드시 조회를 멈춘다.
     */
    private fun conversionTest(body: suspend TestScope.() -> Unit) = runTest {
        try { body() } finally { watched.forEach { it.stopWatchingConversions() } }
    }

    private fun TestScope.model(sheets: Sheets, conversions: Conversions) = keep(
        MySongsViewModel(CatalogSongs(), sheets, InstrumentRepository(FakeKeyValueStore()).apply { clear() }, SampleUserRepository(), conversionRepository = conversions),
    ).also { watched += it; runCurrent() }

    private fun running(processed: Int, poll: Long = 2_000) =
        SheetConversionLookup.Found(SheetConversion("91", SheetConversionStatus.Running, SheetConversionStage.Recognizing, processed, 3, pollAfterMs = poll))

    @Test
    fun listStatusAloneShowsConvertingAndBlocksNothingElse() {
        val state = MySongsUiState().receiveSheets(listOf(sheet("91", "QUEUED"), sheet("92", "DONE"), sheet("93")))
        assertEquals(listOf("91"), state.convertingIds)
        assertEquals(SheetConversionStatus.Done, state.conversionOf(sheet("92", "DONE"))?.status)
        assertNull(state.conversionOf(sheet("93")), "불러오기로 만들지 않은 악보는 변환 상태가 없다")
    }

    @Test
    fun pollsOnlyWhileWatchingAtServerIntervalAndRefreshesListWhenDone() = conversionTest {
        val sheets = Sheets(listOf(sheet("91", "QUEUED"), sheet("92", "DONE")))
        val conversions = Conversions().apply {
            answers = ArrayDeque(listOf(running(1, poll = 3_000), running(2, poll = 3_000), SheetConversionLookup.Found(SheetConversion("91", SheetConversionStatus.Done, issueCount = 2))))
        }
        val model = model(sheets, conversions)
        assertTrue(conversions.asked.isEmpty(), "화면이 보이기 전에는 묻지 않는다")

        model.watchConversions()
        runCurrent()
        assertEquals(listOf("91"), conversions.asked, "변환 중인 악보만 묻는다")
        assertEquals(1, model.uiState.value.conversions["91"]?.processedPageCount)

        advanceTimeBy(2_999); runCurrent()
        assertEquals(1, conversions.asked.size, "pollAfterMs 전에는 다시 묻지 않는다")
        advanceTimeBy(1); runCurrent()
        assertEquals(2, conversions.asked.size)
        assertEquals(2, model.uiState.value.conversions["91"]?.processedPageCount)

        sheets.next = listOf(sheet("91", "DONE"), sheet("92", "DONE"))
        advanceTimeBy(3_000); runCurrent()
        assertEquals(1, sheets.refreshes, "끝나면 목록을 다시 받는다")
        assertEquals(2, model.uiState.value.conversionOf(sheet("91", "DONE"))?.issueCount)
        assertTrue(model.uiState.value.convertingIds.isEmpty())

        advanceTimeBy(60_000); runCurrent()
        assertEquals(3, conversions.asked.size, "변환 중인 악보가 없으면 더 묻지 않는다")
    }

    @Test
    fun stopsAskingWhenScreenIsHiddenAndResumesWhenShownAgain() = conversionTest {
        val sheets = Sheets(listOf(sheet("91", "RUNNING")))
        val conversions = Conversions().apply { answers = ArrayDeque(List(5) { running(1) }) }
        val model = model(sheets, conversions)
        model.watchConversions(); runCurrent()
        assertEquals(1, conversions.asked.size)

        model.stopWatchingConversions()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, conversions.asked.size, "가려진 동안 묻지 않는다")

        model.watchConversions(); runCurrent()
        assertEquals(2, conversions.asked.size)
    }

    @Test
    fun unavailableAnswerKeepsListStatusAndTriesAgainLater() = conversionTest {
        val sheets = Sheets(listOf(sheet("91", "QUEUED")))
        val conversions = Conversions()
        val model = model(sheets, conversions)
        model.watchConversions(); runCurrent()
        assertEquals(SheetConversionStatus.Queued, model.uiState.value.conversionOf(sheets.sheets.value.first())?.status)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, conversions.asked.size)
        assertEquals(0, sheets.refreshes)
    }

    @Test
    fun retrySendsOnlyRetryableFailuresAndConflictAsksToReimport() = conversionTest {
        val failed = sheet("91", "FAILED")
        val sheets = Sheets(listOf(failed))
        val conversions = Conversions().apply { retryAnswer = SheetConversionRetry.NotRetryable }
        val model = model(sheets, conversions)

        model.retryConversion(failed); runCurrent()
        assertEquals(0, conversions.retries, "서버가 다시 시도할 수 있다고 하지 않은 악보는 보내지 않는다")

        conversions.answers = ArrayDeque(listOf(SheetConversionLookup.Found(SheetConversion("91", SheetConversionStatus.Failed, failure = SheetConversionFailure.Timeout, retryable = true))))
        sheets.sheets.value = listOf(sheet("91", "RUNNING")); runCurrent()
        model.watchConversions(); runCurrent()
        sheets.sheets.value = listOf(failed); runCurrent()
        assertTrue(model.uiState.value.conversionOf(failed)?.retryable == true)

        model.retryConversion(failed); runCurrent()
        assertEquals(1, conversions.retries)
        assertEquals(ConversionNotice.Reimport, model.uiState.value.conversionNotice)
        assertFalse(model.uiState.value.conversionOf(failed)?.retryable == true, "409 뒤에는 '다시 불러오기'로 바뀐다")
        assertTrue(model.uiState.value.retrying.isEmpty())
    }

    @Test
    fun acceptedRetryStartsWatchingAgain() = conversionTest {
        val failed = sheet("91", "FAILED")
        val sheets = Sheets(listOf(failed))
        val conversions = Conversions().apply {
            retryAnswer = SheetConversionRetry.Accepted(SheetConversion("91", SheetConversionStatus.Queued, pollAfterMs = 2_000))
        }
        val model = model(sheets, conversions)
        // 변환 중 → 상태 조회에서 '다시 시도할 수 있는 실패'를 받는다.
        conversions.answers = ArrayDeque(listOf(SheetConversionLookup.Found(SheetConversion("91", SheetConversionStatus.Failed, retryable = true))))
        sheets.sheets.value = listOf(sheet("91", "RUNNING")); runCurrent()
        model.watchConversions(); runCurrent()
        sheets.sheets.value = listOf(failed); runCurrent()
        val askedBefore = conversions.asked.size

        conversions.answers = ArrayDeque(listOf(running(1)))
        model.retryConversion(failed); runCurrent()
        assertEquals(1, conversions.retries)
        assertNull(model.uiState.value.conversionNotice)
        assertEquals(askedBefore + 1, conversions.asked.size, "다시 줄을 서면 상태를 다시 묻는다")
        assertEquals(SheetConversionStatus.Running, model.uiState.value.conversionOf(failed)?.status)
    }
}

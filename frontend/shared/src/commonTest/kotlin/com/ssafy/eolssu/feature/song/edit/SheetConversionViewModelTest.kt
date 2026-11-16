package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetOrigin
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionFailure
import com.ssafy.eolssu.data.song.SheetConversionLookup
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionRetry
import com.ssafy.eolssu.data.song.SheetConversionStage
import com.ssafy.eolssu.data.song.SheetConversionStatus
import com.ssafy.eolssu.data.song.SheetNotation
import com.ssafy.eolssu.feature.song.ConversionFailureGuide
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
// 끝나지 않은 변환은 계속 묻기 때문에, 묻는 중인 화면은 테스트 끝에 stop()으로 멈춰야 runTest가 끝난다.
internal class SheetConversionViewModelTest : CatalogViewModelTest() {
    private class Conversions : SheetConversionRepository {
        val answers = ArrayDeque<SheetConversionLookup>()
        var asked = 0
        val acknowledged = mutableListOf<String>()
        override suspend fun status(sheetId: String): SheetConversionLookup {
            asked++
            return answers.removeFirstOrNull() ?: SheetConversionLookup.Unavailable
        }
        override suspend fun retry(sheetId: String) = SheetConversionRetry.NotRetryable
        override fun acknowledgeCompletion(sheetId: String) { acknowledged += sheetId }
    }

    private class Sheets : MySheetRepository {
        override val sheets = MutableStateFlow(listOf(
            MySheet("7", MySheetOrigin.Imported, "아리랑 사진", updatedAt = KoreanDateTime(2026, 11, 16, 9, 0), conversionStatus = "RUNNING"),
        ))
        override suspend fun delete(sheetId: String) = Unit
    }

    private fun found(status: SheetConversionStatus, stage: SheetConversionStage? = null, failure: SheetConversionFailure? = null) =
        SheetConversionLookup.Found(SheetConversion("7", status, stage, pageCount = 2, pollAfterMs = 2_000,
            failure = failure, notation = SheetNotation.Staff))

    private fun TestScope.model(conversions: Conversions) =
        keep(SheetConversionViewModel("7", conversions, Sheets(), clockMs = { currentTime }))

    @Test
    fun followsQueuedReadingBuildingAndOpensTheEditorWhenDone() = runTest {
        val conversions = Conversions().apply {
            answers += found(SheetConversionStatus.Queued)
            answers += found(SheetConversionStatus.Running, SheetConversionStage.Recognizing)
            answers += found(SheetConversionStatus.Running, SheetConversionStage.Recognizing)
            answers += found(SheetConversionStatus.Running, SheetConversionStage.BuildingScore)
            answers += found(SheetConversionStatus.Done)
        }
        val model = model(conversions)
        model.watch(); runCurrent()
        assertEquals("아리랑 사진", model.uiState.value.title, "목록에서 찾은 제목을 보인다")
        assertEquals(SheetConversionStatus.Queued, model.uiState.value.conversion?.status)

        advanceTimeBy(2_001)
        val readingStart = model.uiState.value.progress ?: error("읽는 중 진행 값")
        advanceTimeBy(2_000)
        assertEquals(2_000, model.uiState.value.readingElapsedMs, "읽기 시작부터 지난 시간을 잰다")
        assertTrue((model.uiState.value.progress ?: 0f) > readingStart, "읽는 동안 막대가 조금씩 찬다")

        advanceTimeBy(2_000)
        assertEquals(SheetConversionStage.BuildingScore, model.uiState.value.conversion?.stage)
        assertFalse(model.uiState.value.openEditor)

        advanceTimeBy(2_000)
        assertTrue(model.uiState.value.openEditor, "완료되면 편집 화면으로 보낸다")
        assertEquals(1f, model.uiState.value.progress)
        assertEquals(listOf("7"), conversions.acknowledged, "같은 완료를 앱 배너로 다시 알리지 않는다")

        val asked = conversions.asked
        advanceTimeBy(30_000)
        assertEquals(asked, conversions.asked, "끝난 변환은 더 묻지 않는다")
        model.editorOpened()
        assertFalse(model.uiState.value.openEditor)
    }

    @Test
    fun failureStopsPollingAndCarriesTheNotationSpecificGuide() = runTest {
        val conversions = Conversions().apply { answers += found(SheetConversionStatus.Failed, failure = SheetConversionFailure.NoScoreFound) }
        val model = model(conversions)
        model.watch(); runCurrent()
        assertEquals(ConversionFailureGuide.NoStaff, model.uiState.value.failure)
        assertNull(model.uiState.value.progress, "실패하면 진행 막대가 없다")
        advanceTimeBy(30_000)
        assertEquals(1, conversions.asked)
        assertTrue(conversions.acknowledged.isEmpty())
    }

    @Test
    fun missingSheetStopsAndRepeatedUnavailableShowsConnectionNoticeWhileRetrying() = runTest {
        val gone = Conversions().apply { answers += SheetConversionLookup.NotFound }
        val missing = model(gone)
        missing.watch(); runCurrent()
        assertTrue(missing.uiState.value.missing)
        advanceTimeBy(10_000)
        assertEquals(1, gone.asked)

        val flaky = Conversions()
        val model = model(flaky)
        model.watch(); runCurrent()
        advanceTimeBy(2_001)
        assertFalse(model.uiState.value.unreachable, "한두 번 실패는 조용히 다시 묻는다")
        advanceTimeBy(2_000)
        assertTrue(model.uiState.value.unreachable)
        flaky.answers += found(SheetConversionStatus.Running, SheetConversionStage.Recognizing)
        advanceTimeBy(2_000)
        assertFalse(model.uiState.value.unreachable, "응답이 오면 안내를 거둔다")
        model.stop()
    }

    @Test
    fun hiddenScreenStopsAskingAndResumesOnReturn() = runTest {
        val conversions = Conversions().apply { repeat(5) { answers += found(SheetConversionStatus.Queued) } }
        val model = model(conversions)
        model.watch(); runCurrent()
        model.stop()
        advanceTimeBy(20_000)
        assertEquals(1, conversions.asked, "가려진 동안은 묻지 않는다")
        model.watch(); runCurrent()
        assertEquals(2, conversions.asked)
        model.stop()
    }
}

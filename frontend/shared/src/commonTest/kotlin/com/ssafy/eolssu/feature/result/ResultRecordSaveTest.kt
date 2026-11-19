package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeRecordUnavailable
import com.ssafy.eolssu.data.practice.SampleEvaluationRepository
import com.ssafy.eolssu.data.practice.UnavailablePracticeRecordRepository
import com.ssafy.eolssu.data.practice.PreviousRecord
import com.ssafy.eolssu.data.practice.RecordComparison
import com.ssafy.eolssu.data.practice.SectionEvaluation
import com.ssafy.eolssu.data.practice.WeakSectionPick
import com.ssafy.eolssu.data.practice.WeakSectionRepository
import com.ssafy.eolssu.data.practice.WeakSectionSource
import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.data.song.SampleSongRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ResultRecordSaveTest {
    @Test fun unavailableRepositoryHasNoExampleOrSharedRecords() = withResult(UnavailablePracticeRecordRepository()) { model, records ->
        assertTrue(records.records.value.isEmpty())
        assertFailsWith<PracticeRecordUnavailable> {
            records.saveEvaluation(requireNotNull(model.uiState.value.result), "곡")
        }
        assertFailsWith<PracticeRecordUnavailable> { records.updateMemo("another-account-record", "메모") }
        assertTrue(records.records.value.isEmpty())
    }

    @Test fun saveFirstOpensTheMemoFormWithoutWriting(): TestResult {
        val records = ControlledRecords()
        return withResult(records) { model, _ ->
            model.save()
            runCurrent()
            assertEquals(SaveSheet.Form, model.uiState.value.saveSheet)
            assertEquals(0, records.saveCalls, "‘연습 기록 저장’만 눌러서는 저장하지 않는다")
            model.closeSheet()
            assertNull(model.uiState.value.saveSheet)
            assertEquals(0, records.saveCalls)
        }
    }

    @Test fun confirmSavesOnceWithTheMemoAndShowsOnlyTheSavedStep(): TestResult {
        val records = ControlledRecords()
        return withResult(records) { model, _ ->
            model.save()
            model.changeMemo("  3각에서 박이 밀림 ")
            model.confirmSave()
            runCurrent()
            assertEquals(1, records.saveCalls)
            assertEquals("3각에서 박이 밀림", records.lastMemo, "메모는 저장과 같은 요청에 싣는다")
            assertEquals(0, records.memoCalls, "저장 뒤 메모를 따로 고치지 않는다")
            assertEquals(SaveSheet.Saved, model.uiState.value.saveSheet)
            assertNotNull(model.uiState.value.savedRecord)
            model.closeSheet()
            assertNull(model.uiState.value.saveSheet)
            // 저장 뒤 다시 누르면 ②만 뜨고 다시 저장하지 않는다. 메모도 더 바뀌지 않는다.
            model.save()
            assertEquals(SaveSheet.Saved, model.uiState.value.saveSheet)
            model.changeMemo("다른 메모")
            model.confirmSave()
            runCurrent()
            assertEquals(1, records.saveCalls)
            assertEquals("3각에서 박이 밀림", model.uiState.value.memo)
        }
    }

    @Test fun unavailableSaveKeepsTheFormAndMemo() = withResult(UnavailablePracticeRecordRepository()) { model, _ ->
        model.save()
        model.changeMemo("초안")
        model.confirmSave()
        runCurrent()
        assertEquals(RecordSaveError.Unavailable, model.uiState.value.recordSaveError)
        assertNull(model.uiState.value.savedRecord)
        assertEquals(SaveSheet.Form, model.uiState.value.saveSheet)
        assertEquals("초안", model.uiState.value.memo)
        assertFalse(model.uiState.value.savingRecord)
        model.dismissRecordSaveError()
        assertNull(model.uiState.value.recordSaveError)
    }

    @Test fun failedSaveCanBeRetriedWithoutClaimingEarlierSuccess(): TestResult {
        val records = ControlledRecords().apply { saveFailure = IllegalStateException("transport failure") }
        return withResult(records) { model, _ ->
            model.save()
            model.confirmSave()
            runCurrent()
            assertEquals(RecordSaveError.Failed, model.uiState.value.recordSaveError)
            assertNull(model.uiState.value.savedRecord)
            assertEquals(SaveSheet.Form, model.uiState.value.saveSheet)
            records.saveFailure = null
            model.confirmSave()
            assertNull(model.uiState.value.recordSaveError)
            runCurrent()
            assertEquals(2, records.saveCalls)
            assertNotNull(model.uiState.value.savedRecord)
            assertEquals(SaveSheet.Saved, model.uiState.value.saveSheet)
            assertFalse(model.uiState.value.savingRecord)
        }
    }

    @Test fun repeatedTapsDoNotWriteTheSameResultConcurrently(): TestResult {
        val records = ControlledRecords().apply { saveGate = CompletableDeferred() }
        return withResult(records) { model, _ ->
            model.save()
            model.confirmSave()
            model.confirmSave()
            runCurrent()
            assertEquals(1, records.saveCalls)
            assertTrue(model.uiState.value.savingRecord)
            assertNull(model.uiState.value.savedRecord)
            // 저장 중에는 시트를 닫지 않는다.
            model.closeSheet()
            assertEquals(SaveSheet.Form, model.uiState.value.saveSheet)
            records.saveGate?.complete(Unit)
            runCurrent()
            assertEquals(SaveSheet.Saved, model.uiState.value.saveSheet)
            assertEquals(1, records.saveCalls)
        }
    }

    @Test fun cancellationIsNotShownAsAStorageFailureOrSuccess(): TestResult {
        val records = ControlledRecords().apply { saveFailure = CancellationException("screen removed") }
        return withResult(records) { model, _ ->
            model.save()
            model.confirmSave()
            runCurrent()
            assertNull(model.uiState.value.recordSaveError)
            assertNull(model.uiState.value.savedRecord)
            assertFalse(model.uiState.value.savingRecord)
        }
    }

    @Test fun leavingAnUnsavedResultAsksFirstAndKeepStaysOnTheScreen(): TestResult {
        val records = ControlledRecords()
        return withResult(records) { model, _ ->
            assertTrue(model.uiState.value.confirmsLeave)
            assertFalse(model.requestLeave(), "저장 전에는 바로 나가지 않는다")
            assertTrue(model.uiState.value.leaveWarning)
            model.dismissLeaveWarning()
            assertFalse(model.uiState.value.leaveWarning)
            assertNotNull(model.uiState.value.result, "‘계속 보기’는 결과를 그대로 둔다")
            assertEquals(0, records.saveCalls)
        }
    }

    @Test fun leavingASavedResultGoesBackWithoutAsking(): TestResult {
        val records = ControlledRecords()
        return withResult(records) { model, _ ->
            model.save()
            model.confirmSave()
            runCurrent()
            assertNotNull(model.uiState.value.savedRecord)
            model.closeSheet()
            assertFalse(model.uiState.value.confirmsLeave)
            assertTrue(model.requestLeave(), "저장을 마친 결과는 바로 나간다")
            assertFalse(model.uiState.value.leaveWarning)
        }
    }

    @Test fun failedSaveStillAsksBeforeLeaving(): TestResult {
        val records = ControlledRecords().apply { saveFailure = IllegalStateException("transport failure") }
        return withResult(records) { model, _ ->
            model.save()
            model.confirmSave()
            runCurrent()
            assertNull(model.uiState.value.savedRecord)
            assertFalse(model.requestLeave(), "저장에 실패했으면 결과가 저장되지 않았으므로 확인한다")
            assertTrue(model.uiState.value.leaveWarning)
        }
    }

    @Test fun resultWithoutSongLeavesWithoutAsking() {
        assertFalse(ResultUiState().confirmsLeave)
    }

    @Test fun weakSectionAndFilterShareTheResultScreen(): TestResult {
        val weak = Picks(listOf(WeakSectionPick(1, 2, errorCount = 2)))
        return withResult(ControlledRecords(), weak) { model, _ ->
            assertEquals(WeakSectionPick(1, 2, 2), model.uiState.value.weakSection)
            assertEquals(listOf(model.uiState.value.result?.id), weak.asked)
            model.selectFilter(ErrorFilter.Rhythm)
            assertEquals(ErrorFilter.Rhythm, model.uiState.value.filter)
        }
    }

    @Test fun failedWeakSectionOnlyHidesTheLine() = withResult(ControlledRecords(), Picks(null)) { model, _ ->
        assertNull(model.uiState.value.weakSection)
        assertNotNull(model.uiState.value.result)
    }

    private class Picks(private val picks: List<WeakSectionPick>?) : WeakSectionRepository {
        val asked = mutableListOf<String>()
        override suspend fun source(sourceId: String): WeakSectionSource? = error("unused")
        override suspend fun weakSections(sessionId: String): List<WeakSectionPick>? { asked += sessionId; return picks }
        override suspend fun sectionResult(sessionId: String): SectionEvaluation? = error("unused")
        override suspend fun comparison(recordId: String): RecordComparison? = error("unused")
        override suspend fun previous(currentId: String, previousId: String): PreviousRecord? = error("unused")
    }

    private fun withResult(
        records: PracticeRecordRepository,
        weakSections: WeakSectionRepository? = null,
        block: suspend TestScope.(ResultViewModel, PracticeRecordRepository) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val songs = SampleSongRepository()
            val songId = songs.getSongs().first().id
            // The sample evaluation is confined to this test fixture, not production DI.
            val evaluations = SampleEvaluationRepository(now = { 0L }, analysisMillis = 0)
            val analysisId = evaluations.requestAnalysis(songId, bpm = 60, recordedSeconds = 20)
            val evaluationId = (evaluations.analysisStatus(analysisId) as AnalysisStatus.Done).evaluationId
            val model = ResultViewModel(evaluationId, evaluations, songs, records, weakSections = weakSections)
            store.put("result", model)
            runCurrent()
            assertNotNull(model.uiState.value.song)
            block(model, records)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private class ControlledRecords : PracticeRecordRepository {
    override val records = MutableStateFlow(emptyList<PracticeRecord>())
    var saveCalls = 0
    var memoCalls = 0
    var lastMemo: String? = null
    var saveFailure: Exception? = null
    var memoFailure: Exception? = null
    var saveGate: CompletableDeferred<Unit>? = null

    override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String): PracticeRecord {
        saveCalls++
        lastMemo = memo
        saveGate?.await()
        saveFailure?.let { throw it }
        return PracticeRecord(
            id = "record-${result.id}", songId = result.songId, songTitle = songTitle,
            section = null, repeats = null, recordedAt = result.recordedAt,
            recordedSeconds = result.recordedSeconds, bpm = result.bpm, score = result.total,
            metrics = result.metrics, memo = memo, errors = result.errors,
        )
    }

    override suspend fun updateMemo(recordId: String, memo: String) {
        memoCalls++
        memoFailure?.let { throw it }
    }
}

package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.PracticeRecordUnavailable
import com.ssafy.eolssu.data.practice.SampleEvaluationRepository
import com.ssafy.eolssu.data.practice.UnavailablePracticeRecordRepository
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

    @Test fun unavailableSaveShowsAnErrorWithoutACompletionSheet() = withResult(UnavailablePracticeRecordRepository()) { model, _ ->
        model.save()
        runCurrent()
        assertEquals(RecordSaveError.Unavailable, model.uiState.value.recordSaveError)
        assertNull(model.uiState.value.savedRecord)
        assertFalse(model.uiState.value.sheetOpen)
        assertFalse(model.uiState.value.savingRecord)
        model.dismissRecordSaveError()
        assertNull(model.uiState.value.recordSaveError)
        model.save()
        runCurrent()
        assertEquals(RecordSaveError.Unavailable, model.uiState.value.recordSaveError)
        assertFalse(model.uiState.value.sheetOpen)
    }

    @Test fun failedSaveCanBeRetriedWithoutClaimingEarlierSuccess(): TestResult {
        val records = ControlledRecords().apply { saveFailure = IllegalStateException("transport failure") }
        return withResult(records) { model, _ ->
            model.save()
            runCurrent()
            assertEquals(RecordSaveError.Failed, model.uiState.value.recordSaveError)
            assertNull(model.uiState.value.savedRecord)
            assertFalse(model.uiState.value.sheetOpen)
            records.saveFailure = null
            model.save()
            assertNull(model.uiState.value.recordSaveError)
            runCurrent()
            assertEquals(2, records.saveCalls)
            assertNotNull(model.uiState.value.savedRecord)
            assertTrue(model.uiState.value.sheetOpen)
            assertFalse(model.uiState.value.savingRecord)
        }
    }

    @Test fun repeatedTapsDoNotWriteTheSameResultConcurrently(): TestResult {
        val records = ControlledRecords().apply { saveGate = CompletableDeferred() }
        return withResult(records) { model, _ ->
            model.save()
            model.save()
            runCurrent()
            assertEquals(1, records.saveCalls)
            assertTrue(model.uiState.value.savingRecord)
            assertNull(model.uiState.value.savedRecord)
            assertFalse(model.uiState.value.sheetOpen)
            records.saveGate?.complete(Unit)
            runCurrent()
            assertTrue(model.uiState.value.sheetOpen)
            model.closeSheet()
            model.save()
            runCurrent()
            assertEquals(1, records.saveCalls)
            assertTrue(model.uiState.value.sheetOpen)
        }
    }

    @Test fun cancellationIsNotShownAsAStorageFailureOrSuccess(): TestResult {
        val records = ControlledRecords().apply { saveFailure = CancellationException("screen removed") }
        return withResult(records) { model, _ ->
            model.save()
            runCurrent()
            assertNull(model.uiState.value.recordSaveError)
            assertNull(model.uiState.value.savedRecord)
            assertFalse(model.uiState.value.sheetOpen)
            assertFalse(model.uiState.value.savingRecord)
        }
    }

    @Test fun failedMemoUpdateKeepsTheConfirmedMemoUnchanged(): TestResult {
        val records = ControlledRecords().apply { memoFailure = PracticeRecordUnavailable() }
        return withResult(records) { model, _ ->
            model.save()
            runCurrent()
            model.changeMemo("저장되지 않은 초안")
            model.commitMemo()
            runCurrent()
            assertEquals("", model.uiState.value.savedRecord?.memo)
            assertEquals("저장되지 않은 초안", model.uiState.value.memo)
            assertEquals(RecordSaveError.Unavailable, model.uiState.value.recordSaveError)
        }
    }

    private fun withResult(
        records: PracticeRecordRepository,
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
            val model = ResultViewModel(evaluationId, evaluations, songs, records)
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
    var saveFailure: Exception? = null
    var memoFailure: Exception? = null
    var saveGate: CompletableDeferred<Unit>? = null

    override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String): PracticeRecord {
        saveCalls++
        saveGate?.await()
        saveFailure?.let { throw it }
        return PracticeRecord(
            id = "record-${result.id}", songId = result.songId, songTitle = songTitle,
            section = null, repeats = null, recordedAt = result.recordedAt,
            recordedSeconds = result.recordedSeconds, bpm = result.bpm, score = result.total,
            metrics = result.metrics, memo = "", errors = result.errors,
        )
    }

    override suspend fun updateMemo(recordId: String, memo: String) {
        memoFailure?.let { throw it }
    }
}

package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.UnavailablePracticeRecordRepository
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordListAvailabilityTest : CatalogViewModelTest() {
    @Test fun unavailableLookupNeverAppearsAsAnAvailableEmptyHistory() = runTest {
        val unavailable = keep(RecordListViewModel(null, UnavailablePracticeRecordRepository(), now = { 0L }))
        val empty = keep(RecordListViewModel(null, EmptyAvailableRecords(), now = { 0L }))
        // Initial composition occurs before flow collection; it must already distinguish these states.
        assertFalse(unavailable.uiState.value.recordsAvailable)
        assertTrue(empty.uiState.value.recordsAvailable)
        assertEquals(empty.uiState.value.summaries, unavailable.uiState.value.summaries)
        runCurrent()
        unavailable.changeSort(RecordSort.Oldest)
        assertFalse(unavailable.uiState.value.recordsAvailable)
        assertTrue(empty.uiState.value.recordsAvailable)
        assertEquals(empty.uiState.value.groups, unavailable.uiState.value.groups)
    }
}

private class EmptyAvailableRecords : PracticeRecordRepository {
    override val records = MutableStateFlow(emptyList<PracticeRecord>())
    override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String): PracticeRecord = error("Not used")
    override suspend fun updateMemo(recordId: String, memo: String) = error("Not used")
}

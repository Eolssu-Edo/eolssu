package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.RecordListChange
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 기록 목록(RECORD-01)이 서버 목록(REC-02)을 받는 동안·받지 못했을 때를 빈 목록과 구분하는지(S15P31E102-369). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordListLoadTest : CatalogViewModelTest() {
    private class Records(var ok: Boolean, initial: List<PracticeRecord> = emptyList()) : PracticeRecordRepository {
        private val stored = MutableStateFlow(initial)
        override val records: StateFlow<List<PracticeRecord>> = stored
        override val listChanges = MutableSharedFlow<RecordListChange>()

        suspend fun deleteSavedRecord(id: String) {
            stored.value = stored.value.filterNot { it.id == id }
            listChanges.emit(RecordListChange.Reload(deletedId = id))
        }
        var refreshes = 0
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String) = error("쓰지 않음")
        override suspend fun updateMemo(recordId: String, memo: String) = Unit
        override suspend fun refresh(): Boolean {
            refreshes++
            return ok
        }
    }

    @Test
    fun deletedSongAndRetryStayCorrectWhenTheRefreshFails() = runTest {
        val sample = SamplePracticeRecordRepository().records.value.distinctBy { it.songId }.take(2)
        val deleted = sample.first()
        val repo = Records(ok = true, initial = sample)
        val model = keep(RecordListViewModel(null, repo, now = { 0L }))
        runCurrent()
        assertEquals(2, model.uiState.value.totalRecordCount)

        repo.ok = false
        repo.deleteSavedRecord(deleted.id)
        runCurrent()
        val failed = model.uiState.value
        assertTrue(failed.showLoadStatus)
        assertEquals(listOf(sample.last().songId), failed.groups.map { it.newestSongId })
        assertEquals(1, failed.totalRecordCount)

        repo.ok = true
        model.retry()
        runCurrent()
        assertFalse(model.uiState.value.showLoadStatus)
        assertEquals(1, model.uiState.value.totalRecordCount)
    }

    @Test
    fun loadsOnOpenAndRetriesAfterFailure() = runTest {
        val repo = Records(ok = false)
        val model = keep(RecordListViewModel(null, repo, now = { 0L }))
        assertTrue(model.uiState.value.loading)
        assertTrue(model.uiState.value.showLoadStatus)
        runCurrent()
        assertTrue(model.uiState.value.loadFailed)
        assertTrue(model.uiState.value.showLoadStatus)

        repo.ok = true
        model.retry()
        runCurrent()
        assertEquals(2, repo.refreshes)
        assertFalse(model.uiState.value.loadFailed)
        // 받은 기록이 없으면 이제 빈 목록 안내를 보인다.
        assertFalse(model.uiState.value.showLoadStatus)
    }
}

package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.*
import com.ssafy.eolssu.data.practice.*
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** 기록 상세 메모 창의 읽기 → 고치기 → 저장·취소·실패(S15P31E102-819), 빈 메모도 읽기로 열기(S15P31E102-825). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordMemoEditTest : CatalogViewModelTest() {
    private val song = SampleSongs.first()
    private val record = PracticeRecord(
        "41", song.id, song.title, null, null, KoreanDateTime(2026, 11, 7, 14, 0), 48, 60, 73,
        listOf(MetricScore(EvaluationMetric.Pitch, 61)), "박자 밀림", emptyList(),
    )

    private class Records(value: PracticeRecord) : PracticeRecordRepository {
        override val records = MutableStateFlow(listOf(value))
        val sent = mutableListOf<Pair<String, String>>()
        var update: suspend (String) -> Unit = {}
        override suspend fun detail(recordId: String): PracticeRecord? = records.value.firstOrNull { it.id == recordId }
        override suspend fun delete(recordId: String) = RecordDeleteOutcome.Deleted
        override suspend fun playback(recordId: String) = RecordPlayback.Unavailable
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String): PracticeRecord = error("unused")
        override suspend fun updateMemo(recordId: String, memo: String) {
            sent += recordId to memo
            update(memo)
            // 서버 저장 뒤 저장소가 목록 기록의 메모를 바꾸는 것과 같다.
            records.update { list -> list.map { if (it.id == recordId) it.copy(memo = memo) else it } }
        }
    }

    @Test fun savedMemoReplacesDetailAndListAndReturnsToReading() = runTest {
        val repo = Records(record)
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.openMemo()
        assertEquals(RecordMemoState(), model.uiState.value.memo)
        model.editMemo()
        assertEquals(RecordMemoState(editing = true), model.uiState.value.memo)

        val pending = CompletableDeferred<Unit>()
        repo.update = { pending.await() }
        model.saveMemo("  3–4각에서 박자가 밀림  ")
        runCurrent()
        assertEquals(RecordMemoState(editing = true, saving = true), model.uiState.value.memo)
        // 저장 중에는 취소·닫기·다시 저장을 받지 않는다.
        model.cancelMemoEdit()
        model.closeMemo()
        model.saveMemo("다른 메모")
        assertTrue(model.uiState.value.memo!!.saving)

        pending.complete(Unit)
        runCurrent()
        assertEquals(listOf("41" to "3–4각에서 박자가 밀림"), repo.sent)
        assertEquals("3–4각에서 박자가 밀림", model.uiState.value.record?.memo)
        assertEquals("3–4각에서 박자가 밀림", repo.records.value.single().memo)
        assertEquals(RecordMemoState(), model.uiState.value.memo)
        model.closeMemo()
        assertNull(model.uiState.value.memo)
    }

    @Test fun failedSaveKeepsEditingUntilRetrySucceeds() = runTest {
        val repo = Records(record).apply { update = { error("400") } }
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.openMemo()
        model.editMemo()
        model.saveMemo("새 메모")
        runCurrent()
        assertEquals(RecordMemoState(editing = true, failed = true), model.uiState.value.memo)
        assertEquals("박자 밀림", model.uiState.value.record?.memo)

        repo.update = {}
        model.saveMemo("새 메모")
        runCurrent()
        assertEquals("새 메모", model.uiState.value.record?.memo)
        assertEquals(RecordMemoState(), model.uiState.value.memo)
    }

    @Test fun unchangedMemoSkipsRequestAndCancelReturnsToReading() = runTest {
        val repo = Records(record)
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.openMemo()
        model.editMemo()
        model.cancelMemoEdit()
        assertEquals(RecordMemoState(), model.uiState.value.memo)
        model.editMemo()
        model.saveMemo(" 박자 밀림 ")
        runCurrent()
        assertTrue(repo.sent.isEmpty())
        assertEquals(RecordMemoState(), model.uiState.value.memo)
    }

    @Test fun recordWithoutMemoOpensReadingAndEditsOnlyAfterPencil() = runTest {
        val repo = Records(record.copy(memo = ""))
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        // 메모가 없는 기록도 읽기로 열고, 연필 버튼을 누르기 전에는 저장을 받지 않는다.
        model.openMemo()
        assertEquals(RecordMemoState(), model.uiState.value.memo)
        model.saveMemo("첫 메모")
        runCurrent()
        assertTrue(repo.sent.isEmpty())
        assertEquals(RecordMemoState(), model.uiState.value.memo)
        // 연필 버튼으로 고치기에 들어가고, 취소하면 읽기로 돌아간다.
        model.editMemo()
        assertEquals(RecordMemoState(editing = true), model.uiState.value.memo)
        model.cancelMemoEdit()
        assertEquals(RecordMemoState(), model.uiState.value.memo)
        model.closeMemo()
        assertNull(model.uiState.value.memo)

        model.openMemo()
        model.editMemo()
        model.saveMemo("첫 메모")
        runCurrent()
        assertEquals("첫 메모", model.uiState.value.record?.memo)
        assertEquals(RecordMemoState(), model.uiState.value.memo)

        // 공백만 남기면 메모를 지우고(서버가 빈 값으로 지운다) 창을 닫는다.
        model.editMemo()
        model.saveMemo("   ")
        runCurrent()
        assertEquals("41" to "", repo.sent.last())
        assertEquals("", model.uiState.value.record?.memo)
        assertNull(model.uiState.value.memo)
    }

    @Test fun overLengthMemoIsNotSent() = runTest {
        val repo = Records(record)
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.openMemo()
        model.editMemo()
        model.saveMemo("가".repeat(RECORD_MEMO_MAX_LENGTH + 1))
        runCurrent()
        assertTrue(repo.sent.isEmpty())
        assertEquals(RecordMemoState(editing = true, failed = true), model.uiState.value.memo)
        model.saveMemo("가".repeat(RECORD_MEMO_MAX_LENGTH))
        runCurrent()
        assertEquals(RECORD_MEMO_MAX_LENGTH, repo.sent.single().second.length)
    }

    @Test fun switchingRecordDropsMemoWindowAndLateResult() = runTest {
        val repo = Records(record)
        repo.records.update { it + record.copy(id = "42", memo = "") }
        val pending = CompletableDeferred<Unit>()
        repo.update = { pending.await() }
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.openMemo()
        model.editMemo()
        model.saveMemo("늦은 메모")
        runCurrent()
        model.show("42")
        assertNull(model.uiState.value.memo)
        pending.complete(Unit)
        runCurrent()
        assertEquals("42", model.uiState.value.record?.id)
        assertNull(model.uiState.value.memo)
    }
}

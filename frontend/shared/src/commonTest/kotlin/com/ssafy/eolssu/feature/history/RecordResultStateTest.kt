package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.*
import com.ssafy.eolssu.core.ui.score.*
import com.ssafy.eolssu.data.practice.*
import com.ssafy.eolssu.data.song.CatalogInstrument
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.feature.practice.FakeAudioPlayer
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordResultStateTest : CatalogViewModelTest() {
    private val song = SampleSongs.first()
    private val record = PracticeRecord(
        "41", song.id, song.title, null, null, KoreanDateTime(2026, 11, 7, 14, 0), 48, 60, 73,
        listOf(MetricScore(EvaluationMetric.Pitch, 61)), "메모",
        listOf(ScoreError(0, 0, ScoreErrorKind.Pitch), ScoreError(0, 0, ScoreErrorKind.Rhythm)),
        eventErrors = mapOf(0 to listOf(ScoreErrorKind.Pitch)), recordingAvailable = true, isNew = true,
    )

    private class Records(val value: PracticeRecord) : PracticeRecordRepository {
        override val records = MutableStateFlow(listOf(value, value.copy(id = "42")))
        var read: suspend (String) -> PracticeRecord? = { id -> records.value.firstOrNull { it.id == id }?.copy(isNew = false) }
        var remove: suspend (String) -> RecordDeleteOutcome = { RecordDeleteOutcome.Deleted }
        val opened = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        override suspend fun detail(recordId: String): PracticeRecord? { opened += recordId; return read(recordId) }
        override suspend fun delete(recordId: String): RecordDeleteOutcome { deleted += recordId; return remove(recordId) }
        override suspend fun playback(recordId: String) = RecordPlayback.Ready("https://example.com/record.m4a", 900_000L, null)
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String): PracticeRecord = error("unused")
        override suspend fun updateMemo(recordId: String, memo: String) = Unit
    }

    @Test fun listValuesDoNotPretendToBeLoadedEvaluation() = runTest {
        val pending = CompletableDeferred<PracticeRecord?>()
        val repo = Records(record).apply { read = { pending.await() } }
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        assertEquals(73, model.uiState.value.record?.score)
        assertNull(model.uiState.value.evaluation)
        assertFalse(model.uiState.value.canPractice)
        pending.complete(record.copy(isNew = false))
        runCurrent()
        val state = model.uiState.value
        val result = assertNotNull(state.evaluation)
        assertEquals("41", result.id)
        assertEquals(song.id, result.songId)
        assertEquals(73, result.total)
        assertEquals(record.metrics, result.metrics)
        assertEquals(record.recordedAt, result.recordedAt)
        assertEquals(48, result.recordedSeconds)
        assertEquals(60, result.bpm)
        assertFalse(result.sample)
        assertEquals(mapOf(0 to listOf(ScoreErrorKind.Pitch)), result.eventErrors)
        assertEquals(mapOf((0 to 0) to listOf(ScoreErrorKind.Rhythm)), result.visibleErrorKinds(ErrorFilter.Rhythm))
        assertEquals(emptyMap(), result.visibleErrors(ErrorFilter.Length))
        assertTrue(state.canPractice)
        assertEquals(listOf("41"), repo.opened)
        assertFalse(state.record!!.isNew)
    }

    @Test fun entryShowsLoadingUntilScoreArrivesAndNotAfterFailure() = runTest {
        val pending = CompletableDeferred<PracticeRecord?>()
        val repo = Records(record).apply { read = { pending.await() } }
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        // 목록에서 가져온 기록만 있고 상세를 받는 중이면 실패 안내 대신 ‘로딩 중’ 막을 보인다.
        assertNotNull(model.uiState.value.record)
        assertTrue(model.uiState.value.lookupLoading)
        pending.complete(record.copy(isNew = false))
        runCurrent()
        assertNotNull(model.uiState.value.song)
        assertFalse(model.uiState.value.lookupLoading)

        val failed = keep(RecordDetailViewModel("41", Records(record).apply { read = { error("offline") } }, SampleSongRepository()))
        runCurrent()
        assertEquals(RecordSongLookup.Failed, failed.uiState.value.songLookup)
        assertFalse(failed.uiState.value.lookupLoading)
        assertTrue(RecordDetailUiState(songLookup = RecordSongLookup.Loading).lookupLoading)
        assertFalse(RecordDetailUiState().lookupLoading)
    }

    @Test fun failedDetailKeepsListScoreWithoutFiltersOrPractice() = runTest {
        val repo = Records(record).apply { read = { error("offline") } }
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        assertEquals(73, model.uiState.value.record?.score)
        assertNull(model.uiState.value.evaluation)
        assertFalse(model.uiState.value.canPractice)
        assertEquals(RecordNotice.DetailFailed, model.uiState.value.notice)
    }

    @Test fun deleteRequiresConfirmationAndStopsPlaybackAfterSuccess() = runTest {
        val outcomes = ArrayDeque(listOf(RecordDeleteOutcome.Busy, RecordDeleteOutcome.Failed, RecordDeleteOutcome.Deleted))
        val repo = Records(record).apply { remove = { outcomes.removeFirst() } }
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player))
        runCurrent()
        model.confirmDelete()
        model.askDelete()
        model.cancelDelete()
        assertEquals(RecordDeleteState.Idle, model.uiState.value.delete)
        assertTrue(repo.deleted.isEmpty())
        for (expected in listOf(RecordDeleteState.Busy, RecordDeleteState.Failed)) {
            model.askDelete()
            model.confirmDelete()
            model.confirmDelete()
            runCurrent()
            assertEquals(expected, model.uiState.value.delete)
            model.dismissDeleteError()
        }
        model.togglePlay()
        runCurrent()
        model.askDelete()
        model.confirmDelete()
        runCurrent()
        assertEquals(RecordDeleteState.Deleted, model.uiState.value.delete)
        assertFalse(model.uiState.value.playing)
        assertFalse(model.uiState.value.canPlay)
        assertEquals(null, player.state.value.sourceId)
        assertEquals(listOf("41", "41", "41"), repo.deleted)
    }

    @Test fun changingRecordKeepsFilterAndIgnoresPreviousDeleteResponse() = runTest {
        val deletion = CompletableDeferred<RecordDeleteOutcome>()
        val repo = Records(record).apply { remove = { deletion.await() } }
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.selectFilter(ErrorFilter.Pitch)
        model.askDelete()
        model.confirmDelete()
        runCurrent()
        model.show("42")
        assertFalse(model.uiState.value.detailLoaded)
        runCurrent()
        deletion.complete(RecordDeleteOutcome.Deleted)
        runCurrent()
        assertEquals("42", model.uiState.value.record?.id)
        assertEquals(ErrorFilter.Pitch, model.uiState.value.filter)
        assertEquals(RecordDeleteState.Idle, model.uiState.value.delete)
    }

    @Test fun instrumentLookupUsesRecordedSongAndFailureKeepsEvaluation() = runTest {
        val songs = object : SongRepository by SampleSongRepository() {
            override suspend fun getSong(songId: String) = song.copy(instrumentId = "8")
        }
        val catalog = listOf(CatalogInstrument("3", "SOGEUM", "소금"), CatalogInstrument("8", "DANSO", "단소"))
        val model = keep(RecordDetailViewModel("41", Records(record), songs, loadInstruments = { catalog }))
        val failed = keep(RecordDetailViewModel("41", Records(record), songs, loadInstruments = { error("offline") }))
        runCurrent()
        assertEquals("${song.title} · 단소", model.uiState.value.songTitle)
        assertEquals(song.title, failed.uiState.value.songTitle)
        assertNotNull(failed.uiState.value.evaluation)
    }

    @Test fun accountChangeCancelsMetadataAndDeletion() = runTest {
        val member = com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()).apply { set("A") }
        val metadata = CompletableDeferred<List<CatalogInstrument>>()
        val deletion = CompletableDeferred<RecordDeleteOutcome>()
        val songs = object : SongRepository by SampleSongRepository() {
            override suspend fun getSong(songId: String) = song.copy(instrumentId = "8")
        }
        val repo = Records(record).apply { remove = { deletion.await() } }
        val model = keep(RecordDetailViewModel("41", repo, songs, member = member, loadInstruments = { metadata.await() }))
        runCurrent()
        model.askDelete()
        model.confirmDelete()
        runCurrent()
        member.changing { member.set("B") }
        runCurrent()
        metadata.complete(listOf(CatalogInstrument("8", "DANSO", "단소")))
        deletion.complete(RecordDeleteOutcome.Deleted)
        runCurrent()
        assertNull(model.uiState.value.record)
        assertNull(model.uiState.value.evaluation)
        assertNull(model.uiState.value.instrumentName)
        assertEquals(RecordDeleteState.Idle, model.uiState.value.delete)
    }
}

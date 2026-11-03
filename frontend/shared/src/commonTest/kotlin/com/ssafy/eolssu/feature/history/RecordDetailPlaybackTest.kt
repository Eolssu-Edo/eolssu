package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.RecordPlayback
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.feature.practice.FakeAudioPlayer
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 기록 상세(RECORD-02)가 서버 상세를 채우고 녹음 임시 주소(REC-03)로 재생하는지(S15P31E102-369). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordDetailPlaybackTest : CatalogViewModelTest() {
    private val song = SampleSongs.first()

    private fun record(available: Boolean) = PracticeRecord(
        id = "41", songId = song.id, songTitle = song.title, section = null, repeats = null,
        recordedAt = KoreanDateTime(2026, 10, 20, 15, 20), recordedSeconds = 48, bpm = song.baseBpm, score = 84,
        metrics = emptyList(), memo = "", errors = emptyList(), recordingAvailable = available, isNew = true,
    )

    private class Records(listed: PracticeRecord, private val detail: PracticeRecord?) : PracticeRecordRepository {
        override val records: StateFlow<List<PracticeRecord>> = MutableStateFlow(listOf(listed))
        val playbacks = mutableListOf<RecordPlayback>()
        var asked = 0
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String) = error("쓰지 않음")
        override suspend fun updateMemo(recordId: String, memo: String) = Unit
        override suspend fun detail(recordId: String) = detail
        override suspend fun playback(recordId: String): RecordPlayback {
            asked++
            return playbacks.removeFirstOrNull() ?: RecordPlayback.Failed
        }
    }

    @Test
    fun detailReplacesListValuesAndUnavailableRecordingDisablesPlay() = runTest {
        val repo = Records(record(available = true), record(available = false).copy(memo = "서버 메모", isNew = false))
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player, now = { 0L }))
        runCurrent()

        val state = model.uiState.value
        assertEquals("서버 메모", state.record?.memo)
        assertFalse(state.canPlay)
        model.togglePlay()
        runCurrent()
        assertEquals(0, repo.asked)
        assertEquals(0, player.loads)
    }

    @Test
    fun playLoadsSignedUrlAndFollowsPlayerPosition() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", expiresAtMillis = 900_000, durationMs = 48_000)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player, now = { 0L }))
        runCurrent()

        model.togglePlay()
        runCurrent()
        assertEquals(1, player.loads)
        assertEquals(0L, player.requestedPosition)

        val third = model.uiState.value.startMillis(3)
        player.emit(AudioPlaybackStatus.Playing, third)
        runCurrent()
        assertTrue(model.uiState.value.playing)
        assertEquals(3, model.uiState.value.position)

        model.pause()
        model.togglePlay()
        runCurrent()
        // 아직 만료 전이라 주소를 다시 받지 않고 지금 위치부터 잇는다.
        assertEquals(1, repo.asked)
        assertEquals(third, player.requestedPosition)
    }

    @Test
    fun expiredUrlIsFetchedAgain() = runTest {
        var clock = 0L
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", expiresAtMillis = 900_000, durationMs = null)
        repo.playbacks += RecordPlayback.Ready("https://r2.example/b.m4a", expiresAtMillis = 1_800_000, durationMs = null)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player, now = { clock }))
        runCurrent()

        model.togglePlay()
        runCurrent()
        model.pause()
        clock = 880_000 // 만료 30초 전 안쪽
        model.togglePlay()
        runCurrent()
        assertEquals(2, repo.asked)
    }

    @Test
    fun missingRecordingTurnsOffPlayAndShowsNotice() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Unavailable
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), FakeAudioPlayer(), now = { 0L }))
        runCurrent()

        model.togglePlay()
        runCurrent()

        assertEquals(RecordNotice.PlaybackUnavailable, model.uiState.value.notice)
        assertFalse(model.uiState.value.canPlay)
        assertFalse(model.uiState.value.playing)
        model.consumeNotice()
        assertEquals(null, model.uiState.value.notice)
    }

    @Test
    fun busyServerKeepsRecordingPlayableForRetry() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Busy
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), FakeAudioPlayer(), now = { 0L }))
        runCurrent()

        model.togglePlay()
        runCurrent()

        assertEquals(RecordNotice.PlaybackBusy, model.uiState.value.notice)
        assertTrue(model.uiState.value.canPlay)
    }

    @Test
    fun deletedRecordShowsMissing() = runTest {
        val model = keep(RecordDetailViewModel("41", Records(record(available = true), detail = null), SampleSongRepository(), FakeAudioPlayer(), now = { 0L }))
        runCurrent()
        assertEquals(RecordSongLookup.Missing, model.uiState.value.songLookup)
        assertEquals(null, model.uiState.value.record)
    }
}

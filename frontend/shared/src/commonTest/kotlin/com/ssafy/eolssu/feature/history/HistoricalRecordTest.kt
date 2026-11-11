package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.PracticeRecordRepository
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoricalRecordTest : CatalogViewModelTest() {
    private val sample = SamplePracticeRecordRepository()
    private val original = SampleSongs.first().copy(scoreVersion = 1)
    private val current = original.copy(scoreVersion = 2)
    private val requested = mutableListOf<Int?>()
    private val songs = object : SongRepository by SampleSongRepository() {
        override suspend fun getSong(songId: String): Song { requested += null; return current }
        override suspend fun getSong(songId: String, scoreVersion: Int): Song {
            requested += scoreVersion
            return if (scoreVersion == 1) original else current
        }
    }

    @Test fun missingOrFailedScoreKeepsMemoScoresAndRecordingAndCanRetry() = runTest {
        for (missing in listOf(true, false)) {
            var available = false
            var refreshes = 0
            val detail = sample.records.value.first().copy(songId = original.id, scoreVersion = 1,
                memo = "보존 메모", recordingAvailable = true)
            val records = object : PracticeRecordRepository by sample {
                override val records = MutableStateFlow(emptyList<com.ssafy.eolssu.core.model.PracticeRecord>())
                override suspend fun refresh(): Boolean { refreshes++; error("목록 요청 금지") }
                override suspend fun detail(recordId: String) = detail
                override suspend fun playback(recordId: String) = com.ssafy.eolssu.data.practice.RecordPlayback.Ready("https://example.test/recording", 900000, 48000)
            }
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String, scoreVersion: Int): Song? {
                    assertEquals(1, scoreVersion)
                    if (available) return original
                    if (missing) return null
                    error("네트워크 실패")
                }
            }
            val player = com.ssafy.eolssu.feature.practice.FakeAudioPlayer()
            val model = keep(RecordDetailViewModel(detail.id, records, songs, player, now = { 0L }))
            runCurrent()
            assertEquals(0, refreshes)
            assertEquals("보존 메모", model.uiState.value.record?.memo)
            assertEquals(detail.score, model.uiState.value.evaluation?.total)
            assertEquals(null, model.uiState.value.song)
            kotlin.test.assertTrue(model.uiState.value.canPlay)
            model.togglePlay()
            runCurrent()
            assertEquals(1, player.loads)
            available = true
            model.retrySong()
            runCurrent()
            assertEquals(original, model.uiState.value.song)
            assertEquals(1, player.loads, "악보 재시도는 녹음을 재시작하지 않는다")
        }
    }

    @Test fun cachedRecordRetriesFailedDetailBeforeLoadingItsScore() = runTest {
        var attempts = 0
        val detail = sample.records.value.first().copy(songId = original.id, scoreVersion = 1, memo = "서버 상세")
        val records = object : PracticeRecordRepository by sample {
            override val records = MutableStateFlow(listOf(detail.copy(memo = "목록 캐시")))
            override suspend fun detail(recordId: String): com.ssafy.eolssu.core.model.PracticeRecord {
                attempts++
                if (attempts == 1) error("일시 상세 실패")
                return detail
            }
        }
        val model = keep(RecordDetailViewModel(detail.id, records, songs))
        runCurrent()
        kotlin.test.assertFalse(model.uiState.value.detailLoaded)
        assertEquals("목록 캐시", model.uiState.value.record?.memo)
        model.retrySong()
        runCurrent()
        assertEquals(2, attempts)
        kotlin.test.assertTrue(model.uiState.value.detailLoaded)
        assertEquals("서버 상세", model.uiState.value.record?.memo)
        assertEquals(original, model.uiState.value.song)
    }

    @Test fun retainedSongDoesNotHideDetailRetryAfterSwitchingRecords() = runTest {
        var failSecond = true
        val first = sample.records.value.first().copy(id = "first", songId = original.id, scoreVersion = 1)
        val second = first.copy(id = "second")
        val records = object : PracticeRecordRepository by sample {
            override val records = MutableStateFlow(listOf(first, second))
            override suspend fun detail(recordId: String): com.ssafy.eolssu.core.model.PracticeRecord {
                if (recordId == second.id && failSecond) error("상세 실패")
                return records.value.first { it.id == recordId }
            }
        }
        val model = keep(RecordDetailViewModel(first.id, records, songs))
        runCurrent()
        assertEquals(original, model.uiState.value.song)
        model.show(second.id)
        runCurrent()
        assertEquals(RecordSongLookup.Failed, model.uiState.value.songLookup)
        assertEquals(null, model.uiState.value.song, "상세 재시도 버튼을 악보 대신 표시한다")
        failSecond = false
        model.retrySong()
        runCurrent()
        kotlin.test.assertTrue(model.uiState.value.detailLoaded)
        assertEquals(second.id, model.uiState.value.record?.id)
        assertEquals(original, model.uiState.value.song)
    }

    @Test fun recordsOfSameSongNeverReuseAnotherRevision() = runTest {
        val base = sample.records.value.first().copy(songId = original.id)
        val listed = MutableStateFlow(listOf(base.copy(id = "old", scoreVersion = 1), base.copy(id = "new", scoreVersion = 2)))
        val records = object : PracticeRecordRepository by sample {
            override val records = listed
            override suspend fun detail(recordId: String) = listed.value.first { it.id == recordId }
        }
        val model = keep(RecordDetailViewModel("old", records, songs))
        runCurrent()
        assertEquals(1, model.uiState.value.song?.scoreVersion)
        assertEquals(1, model.uiState.value.evaluation?.scoreVersion)
        model.show("new")
        runCurrent()
        assertEquals(2, model.uiState.value.song?.scoreVersion)
        assertEquals(listOf<Int?>(1, 2), requested)
    }

    @Test fun detailRevisionReplacesCurrentSongLoadedFromVersionlessList() = runTest {
        val base = sample.records.value.first().copy(songId = original.id, scoreVersion = null)
        val records = object : PracticeRecordRepository by sample {
            override val records = MutableStateFlow(listOf(base))
            override suspend fun detail(recordId: String) = base.copy(scoreVersion = 1)
        }
        val model = keep(RecordDetailViewModel(base.id, records, songs))
        runCurrent()
        assertEquals(1, model.uiState.value.song?.scoreVersion)
        assertEquals(1, model.uiState.value.record?.scoreVersion)
    }
}

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

    private class Records(listed: PracticeRecord, private val detail: PracticeRecord?, others: List<PracticeRecord> = emptyList()) : PracticeRecordRepository {
        override val records: StateFlow<List<PracticeRecord>> = MutableStateFlow(listOf(listed) + others)
        val playbacks = mutableListOf<RecordPlayback>()
        var asked = 0
        override suspend fun saveEvaluation(result: EvaluationResult, songTitle: String, memo: String) = error("쓰지 않음")
        override suspend fun updateMemo(recordId: String, memo: String) = Unit
        override suspend fun detail(recordId: String) = detail
        override suspend fun playback(recordId: String): RecordPlayback {
            asked++
            return playbacks.removeFirstOrNull() ?: RecordPlayback.Failed
        }
    }


    @Test
    fun accountChangeClearsRecordAndStopsSignedUrlPlayback() = runTest {
        val member = com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()).apply { set("A") }
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/account-a.m4a", 900_000, 48_000)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player, now = { 0L }, member = member))
        runCurrent()
        model.togglePlay()
        runCurrent()
        assertEquals(1, player.loads)
        member.changing { member.set("B") }
        runCurrent()
        assertEquals(null, model.uiState.value.record)
        assertEquals(null, player.state.value.sourceId)
        assertFalse(model.uiState.value.playing)
        model.show("41")
        model.togglePlay()
        runCurrent()
        assertEquals(1, player.loads, "이전 화면은 기존 URL을 다시 사용하지 않는다")
        assertEquals(1, repo.asked)
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
    fun unavailableRecordingIgnoresSeekingAndSkipping() = runTest {
        val repo = Records(record(available = false), record(available = false))
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.seek(3)
        model.skipSeconds(5)
        assertEquals(0, model.uiState.value.position)
        assertEquals(0, repo.asked)
    }

    @Test
    fun tappingEventWhilePausedOnlyMovesPositionUntilPlayIsPressed() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", 900_000, null)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player))
        runCurrent()
        // 녹음 길이(48초) 안의 음을 고른다. 길이를 넘는 음은 끝으로 맞춰진다.
        val target = 3
        model.playAt(target)
        runCurrent()
        assertEquals(0, player.loads, "멈춰 있을 때 정간을 누르면 재생하지 않는다")
        assertEquals(0, repo.asked)
        assertFalse(model.uiState.value.playing)
        assertEquals(target, model.uiState.value.position)
        model.togglePlay()
        runCurrent()
        assertEquals(model.uiState.value.startMillis(target), player.requestedPosition, "재생 버튼을 누르면 고른 정간부터 재생한다")
        assertEquals(1, player.loads)
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
    fun playbackTimeMovesWithinOneNoteInsteadOfWaitingForNextNote() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", 900_000, 48_000)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player))
        runCurrent()
        model.togglePlay()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 125L)
        runCurrent()
        assertEquals(0, model.uiState.value.position)
        assertEquals(0.125, model.uiState.value.elapsedSeconds, 0.000001)
        player.emit(AudioPlaybackStatus.Playing, 175L)
        runCurrent()
        assertEquals(0, model.uiState.value.position)
        assertEquals(0.175, model.uiState.value.elapsedSeconds, 0.000001)
    }

    @Test
    fun pauseAndResumeKeepTheExactAudioPosition() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", 900_000, 48_000)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player))
        runCurrent()
        model.togglePlay()
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 125L)
        runCurrent()
        model.pause()
        runCurrent()
        model.togglePlay()
        runCurrent()
        assertEquals(125L, player.requestedPosition)
    }

    @Test
    fun sliderSeeksToExactMillisecondsAndSkipKeepsTheFractionalSecond() = runTest {
        val repo = Records(record(available = true), record(available = true))
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.seekToMillis(1_125L)
        assertEquals(1.125, model.uiState.value.elapsedSeconds, 0.000001)
        model.skipSeconds(5)
        assertEquals(6.125, model.uiState.value.elapsedSeconds, 0.000001)
        model.skipSeconds(-5)
        assertEquals(1.125, model.uiState.value.elapsedSeconds, 0.000001)
        model.seekToMillis(-1L)
        assertEquals(0.0, model.uiState.value.elapsedSeconds)
        model.seekToMillis(99_999L)
        assertEquals(48.0, model.uiState.value.elapsedSeconds)
        assertFalse(model.uiState.value.playing)
        assertEquals(0, repo.asked)
    }

    @Test
    fun seekingDuringPlaybackLoadsOnceAtTheExactPositionAndStopsAtTheEnd() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", 900_000, 48_000)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player))
        runCurrent()
        model.togglePlay()
        runCurrent()
        player.state.value = player.state.value.copy(durationMs = 48_000L)
        player.emit(AudioPlaybackStatus.Playing, 125L)
        runCurrent()
        model.seekToMillis(6_125L)
        runCurrent()
        assertEquals(6_125L, player.requestedPosition)
        assertEquals(2, player.loads)
        player.state.value = player.state.value.copy(durationMs = 48_000L)
        runCurrent()
        model.seekToMillis(48_000L)
        runCurrent()
        assertEquals(2, player.loads, "파일 끝을 잘못된 load 위치로 넘기지 않는다")
        assertEquals(48.0, model.uiState.value.elapsedSeconds)
        assertFalse(model.uiState.value.playing)
    }

    @Test
    fun resumingInsideTheLastNoteDoesNotRestartButCompletedPlaybackDoes() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", 900_000, 48_000)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player))
        runCurrent()
        model.togglePlay()
        runCurrent()
        val lastNote = model.uiState.value.startMillis(model.uiState.value.eventCount - 1) + 125L
        val duration = lastNote + 1_000L
        player.state.value = player.state.value.copy(durationMs = duration)
        player.emit(AudioPlaybackStatus.Playing, lastNote)
        runCurrent()
        model.pause()
        runCurrent()
        model.togglePlay()
        runCurrent()
        assertEquals(lastNote, player.requestedPosition)
        player.state.value = player.state.value.copy(durationMs = duration)
        player.emit(AudioPlaybackStatus.Completed, duration)
        runCurrent()
        assertEquals(duration, model.uiState.value.playbackPositionMs)
        model.togglePlay()
        runCurrent()
        assertEquals(0L, player.requestedPosition)
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

    @Test
    fun firstPlayShowsLoadingUntilPlayerStartsAndCachedUrlSkipsIt() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Ready("https://r2.example/a.m4a", expiresAtMillis = 900_000, durationMs = 48_000)
        val player = FakeAudioPlayer()
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), player, now = { 0L }))
        runCurrent()
        assertFalse(model.uiState.value.playbackLoading)
        model.togglePlay()
        assertTrue(model.uiState.value.playbackLoading, "녹음 주소를 받는 처음 재생은 ‘로딩 중’")
        runCurrent()
        assertTrue(model.uiState.value.playbackLoading, "플레이어가 불러오는 중이면 그대로 둔다")
        player.emit(AudioPlaybackStatus.Playing, 0)
        runCurrent()
        assertFalse(model.uiState.value.playbackLoading)
        model.pause()
        model.togglePlay()
        assertFalse(model.uiState.value.playbackLoading, "받아 둔 주소로 이어 들을 때는 띄우지 않는다")
    }

    @Test
    fun failedFirstPlayClearsLoading() = runTest {
        val repo = Records(record(available = true), record(available = true))
        repo.playbacks += RecordPlayback.Busy
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), FakeAudioPlayer(), now = { 0L }))
        runCurrent()
        model.togglePlay()
        runCurrent()
        assertFalse(model.uiState.value.playbackLoading)
        assertFalse(model.uiState.value.playing)
    }

    @Test
    fun manualScrollStopsFollowingUntilFollowNowOrNextPlay() = runTest {
        val repo = Records(record(available = true), record(available = true))
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), FakeAudioPlayer(), now = { 0L }))
        runCurrent()
        assertTrue(model.uiState.value.follow)
        assertFalse(model.uiState.value.showFollowNow)
        model.detachFollow()
        assertTrue(model.uiState.value.showFollowNow)
        model.followNow()
        assertFalse(model.uiState.value.showFollowNow)
        model.detachFollow()
        model.togglePlay()
        assertTrue(model.uiState.value.follow, "다시 재생하면 재생 위치를 따라간다")
    }

    @Test
    fun recordingWithoutAudioNeverShowsFollowNow() = runTest {
        val repo = Records(record(available = false), record(available = false))
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository()))
        runCurrent()
        model.detachFollow()
        assertFalse(model.uiState.value.showFollowNow)
    }

    @Test
    fun compareOpensPickSheetWithAllEarlierWholeRecords() = runTest {
        val current = record(available = true)
        // copy는 recordedAtMillis 기본값을 다시 구하지 않으므로 생성자로 만든다.
        fun earlier(id: String, month: Int, day: Int, section: String? = null) = PracticeRecord(
            id = id, songId = song.id, songTitle = song.title, section = section, repeats = null,
            recordedAt = KoreanDateTime(2026, month, day, 9, 0), recordedSeconds = 48, bpm = song.baseBpm, score = 70,
            metrics = emptyList(), memo = "", errors = emptyList(),
        )
        val others = listOf(earlier("week", 10, 13), earlier("months", 6, 2), earlier("section", 10, 1, section = "3–4각"))
        val repo = Records(current, current, others)
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), FakeAudioPlayer(), now = { 0L }))
        runCurrent()
        assertTrue(model.uiState.value.canCompare)
        model.openComparePick()
        val state = model.uiState.value
        assertTrue(state.comparePickOpen)
        val pick = checkNotNull(state.comparePick)
        assertEquals(listOf("week", "months"), pick.candidates.map { it.id })
        assertEquals(null, pick.selectedId)
        assertEquals(KoreanDateTime(2026, 10, 13, 0, 0).epochDay, pick.initialDay, "처음엔 바로 전 기록의 날")
        model.closeComparePick()
        assertFalse(model.uiState.value.comparePickOpen)
    }

    @Test
    fun sectionRecordCannotOpenCompare() = runTest {
        val section = record(available = true).copy(section = "3–4각")
        val repo = Records(section, section)
        val model = keep(RecordDetailViewModel("41", repo, SampleSongRepository(), FakeAudioPlayer(), now = { 0L }))
        runCurrent()
        model.openComparePick()
        assertFalse(model.uiState.value.canCompare)
        assertFalse(model.uiState.value.comparePickOpen)
    }
}

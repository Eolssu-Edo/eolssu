package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.audio.UnavailableAudioPlayer
import com.ssafy.eolssu.data.song.ModelAudio
import com.ssafy.eolssu.data.song.ModelAudioRepository
import com.ssafy.eolssu.data.song.SheetPlayback
import com.ssafy.eolssu.data.song.audioFixture
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModelAudioSessionTest {
    private val song = smallScoreSong().copy(id = "42", scoreVersion = 1)

    @Test fun firstPlaybackDoesNotAdvanceTheScoreBeforeTheDecoderReportsMovement() = runTest {
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(modelRepository { audioFixture() }, player, backgroundScope, testScheduler.timeSource)
        session.play(song, song.baseBpm)
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 0)
        runCurrent()
        advanceTimeBy(400)
        assertEquals(0.0, session.state.value.anchor!!.positionNow(), "Zero startup reports must not paint future cells")
        player.emit(AudioPlaybackStatus.Playing, 50)
        runCurrent()
        assertEquals(50.0, session.state.value.anchor!!.positionNow())
        advanceTimeBy(100)
        assertEquals(150.0, session.state.value.anchor!!.positionNow(), .001)
    }

    @Test fun latePositionReportsCannotRewindContinuousPlaybackButExplicitSeekCan() = runTest {
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(modelRepository { audioFixture() }, player, backgroundScope, testScheduler.timeSource)
        session.play(song, song.baseBpm)
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 100)
        runCurrent()
        advanceTimeBy(400)
        val painted = session.state.value.anchor!!.positionNow()
        player.emit(AudioPlaybackStatus.Playing, 110)
        runCurrent()
        assertTrue(session.state.value.anchor!!.positionNow() >= painted, "A clock correction is not a user seek")
        player.emit(AudioPlaybackStatus.Playing, 600)
        runCurrent()
        assertEquals(600.0, session.state.value.anchor!!.positionNow(), .001)
        session.seekBy(-1000)
        runCurrent()
        player.emit(AudioPlaybackStatus.Playing, 0)
        runCurrent()
        assertEquals(0.0, session.state.value.anchor!!.positionNow(), "A requested seek is allowed to move backward")
    }

    @Test fun loadingAndMissingAudioNeverPretendToPlayAndRetryUsesNewResponse() = runTest {
        val response = CompletableDeferred<ModelAudio?>()
        var requests = 0
        val repository = modelRepository { requests++; if (requests == 1) response.await() else audioFixture() }
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(repository, player, backgroundScope)
        session.play(song, song.baseBpm)
        runCurrent()
        assertEquals(ModelAudioStatus.Loading, session.state.value.status)
        session.play(song, song.baseBpm)
        assertEquals(1, requests)
        assertEquals(0, player.loads)
        response.complete(null)
        runCurrent()
        assertEquals(ModelAudioStatus.Unavailable, session.state.value.status)
        session.play(song, song.baseBpm)
        runCurrent()
        assertEquals(1, player.loads)
        assertEquals(audioFixture().audioUrl, player.requestedSource?.url)
        assertNull(player.requestedSource?.localFile)
        assertEquals(ModelAudioStatus.Loading, session.state.value.status)
        player.emit(AudioPlaybackStatus.Playing, 350)
        runCurrent()
        assertEquals(ModelAudioStatus.Playing, session.state.value.status)
    }

    @Test fun staleMetadataCannotStartAfterLeavingAndVersionMismatchFailsClosed() = runTest {
        val response = CompletableDeferred<ModelAudio?>()
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(modelRepository { response.await() }, player, backgroundScope)
        session.play(song, 60)
        runCurrent()
        session.suspendAndRelease()
        session.suspendAndRelease() // ON_STOP and disposal may both arrive.
        response.complete(audioFixture())
        runCurrent()
        assertEquals(0, player.loads)
        assertEquals(ModelAudioStatus.Idle, session.state.value.status)
        val mismatch = ModelAudioSession(modelRepository { audioFixture().copy(scoreVersion = 2) }, player, backgroundScope)
        mismatch.play(song, 60)
        runCurrent()
        assertEquals(ModelAudioStatus.Failed, mismatch.state.value.status)
        assertEquals(0, player.loads)
    }

    @Test fun interruptionAndHiddenScreenReleaseResourcesThenResumeFromActualPosition() = runTest {
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(modelRepository { audioFixture() }, player, backgroundScope)
        session.play(song, 60)
        runCurrent()
        player.emit(AudioPlaybackStatus.Interrupted, 1280)
        runCurrent()
        assertEquals(ModelAudioStatus.Interrupted, session.state.value.status)
        session.play(song, 48)
        assertEquals(1280L, player.requestedPosition)
        assertEquals(0.8f, player.requestedSpeed)
        player.emit(AudioPlaybackStatus.Playing, 1400)
        runCurrent()
        session.suspendAndRelease()
        session.suspendAndRelease()
        assertEquals(AudioPlaybackStatus.Idle, player.state.value.status)
        assertEquals(ModelAudioStatus.Paused, session.state.value.status)
        session.play(song, 60)
        assertEquals(1400L, player.requestedPosition)
        session.release()
        assertTrue(player.released)
    }

    @Test fun actualPositionPreservesTicksAcrossRateChangeAndDoesNotStretchTailOrLead() {
        val audio = audioFixture().copy(startOffsetMs = 200, durationMs = 3750)
        val initial = PracticeUiState(song = song, mode = PracticeMode.Model)
        val leading = initial.withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 100))
        assertEquals(0.0, leading.currentTick)
        val progress = initial.withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 1200))
        assertEquals(4.0, progress.currentTick)
        assertEquals("c0-j0-r0-u1", progress.song!!.timeline[progress.position].id)
        val slow = progress.copy(settings = progress.settings.copy(bpmBySong = mapOf(song.id to 48)))
            .withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 1200))
        assertEquals(progress.currentTick, slow.currentTick)
        assertEquals(1250.0, slow.elapsedMs)
        val tail = initial.withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 3650))
        assertEquals(12.0, tail.currentTick)
        assertEquals(100f, tail.progressPercent)
        assertTrue(tail.playing)
    }

    @Test fun personalMixPlaysFromDeviceAndUsesItsGeneratedBpmWithoutLayeringAccompaniment() = runTest {
        val player = FakeAudioPlayer()
        val localFile = LocalAudioFile("/app/files/sheet-audio/42.m4a")
        val audio = audioFixture().copy(audioUrl = "", bpm = 75, sheetPlayback = SheetPlayback(withJangdan = true, localFile = localFile))
        var accompanimentRequests = 0
        val session = ModelAudioSession(modelRepository { audio }, player, backgroundScope,
            prepareAccompaniment = { _, _ -> accompanimentRequests++; false })
        session.play(song, 60)
        runCurrent()
        assertEquals(1, player.loads)
        assertEquals(localFile, player.requestedSource?.localFile)
        assertEquals("", player.requestedSource?.url)
        assertEquals(0.8f, player.requestedSpeed)
        assertEquals(0, accompanimentRequests)
    }

    @Test fun missingOptionalAccompanimentDoesNotPreventPlayingDownloadedPersonalAudio() = runTest {
        val localFile = LocalAudioFile("/app/files/sheet-audio/42.m4a")
        val audio = audioFixture().copy(audioUrl = "", sheetPlayback = SheetPlayback(withJangdan = false, localFile = localFile))
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(modelRepository { audio }, player, backgroundScope, prepareAccompaniment = { _, _ -> false })
        session.play(song, song.baseBpm)
        runCurrent()
        assertEquals(localFile, player.requestedSource?.localFile)
        player.emit(AudioPlaybackStatus.Playing, 500)
        runCurrent()
        assertEquals(ModelAudioStatus.Playing, session.state.value.status)
    }

    @Test fun unsupportedAndTruncatedFileAreNotSuccessfulPlayback() = runTest {
        val unavailable = ModelAudioSession(modelRepository { audioFixture() }, UnavailableAudioPlayer(), backgroundScope)
        unavailable.play(song, 60)
        assertEquals(ModelAudioStatus.Unsupported, unavailable.state.value.status)
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(modelRepository { audioFixture() }, player, backgroundScope)
        session.play(song, 60)
        runCurrent()
        player.emit(AudioPlaybackStatus.Completed, 1000)
        runCurrent()
        assertEquals(ModelAudioStatus.Failed, session.state.value.status)
        val state = PracticeUiState(song = song, mode = PracticeMode.Model).withModelAudio(session.state.value)
        assertFalse(state.playing)
        assertTrue(state.progressPercent < 100f)
    }
}

internal fun modelRepository(block: suspend () -> ModelAudio?) = object : ModelAudioRepository {
    override suspend fun get(song: com.ssafy.eolssu.core.model.Song): ModelAudio? = block()
}

internal class FakeAudioPlayer : AudioPlayer {
    override val available = true
    override val state = MutableStateFlow(AudioPlaybackState())
    var loads = 0
    var requestedPosition = 0L
    var requestedSpeed = 1f
    var requestedSource: AudioSource? = null
    var released = false
    override fun load(source: AudioSource, positionMs: Long, speed: Float) {
        loads++
        requestedSource = source
        requestedPosition = positionMs
        requestedSpeed = speed
        state.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading, positionMs, 3550)
    }
    fun emit(status: AudioPlaybackStatus, positionMs: Long) { state.value = state.value.copy(status = status, positionMs = positionMs) }
    override fun resume(speed: Float) { requestedSpeed = speed; emit(AudioPlaybackStatus.Playing, state.value.positionMs) }
    override fun pause() { emit(AudioPlaybackStatus.Paused, state.value.positionMs) }
    override fun setSpeed(speed: Float) { requestedSpeed = speed }
    override fun stop() { state.value = AudioPlaybackState() }
    override fun release() { stop(); released = true }
}

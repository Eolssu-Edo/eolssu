package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.SourceHash
import com.ssafy.eolssu.data.song.previewAudio
import com.ssafy.eolssu.data.song.previewJson
import com.ssafy.eolssu.data.song.previewSong
import com.ssafy.eolssu.data.song.withPerformance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModelPerformanceTest {
    private val song = previewSong()
    private val audio = previewAudio().withPerformance(previewJson(), song, SourceHash)

    @Test fun writtenOrnamentsAreVisibleWithoutModelAudioAndOnlyAtTheEventStart() {
        val initial = PracticeUiState(song = song)
        val written = mapOf(
            ScoreUnitKey("c0-j0", 0, 0) to listOf("늘임표"),
            ScoreUnitKey("c0-j0", 0, 1) to listOf("니레", "노라느니"),
        )
        for (mode in PracticeMode.entries) {
            assertEquals(written, initial.copy(mode = mode).scoreAnnotations, mode.name)
        }
        val model = initial.copy(mode = PracticeMode.Model)
            .withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 250))
        assertEquals(listOf("노라느니"), model.scoreAnnotations[ScoreUnitKey("c0-j1", 0, 0)])
        assertEquals(written, model.copy(mode = PracticeMode.Recording).scoreAnnotations)
        assertTrue(PracticeUiState().scoreAnnotations.isEmpty())
    }

    @Test fun stretchedAndRepeatedMediaUsesWrittenCursorButPerformedProgress() {
        val initial = PracticeUiState(song = song, mode = PracticeMode.Model)
        val stretched = initial.withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 250))
        assertEquals(0.5, stretched.currentTick)
        assertEquals(7, stretched.totalSeconds)
        assertEquals(250f / 6500 * 100, stretched.progressPercent, 0.00001f)
        val repeated = initial.withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 3500))
        assertEquals(0.5, repeated.currentTick)
        assertEquals(0, repeated.position)
        assertEquals(3500.0, repeated.elapsedMs)
        assertTrue(repeated.progressPercent > 50f)
        val slower = repeated.copy(settings = repeated.settings.copy(bpmBySong = mapOf(song.id to 30)))
            .withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 3500))
        assertEquals(0.5, slower.currentTick)
        assertEquals(7000.0, slower.elapsedMs)
        assertEquals(13, slower.totalSeconds)
        assertEquals(repeated.progressPercent, slower.progressPercent)
        val tail = initial.withModelAudio(ModelAudioState(ModelAudioStatus.Completed, audio, 7050))
        assertEquals(6500.0, tail.elapsedMs)
        assertEquals(12.0, tail.currentTick)
        assertEquals(100f, tail.progressPercent)
    }

    @Test fun recordingAndScoringKeepOriginalTimeEvenAfterPreview() {
        val preview = PracticeUiState(song = song, mode = PracticeMode.Model)
            .withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 3500))
        val recording = preview.copy(mode = PracticeMode.Recording, elapsedMs = 0.0, eventElapsedMs = 0.0, pausedFraction = 0f)
        assertEquals(3, recording.totalSeconds)
        assertEquals(250.0, recording.eventDurationMs(0))
        assertEquals(0f, recording.progressPercent)
        assertEquals(recording, recording.withModelAudio(ModelAudioState(ModelAudioStatus.Playing, audio, 6000)))
        assertEquals(listOf(0L, 1L, 8L), song.timeline.map { it.startTick })
        assertEquals(listOf(0, 1, null), song.timeline.map { it.noteIndex })
    }

    @Test fun sessionRejectsTruncationAgainstPerformedDurationAndReleasesNormally() = runTest {
        val player = FakeAudioPlayer()
        val session = ModelAudioSession(modelRepository { audio }, player, backgroundScope)
        session.play(song, 60)
        runCurrent()
        assertEquals(1, player.loads)
        player.emit(AudioPlaybackStatus.Completed, 3500) // Written score ended, performed score did not.
        runCurrent()
        assertEquals(ModelAudioStatus.Failed, session.state.value.status)
        assertFalse(PracticeUiState(song = song, mode = PracticeMode.Model).withModelAudio(session.state.value).playing)
        session.release()
        assertTrue(player.released)
    }

    @Test fun boardSeekUsesPerformedEndAndKeepsWrittenFallback() = runTest {
        for ((source, expectedEnd) in listOf(audio to 6500L, previewAudio() to 3000L)) {
            val player = FakeAudioPlayer()
            val session = ModelAudioSession(modelRepository { source }, player, backgroundScope)
            session.play(song, 60)
            runCurrent()
            player.emit(AudioPlaybackStatus.Playing, 1000)
            runCurrent()
            session.seekBy(10000)
            assertEquals(expectedEnd, player.requestedPosition)
            player.emit(AudioPlaybackStatus.Paused, expectedEnd)
            runCurrent()
            session.seekBy(-500)
            assertEquals(ModelAudioStatus.Paused, session.state.value.status)
            assertEquals(expectedEnd - 500, session.state.value.mediaPositionMs)
            session.seekBy(-10000)
            assertEquals(0L, session.state.value.mediaPositionMs)
            session.release()
        }
    }
}

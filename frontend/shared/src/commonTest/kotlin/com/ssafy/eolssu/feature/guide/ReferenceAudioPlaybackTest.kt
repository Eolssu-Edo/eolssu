package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.audio.AudioPlaybackState
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.data.song.ReferenceAudioClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceAudioPlaybackTest {
    private val clip = ReferenceAudioClip(
        assetId = "a".repeat(64), datasetId = "danso", labelId = "fixture",
        category = "기본음계", technique = "평취 강", register = "평취", yulmyeong = "중",
        displayLabel = "평취 중", durationMs = 5_000, review = "inferred",
        soundUrl = "https://audio.example/api/reference-audios/" + "a".repeat(64) + "/play",
    )

    @Test fun tappingTheSoundingOrPreparingExampleAgainStopsIt() {
        for (status in listOf(AudioPlaybackStatus.Loading, AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused)) {
            val player = RecordingPlayer(AudioPlaybackState(clip.assetId, status, 2_125, 5_000))
            toggleReferenceAudio(player, clip)
            assertEquals(AudioPlaybackState(), player.state.value)
            assertEquals(listOf("stop"), player.calls)
        }
    }

    @Test fun selectingAnotherClipReplacesThePreviousSourceFromTheBeginning() {
        for (status in listOf(AudioPlaybackStatus.Loading, AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused)) {
            val player = RecordingPlayer(AudioPlaybackState("b".repeat(64), status, 2_125, 5_000))
            toggleReferenceAudio(player, clip)
            assertEquals(AudioPlaybackState(clip.assetId, AudioPlaybackStatus.Loading, 0), player.state.value)
            assertEquals(listOf("load:${clip.assetId}:0"), player.calls)
            assertEquals(AudioSource(clip.assetId, clip.soundUrl), player.loaded)
        }
    }

    @Test fun completedAndFailedPlaybackStartFreshThroughTheApi() {
        for (status in listOf(AudioPlaybackStatus.Completed, AudioPlaybackStatus.Failed, AudioPlaybackStatus.Interrupted)) {
            val player = RecordingPlayer(AudioPlaybackState(clip.assetId, status, 5_000, 5_000))
            toggleReferenceAudio(player, clip)
            assertEquals(AudioPlaybackState(clip.assetId, AudioPlaybackStatus.Loading, 0), player.state.value)
            assertEquals(AudioSource(clip.assetId, clip.soundUrl), player.loaded)
            assertEquals(listOf("load:${clip.assetId}:0"), player.calls)
        }
    }

    private class RecordingPlayer(initial: AudioPlaybackState) : AudioPlayer {
        override val available = true
        override val state = MutableStateFlow(initial)
        val calls = mutableListOf<String>()
        var loaded: AudioSource? = null
        override fun load(source: AudioSource, positionMs: Long, speed: Float) {
            calls += "load:${source.id}:$positionMs"
            loaded = source
            state.value = AudioPlaybackState(source.id, AudioPlaybackStatus.Loading, positionMs)
        }
        override fun pause() { calls += "pause"; state.value = state.value.copy(status = AudioPlaybackStatus.Paused) }
        override fun resume(speed: Float) { calls += "resume"; state.value = state.value.copy(status = AudioPlaybackStatus.Playing) }
        override fun stop() { calls += "stop"; state.value = AudioPlaybackState() }
        override fun setSpeed(speed: Float) = Unit
        override fun release() = stop()
    }
}

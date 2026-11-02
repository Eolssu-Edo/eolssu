package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.data.song.ReferenceAudioClip

internal fun toggleReferenceAudio(player: AudioPlayer, clip: ReferenceAudioClip) {
    val current = player.state.value
    if (current.sourceId == clip.assetId) {
        when (current.status) {
            AudioPlaybackStatus.Playing -> { player.pause(); return }
            AudioPlaybackStatus.Paused -> { player.resume(1f); return }
            AudioPlaybackStatus.Loading -> { player.stop(); return }
            else -> Unit
        }
    }
    // A different clip, replay or retry starts a fresh /play request, never a cached signed URL.
    player.load(AudioSource(clip.assetId, clip.soundUrl), 0, 1f)
}

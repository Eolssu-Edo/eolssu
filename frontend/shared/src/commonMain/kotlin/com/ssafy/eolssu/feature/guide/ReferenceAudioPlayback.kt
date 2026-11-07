package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.data.song.ReferenceAudioClip

/** 같은 예시음을 다시 누르면 멈추고, 그 밖에는 처음부터 새로 재생한다. */
internal fun toggleReferenceAudio(player: AudioPlayer, clip: ReferenceAudioClip) {
    val current = player.state.value
    if (current.sourceId == clip.assetId && current.status in setOf(
            AudioPlaybackStatus.Loading, AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused)) {
        player.stop()
        return
    }
    // A different clip, replay or retry starts a fresh /play request, never a cached signed URL.
    player.load(AudioSource(clip.assetId, clip.soundUrl), 0, 1f)
}

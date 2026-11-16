package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
import com.ssafy.eolssu.core.audio.AudioPlayer
import com.ssafy.eolssu.core.audio.AudioSource
import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.data.song.ReferenceAudioClip

/**
 * 같은 예시음을 다시 누르면 멈추고, 그 밖에는 처음부터 새로 재생한다.
 * [localFile]이 있으면 기기에 받아 둔 파일로 바로 재생한다(S15P31E102-1005).
 */
internal fun toggleReferenceAudio(player: AudioPlayer, clip: ReferenceAudioClip, localFile: LocalAudioFile? = null) {
    val current = player.state.value
    if (current.sourceId == clip.assetId && current.status in setOf(
            AudioPlaybackStatus.Loading, AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused)) {
        player.stop()
        return
    }
    // Without a verified local file, a different clip, replay or retry starts a fresh /play request, never a cached signed URL.
    val source = if (localFile != null) AudioSource(clip.assetId, "", localFile) else AudioSource(clip.assetId, clip.soundUrl)
    player.load(source, 0, 1f)
}

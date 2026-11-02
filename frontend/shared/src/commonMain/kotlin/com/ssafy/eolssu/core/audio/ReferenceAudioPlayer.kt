package com.ssafy.eolssu.core.audio

import androidx.compose.runtime.Composable

/** Player owned by the reference-audio dialog, independent of local animation sounds. */
@Composable
expect fun rememberReferenceAudioPlayer(): ReferenceAudioPlayer

interface ReferenceAudioPlayer : AudioPlayer {
    /**
     * Seeks Playing/Paused/Completed media within its decoded duration without reloading it.
     * Playing/Paused are preserved; Completed becomes Paused until an explicit resume.
     */
    fun seekTo(positionMs: Long)
}

internal fun AudioPlaybackState.referenceSeekTarget(positionMs: Long): AudioPlaybackState? {
    if (sourceId == null || durationMs <= 0 || status !in setOf(
            AudioPlaybackStatus.Playing, AudioPlaybackStatus.Paused, AudioPlaybackStatus.Completed,
        )) return null
    return copy(
        positionMs = positionMs.coerceIn(0, durationMs),
        status = if (status == AudioPlaybackStatus.Completed) AudioPlaybackStatus.Paused else status,
    )
}

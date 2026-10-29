package com.ssafy.eolssu.backend.feature.song.domain

import java.net.URI

/** File duration includes the encoded tail; startOffsetMs aligns score zero to media time. */
data class ModelAudio(
    val songId: Long,
    val scoreVersion: Int,
    val audioUrl: String,
    val bpm: Int,
    val bpmUnit: String,
    val durationMs: Int,
    val startOffsetMs: Int,
    val contentType: String,
    val sha256: String,
) {
    fun hasValidMetadata(): Boolean {
        val uri = runCatching { URI(audioUrl) }.getOrNull() ?: return false
        return songId > 0 && scoreVersion > 0 && bpm in 30..400 &&
            bpmUnit in setOf("QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT") &&
            durationMs > 0 && startOffsetMs in 0 until durationMs &&
            contentType in setOf("audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav") &&
            sha256.matches(Regex("[0-9a-f]{64}")) && uri.scheme == "https" &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null
    }
}

interface ModelAudioRepository {
    /** Only published, undeleted songs and published audio matching the current score and tempo. */
    fun findCurrent(songId: Long): ModelAudio?
}

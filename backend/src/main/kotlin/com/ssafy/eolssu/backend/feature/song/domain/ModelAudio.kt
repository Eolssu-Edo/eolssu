package com.ssafy.eolssu.backend.feature.song.domain

/** File duration includes the encoded tail; startOffsetMs aligns score zero to media time. */
data class ModelAudio(
    val songId: Long,
    val scoreVersion: Int,
    val objectKey: String,
    val bpm: Int,
    val bpmUnit: String,
    val durationMs: Int,
    val startOffsetMs: Int,
    val contentType: String,
    val sha256: String,
    val performanceObjectKey: String? = null,
    val sourceCommonSha256: String? = null,
) {
    fun hasValidMetadata(): Boolean {
        return songId > 0 && scoreVersion > 0 && bpm in 30..400 &&
            bpmUnit in setOf("QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT") &&
            durationMs > 0 && startOffsetMs in 0 until durationMs &&
            contentType in setOf("audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav") &&
            sha256.matches(HASH) && AUDIO_KEY.matches(objectKey) &&
            ((performanceObjectKey == null && sourceCommonSha256 == null) ||
                (performanceObjectKey == objectKey.substringBeforeLast('/') + "/performance.common.json" &&
                    sourceCommonSha256?.matches(HASH) == true))
    }

    companion object {
        private val HASH = Regex("[0-9a-f]{64}")
        private val AUDIO_KEY = Regex("model-audio/drafts/[0-9a-f]{64}/cases/(danso|sogeum|jeongak-daegeum|sanjo-daegeum)-[0-9]{3}-p[0-9]+/\\1\\.m4a")
    }
}

interface ModelAudioRepository {
    /** Only published, undeleted songs and published audio matching the current score and tempo. */
    fun findCurrent(songId: Long): ModelAudio?
}

interface ModelAudioFiles {
    fun playbackUrl(audio: ModelAudio): String
    fun performance(audio: ModelAudio): ByteArray
}

/** Storage failures deliberately carry no signed address or provider exception. */
class ModelAudioUnavailable : RuntimeException("Model audio storage is unavailable")

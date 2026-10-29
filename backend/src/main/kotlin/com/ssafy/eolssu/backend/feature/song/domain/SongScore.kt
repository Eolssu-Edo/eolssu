package com.ssafy.eolssu.backend.feature.song.domain

/** Stored score and lookup metadata. JSON remains authoritative; the timeline is an additive view. */
data class PublishedSongScore(
    val songId: Long,
    val scoreVersion: Int,
    val noteCount: Int,
    val defaultBpm: Int,
    val bpmUnit: String,
    val scoreJson: String,
)

data class ScorePitch(
    val id: Long, val yul: String, val octave: Int, val semitone: Int,
    val hanja: String, val hangul: String,
)

interface SongScoreRepository {
    fun findPublished(songId: Long): PublishedSongScore?
    fun pitches(): List<ScorePitch>
}

interface SongScoreDecoder {
    fun decode(score: PublishedSongScore, pitches: List<ScorePitch>): DecodedSongScore
}

class InvalidStoredScore : RuntimeException()

data class DecodedSongScore(
    val schemaVersion: Int,
    val events: List<ScoreTimelineEvent>,
    val notationCapabilities: ScoreNotationCapabilities,
)

data class ScoreTimelineEvent(
    val eventId: String,
    val kind: String,
    val noteIdx: Int?,
    val pitchId: String?,
    val columnIdx: Int,
    val cellIdx: Int,
    val subIdx: Int,
    val startTick: Int,
    val durationTick: Int,
    val onsetBeat: Double,
    val durationBeat: Double,
)

/** solfege is not declared supported until a server/client notation contract is established. */
data class ScoreNotationCapabilities(val hanja: Boolean, val hangul: Boolean, val solfege: Boolean = false)

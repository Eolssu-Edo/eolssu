package com.ssafy.eolssu.backend.feature.song.domain

/** Stored score and lookup metadata. JSON remains authoritative; the timeline is an additive view. */
data class PublishedSongScore(
    val songId: Long,
    val scoreVersion: Int,
    val noteCount: Int,
    val defaultBpm: Int,
    val bpmUnit: String,
    val scoreJson: String,
    /** Optional slot positions of event sigimsae (V46). Invalid or stale values are dropped, not fatal. */
    val sigimsaePlacementsJson: String? = null,
)

data class ScorePitch(
    val id: Long, val yul: String, val octave: Int, val semitone: Int,
    val hanja: String, val hangul: String,
)

/** 카탈로그 확인이 필요한 인식 음. 편집 초안에서 원래 위치와 율명을 알려 준다. */
data class UnregisteredScorePitch(val eventId: String, val cellId: String, val yul: String, val octave: Int)

interface SongScoreRepository {
    fun findPublished(songId: Long): PublishedSongScore?
    fun findPublishedRevision(songId: Long, scoreVersion: Int): PublishedSongScore? =
        findPublished(songId)?.takeIf { it.scoreVersion == scoreVersion }
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
    val sigimsaePlacements: List<SigimsaePlacement>? = null,
)

/** One display slot's sigimsae. Per event, the slots' symbols in display order equal the event's sigimsae. */
data class SigimsaePlacement(val cellId: String, val row: Int, val col: Int, val symbols: List<String>)

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

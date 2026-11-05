package com.ssafy.eolssu.backend.feature.song.application

import com.ssafy.eolssu.backend.feature.song.domain.*

class GetSongScore(private val repository: SongScoreRepository, private val decoder: SongScoreDecoder,
    private val jangdanPlayback: SongJangdanPlaybackResolver = SongJangdanPlaybackResolver { null }) {
    fun get(songId: Long): SongScoreLookup {
        val stored = repository.findPublished(songId) ?: return SongScoreLookup.NotFound
        return try {
            val decoded = decoder.decode(stored, repository.pitches())
            SongScoreLookup.Found(stored, decoded, jangdanPlayback.resolve(stored))
        } catch (_: InvalidStoredScore) {
            SongScoreLookup.Conflict
        }
    }
}

sealed interface SongScoreLookup {
    data class Found(val stored: PublishedSongScore, val decoded: DecodedSongScore,
        val jangdanPlayback: ScoreJangdanPlayback? = null) : SongScoreLookup
    data object NotFound : SongScoreLookup
    data object Conflict : SongScoreLookup
}

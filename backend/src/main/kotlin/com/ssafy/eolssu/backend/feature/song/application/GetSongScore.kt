package com.ssafy.eolssu.backend.feature.song.application

import com.ssafy.eolssu.backend.feature.song.domain.*

class GetSongScore(private val repository: SongScoreRepository, private val decoder: SongScoreDecoder) {
    fun get(songId: Long): SongScoreLookup {
        val stored = repository.findPublished(songId) ?: return SongScoreLookup.NotFound
        return try {
            SongScoreLookup.Found(stored, decoder.decode(stored, repository.pitches()))
        } catch (_: InvalidStoredScore) {
            SongScoreLookup.Conflict
        }
    }
}

sealed interface SongScoreLookup {
    data class Found(val stored: PublishedSongScore, val decoded: DecodedSongScore) : SongScoreLookup
    data object NotFound : SongScoreLookup
    data object Conflict : SongScoreLookup
}

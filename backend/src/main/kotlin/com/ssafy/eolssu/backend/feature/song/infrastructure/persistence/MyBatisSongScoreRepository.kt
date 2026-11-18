package com.ssafy.eolssu.backend.feature.song.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.song.domain.*

class MyBatisSongScoreRepository(private val mapper: SongScoreMapper) : SongScoreRepository {
    override fun findPublished(songId: Long): PublishedSongScore? = mapper.findPublished(songId)?.let {
        PublishedSongScore(it.songId, it.scoreVersion, it.noteCount, it.defaultBpm, it.bpmUnit, it.scoreJson, it.sigimsaePlacementsJson)
    }

    override fun findPublishedRevision(songId: Long, scoreVersion: Int): PublishedSongScore? =
        mapper.findPublishedRevision(songId, scoreVersion)?.let {
            PublishedSongScore(it.songId, it.scoreVersion, it.noteCount, it.defaultBpm, it.bpmUnit, it.scoreJson, it.sigimsaePlacementsJson)
        }

    override fun pitches(): List<ScorePitch> = mapper.findPitches().map {
        ScorePitch(it.id, it.yul, it.octave.toInt(), it.semitone.toInt(), it.hanja, it.hangul)
    }
}

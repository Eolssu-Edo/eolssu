package com.ssafy.eolssu.backend.feature.song.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence.PitchRow
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface SongScoreMapper {
    fun findPublished(@Param("songId") songId: Long): PublishedScoreRow?
    fun findPitches(): List<PitchRow>
}

data class PublishedScoreRow(
    val songId: Long, val scoreVersion: Int, val noteCount: Int,
    val defaultBpm: Int, val bpmUnit: String, val scoreJson: String,
)

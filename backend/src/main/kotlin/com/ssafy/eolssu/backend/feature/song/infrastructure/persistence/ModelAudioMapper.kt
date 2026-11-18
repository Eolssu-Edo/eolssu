package com.ssafy.eolssu.backend.feature.song.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioRepository
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

data class ModelAudioRow(
    val songId: Long, val scoreVersion: Int, val objectKey: String, val bpm: Short, val bpmUnit: String,
    val durationMs: Int, val startOffsetMs: Int, val contentType: String, val sha256: String,
    val performanceObjectKey: String?, val sourceCommonSha256: String?,
)

@Mapper
interface ModelAudioMapper {
    fun findCurrent(@Param("songId") songId: Long): ModelAudioRow?
}

class MyBatisModelAudioRepository(private val mapper: ModelAudioMapper) : ModelAudioRepository {
    override fun findCurrent(songId: Long): ModelAudio? = mapper.findCurrent(songId)?.let {
        ModelAudio(it.songId, it.scoreVersion, it.objectKey, it.bpm.toInt(), it.bpmUnit,
            it.durationMs, it.startOffsetMs, it.contentType, it.sha256, it.performanceObjectKey, it.sourceCommonSha256)
    }
}

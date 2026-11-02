package com.ssafy.eolssu.backend.feature.catalog.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.catalog.domain.ReferenceAudioAsset
import com.ssafy.eolssu.backend.feature.catalog.domain.ReferenceAudioFilter
import com.ssafy.eolssu.backend.feature.catalog.domain.ReferenceAudioRepository
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface ReferenceAudioMapper {
    fun findPublished(@Param("filter") filter: ReferenceAudioFilter): List<ReferenceAudioAsset>
    fun findPublishedById(@Param("assetId") assetId: String): ReferenceAudioAsset?
}

class MyBatisReferenceAudioRepository(private val mapper: ReferenceAudioMapper) : ReferenceAudioRepository {
    override fun published(filter: ReferenceAudioFilter) = mapper.findPublished(filter)
    override fun published(assetId: String) = mapper.findPublishedById(assetId)
}

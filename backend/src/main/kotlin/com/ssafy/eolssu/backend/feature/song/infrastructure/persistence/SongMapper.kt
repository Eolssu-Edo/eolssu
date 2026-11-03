package com.ssafy.eolssu.backend.feature.song.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface SongMapper {
    fun listPublished(@Param("instrumentId") instrumentId: Long? = null): List<SongRow>
    fun findPublished(@Param("id") id: Long): SongRow?

    fun findById(@Param("id") id: Long): SongRow?
}

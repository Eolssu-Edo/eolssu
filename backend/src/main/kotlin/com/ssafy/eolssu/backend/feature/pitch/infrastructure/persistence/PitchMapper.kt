package com.ssafy.eolssu.backend.feature.pitch.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PitchMapper {
    fun findById(@Param("id") id: Long): PitchRow?
}

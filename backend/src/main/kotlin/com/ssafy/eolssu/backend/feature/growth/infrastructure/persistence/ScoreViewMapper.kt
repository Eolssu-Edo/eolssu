package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface ScoreViewMapper {
    fun findById(@Param("id") id: Long): ScoreViewRow?
}

package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface RefreshTokenMapper {
    fun findById(@Param("id") id: Long): RefreshTokenRow?
}

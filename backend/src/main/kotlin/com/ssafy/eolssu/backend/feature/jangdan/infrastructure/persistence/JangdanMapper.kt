package com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface JangdanMapper {
    fun listAll(): List<JangdanRow>
    fun findById(@Param("id") id: Long): JangdanRow?
}

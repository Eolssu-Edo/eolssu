package com.ssafy.eolssu.backend.feature.notation.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface NotationSymbolMapper {
    fun listAll(): List<NotationSymbolRow>
    fun findById(@Param("id") id: Long): NotationSymbolRow?
}

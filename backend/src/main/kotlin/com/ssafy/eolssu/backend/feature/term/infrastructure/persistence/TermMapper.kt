package com.ssafy.eolssu.backend.feature.term.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface TermMapper {
    fun findById(@Param("id") id: Long): TermRow?
}

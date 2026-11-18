package com.ssafy.eolssu.backend.feature.term.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface TermMapper {
    fun findById(@Param("id") id: Long): TermRow?
    fun findCurrent(@Param("asOf") asOf: LocalDateTime): List<TermRow>
}

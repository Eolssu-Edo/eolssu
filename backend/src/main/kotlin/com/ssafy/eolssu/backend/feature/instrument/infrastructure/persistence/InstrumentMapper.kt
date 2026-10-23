package com.ssafy.eolssu.backend.feature.instrument.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface InstrumentMapper {
    fun findById(@Param("id") id: Long): InstrumentRow?
}

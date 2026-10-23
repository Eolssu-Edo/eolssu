package com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface FingeringChartMapper {
    fun findById(@Param("id") id: Long): FingeringChartRow?
}

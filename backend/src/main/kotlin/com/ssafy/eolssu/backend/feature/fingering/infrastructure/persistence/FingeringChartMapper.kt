package com.ssafy.eolssu.backend.feature.fingering.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface FingeringChartMapper {
    fun listForInstrument(@Param("instrumentId") instrumentId: Long, @Param("pitchId") pitchId: Long?): List<FingeringChartRow>
    fun findById(@Param("id") id: Long): FingeringChartRow?
}

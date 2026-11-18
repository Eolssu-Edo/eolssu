package com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface EvaluationMapper {
    fun findById(@Param("id") id: Long): EvaluationRow?
}

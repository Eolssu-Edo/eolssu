package com.ssafy.eolssu.backend.feature.term.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface UserTermAgreementMapper {
    fun findById(@Param("id") id: Long): UserTermAgreementRow?
}

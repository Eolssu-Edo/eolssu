package com.ssafy.eolssu.backend.feature.term.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface UserTermAgreementMapper {
    fun findById(@Param("id") id: Long): UserTermAgreementRow?
    fun insertAll(@Param("userId") userId: Long, @Param("agreements") agreements: List<UserTermAgreementInsert>, @Param("agreedAt") agreedAt: LocalDateTime): Int
}

class UserTermAgreementInsert(val termId: Long, val agreed: Boolean)

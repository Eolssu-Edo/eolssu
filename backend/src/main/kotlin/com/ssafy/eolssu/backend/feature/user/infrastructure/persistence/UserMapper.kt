package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface UserMapper {
    fun findById(@Param("id") id: Long): UserRow?
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insert(user: UserInsert): Int
    fun findSignInMemberForUpdate(@Param("email") email: String): UserRow?
    fun cancelWithdrawal(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
    fun recordLogin(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
}

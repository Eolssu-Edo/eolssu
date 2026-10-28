package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Param

@Mapper
interface UserMapper {
    fun findById(@Param("id") id: Long): UserRow?
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insert(user: UserInsert): Int
}

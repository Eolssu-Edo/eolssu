package com.ssafy.eolssu.backend.feature.user.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface UserMapper {
    fun findById(@Param("id") id: Long): UserRow?
}

package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface LessonProgressMapper {
    fun findById(@Param("id") id: Long): LessonProgressRow?
}

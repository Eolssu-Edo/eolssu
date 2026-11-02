package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface LessonMapper {
    fun findById(@Param("id") id: Long): LessonRow?

    /** 트랙의 공개 단계와 회원 완료 시각. (track, seq)·(user_id, lesson_id) 유일 키로 정렬 없이 읽는다. */
    fun findPublishedSteps(@Param("userId") userId: Long, @Param("track") track: String): List<LessonStepRow>

    fun findPublishedDetail(@Param("userId") userId: Long, @Param("lessonId") lessonId: Long): LessonDetailRow?
}

package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface LessonMapper {
    fun findById(@Param("id") id: Long): LessonRow?

    /** 트랙의 공개 단계와 회원 완료 시각. (track, seq)·(user_id, lesson_id) 유일 키로 정렬 없이 읽는다. */
    fun findPublishedSteps(@Param("userId") userId: Long, @Param("track") track: String): List<LessonStepRow>

    fun findPublishedStep(@Param("userId") userId: Long, @Param("lessonId") lessonId: Long): LessonStepRow?

    fun findPublishedDetail(@Param("userId") userId: Long, @Param("lessonId") lessonId: Long): LessonDetailRow?

    /** 회원+단계 유일 키에 걸리면 기존 행(처음 완료 시각)을 그대로 둔다. */
    fun insertCompletionIfAbsent(
        @Param("userId") userId: Long,
        @Param("lessonId") lessonId: Long,
        @Param("completedAt") completedAt: LocalDateTime,
    ): Int

    fun deleteCompletion(@Param("userId") userId: Long, @Param("lessonId") lessonId: Long): Int
}

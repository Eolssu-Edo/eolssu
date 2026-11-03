package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import java.time.LocalDateTime

data class LessonProgressRow(
    val id: Long,
    val userId: Long,
    val lessonId: Long,
    val completedAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

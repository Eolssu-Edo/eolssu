package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import java.time.LocalDateTime

data class LessonRow(
    val id: Long,
    val kind: String,
    val instrumentId: Long?,
    val seq: Short,
    val title: String,
    val contentJson: String,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

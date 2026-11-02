package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import java.time.LocalDateTime

data class LessonRow(
    val id: Long,
    val track: String,
    val seq: Short,
    val title: String,
    val judgeType: String,
    val isOptional: Boolean,
    val contentJson: String,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

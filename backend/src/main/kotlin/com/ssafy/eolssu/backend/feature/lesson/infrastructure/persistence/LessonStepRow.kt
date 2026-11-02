package com.ssafy.eolssu.backend.feature.lesson.infrastructure.persistence

import java.time.LocalDateTime

/** 공개 단계와 회원 완료 시각(UTC). 완료하지 않았으면 completedAt이 null이다. */
data class LessonStepRow(
    val id: Long,
    val track: String,
    val seq: Short,
    val title: String,
    val judgeType: String,
    val isOptional: Boolean,
    val completedAt: LocalDateTime?,
)

data class LessonDetailRow(
    val id: Long,
    val track: String,
    val seq: Short,
    val title: String,
    val judgeType: String,
    val isOptional: Boolean,
    val contentJson: String,
    val completedAt: LocalDateTime?,
)

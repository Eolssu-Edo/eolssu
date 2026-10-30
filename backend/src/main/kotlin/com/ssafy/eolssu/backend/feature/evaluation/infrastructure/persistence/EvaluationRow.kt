package com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence

import java.time.LocalDateTime

data class EvaluationRow(
    val id: Long,
    val sessionId: Long,
    val totalScore: Short,
    val pitchScore: Short,
    val timingScore: Short,
    val durationScore: Short,
    val modelVersion: String,
    val evaluatedAt: LocalDateTime,
    val tuningOffsetCent: Double?,
    val tuningOutOfRange: Boolean?,
)

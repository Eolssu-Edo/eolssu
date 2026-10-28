package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import java.time.LocalDateTime

data class PracticeSessionRow(
    val id: Long,
    val userId: Long,
    val songId: Long,
    val missionId: Long?,
    val parentSessionId: Long?,
    val scoreVersion: Int,
    val bpm: Short,
    val bpmUnit: String,
    val idempotencyKey: String?,
    val requestHash: String?,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val recordingUrl: String?,
    val recordingBytes: Int?,
    val recordingDurationMs: Int?,
    val recordingSampleRate: Int?,
    val recordingChannels: Short?,
    val status: String,
    val failReason: String?,
    val analysisAttempt: Int,
    val analysisStartedAt: LocalDateTime?,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val savedAt: LocalDateTime?,
    val memo: String?,
    val viewedAt: LocalDateTime?,
    val recordDeletedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)

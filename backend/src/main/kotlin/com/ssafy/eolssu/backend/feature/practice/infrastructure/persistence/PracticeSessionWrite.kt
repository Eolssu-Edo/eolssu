package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import java.time.LocalDateTime

class PracticeSessionInsert(
    val userId: Long,
    val songId: Long,
    val parentSessionId: Long?,
    val scoreVersion: Int,
    val bpm: Int,
    val bpmUnit: String,
    val idempotencyKey: String,
    val requestHash: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val startedAt: LocalDateTime,
) {
    var id: Long = 0
}

data class PlayableSongRow(val id: Long, val bpmUnit: String, val noteCount: Int, val scoreVersion: Int)

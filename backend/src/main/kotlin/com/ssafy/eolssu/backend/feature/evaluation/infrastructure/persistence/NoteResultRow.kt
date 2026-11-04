package com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence

data class NoteResultRow(
    val id: Long,
    val evaluationId: Long,
    val noteIdx: Int,
    val expectedPitchId: Long,
    val detectedPitchId: Long?,
    val pitchErrorCent: Short?,
    val onsetErrorMs: Int?,
    val durationErrorMs: Int?,
    val verdict: String,
    val recordingOffsetMs: Int?,
)

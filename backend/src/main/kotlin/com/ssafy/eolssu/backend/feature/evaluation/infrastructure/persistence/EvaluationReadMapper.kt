package com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface EvaluationReadMapper {
    fun findOwned(@Param("sessionId") sessionId: Long, @Param("userId") userId: Long): EvaluationReadRow?
    fun notes(@Param("evaluationId") evaluationId: Long): List<NoteResultRow>
}

data class EvaluationReadRow(val sessionId: Long, val songId: Long, val scoreVersion: Int, val bpm: Int, val bpmUnit: String,
    val status: String, val startedAt: LocalDateTime, val endedAt: LocalDateTime?, val evaluationId: Long?,
    val totalScore: Int?, val pitchScore: Int?, val timingScore: Int?, val durationScore: Int?, val evaluatedAt: LocalDateTime?, val recordingDurationMs: Int?,
    val tuningOffsetCent: Double?, val tuningOutOfRange: Boolean?)

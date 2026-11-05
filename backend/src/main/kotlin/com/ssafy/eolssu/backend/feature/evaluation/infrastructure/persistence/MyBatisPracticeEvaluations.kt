package com.ssafy.eolssu.backend.feature.evaluation.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluationNote
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluationSnapshot
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluations
import java.time.LocalDateTime
import java.time.ZoneOffset

class MyBatisPracticeEvaluations(private val mapper: EvaluationReadMapper) : PracticeEvaluations {
    override fun findOwned(sessionId: Long, userId: Long): PracticeEvaluationSnapshot? =
        mapper.findOwned(sessionId, userId)?.let { row ->
            PracticeEvaluationSnapshot(
                row.sessionId, row.songId, row.scoreVersion, row.bpm, row.bpmUnit, row.status,
                row.startedAt.toInstant(ZoneOffset.UTC), row.endedAt?.toInstant(ZoneOffset.UTC),
                row.evaluationId, row.totalScore, row.pitchScore, row.timingScore, row.durationScore,
                row.evaluatedAt?.toInstant(ZoneOffset.UTC), row.recordingDurationMs, row.startNoteIdx, row.endNoteIdx,
                row.tuningOffsetCent, row.tuningOutOfRange, row.parentSessionId, row.modelVersion,
            )
        }

    override fun findPreviousSessionId(userId: Long, current: PracticeEvaluationSnapshot): Long? = mapper.findPreviousSessionId(
        userId, current.sessionId, current.songId, LocalDateTime.ofInstant(current.startedAt, ZoneOffset.UTC),
        current.scoreVersion, current.startNoteIdx, current.endNoteIdx,
    )

    override fun notes(evaluationId: Long): List<PracticeEvaluationNote> = mapper.notes(evaluationId).map {
        PracticeEvaluationNote(it.noteIdx, it.verdict, it.pitchErrorCent?.toInt(), it.onsetErrorMs, it.durationErrorMs,
            it.expectedPitchId, it.detectedPitchId, it.recordingOffsetMs)
    }
}

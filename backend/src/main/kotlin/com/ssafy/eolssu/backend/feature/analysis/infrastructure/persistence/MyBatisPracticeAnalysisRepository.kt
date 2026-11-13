package com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import java.time.LocalDateTime
import kotlin.math.roundToInt
import org.springframework.transaction.support.TransactionOperations

/**
 * 세션 상태를 먼저 조건부로 바꾸고, 바뀐 경우에만 평가를 저장한다. 같은 트랜잭션이라 상태와 평가가 함께 남거나 함께 빠진다.
 * 조건부 갱신의 행 잠금 덕분에 같은 세션의 결과가 두 번 저장되지 않는다.
 */
class MyBatisPracticeAnalysisRepository(
    private val mapper: PracticeAnalysisMapper,
    private val transactions: TransactionOperations,
) : PracticeAnalysisRepository {
    override fun findPending(sessionId: Long, startedAfter: LocalDateTime) = mapper.findPending(sessionId, startedAfter)?.run {
        PendingAnalysis(sessionId, attempt, songId, scoreVersion, startNoteIdx, endNoteIdx, bpm, bpmUnit, recordingUrl, instrument)
    }

    override fun saveDone(analysis: PendingAnalysis, result: AnalysisResult.Done, notes: List<ScoreNote>, startedAfter: LocalDateTime): Boolean {
        val pitchIds = notes.associate { it.idx to it.pitchId }
        // 인식 음이 하나라도 있을 때만 기준 데이터를 읽는다. `pitches`에 없는 반음(악기 음역 밖)은 저장하지 않는다.
        val detectedIds = if (result.notes.any { it.detectedSemitone != null }) {
            mapper.findPitches().associate { it.semitone.toInt() to it.id }
        } else {
            emptyMap()
        }
        val rows = result.notes.map {
            NewNoteResult(
                it.idx,
                checkNotNull(pitchIds[it.idx]) { "note ${it.idx} is not in the requested score notes" },
                it.pitchErrorCent?.roundToInt(),
                it.onsetErrorMs,
                it.durationErrorMs,
                it.verdict.name,
                it.detectedSemitone?.let(detectedIds::get),
                it.recordingOffsetMs,
                it.score?.pitch,
                it.score?.timing,
                it.score?.duration,
                it.score?.weight,
            )
        }
        return transactions.execute {
            if (mapper.markDone(analysis.sessionId, analysis.attempt, startedAfter) == 0) return@execute false
            val evaluation = NewEvaluation(
                analysis.sessionId, result.total, result.pitch, result.timing, result.duration, result.modelVersion,
                result.tuningOffsetCent, result.tuningOutOfRange, result.scoring?.version,
                result.scoring?.pitchWeight, result.scoring?.timingWeight, result.scoring?.durationWeight,
            )
            mapper.insertEvaluation(evaluation)
            if (rows.isNotEmpty()) mapper.insertNoteResults(checkNotNull(evaluation.id), rows)
            true
        } == true
    }

    override fun saveFailed(analysis: PendingAnalysis, failReason: String, startedAfter: LocalDateTime) =
        mapper.markFailed(analysis.sessionId, analysis.attempt, failReason, startedAfter) == 1

    override fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime, expectedAttempt: Int?) =
        mapper.expireOverdue(sessionId, startedAfter, expectedAttempt) == 1
}

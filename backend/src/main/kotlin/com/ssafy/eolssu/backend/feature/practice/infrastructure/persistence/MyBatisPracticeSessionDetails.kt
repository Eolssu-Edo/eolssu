package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetail
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetails
import java.time.LocalDateTime

class MyBatisPracticeSessionDetails(
    private val mapper: PracticeSessionMapper,
) : PracticeSessionDetails {
    override fun findOwned(sessionId: Long, userId: Long) = mapper.findDetail(sessionId, userId)?.run {
        PracticeSessionDetail(
            sessionId, userId, songId, bpm, bpmUnit, startNoteIdx, endNoteIdx, status, evaluationId, failReason,
            recordingAvailable, startedAt, endedAt, analysisStartedAt, savedAt, memo, viewedAt,
        )
    }

    override fun markViewed(sessionId: Long, userId: Long, viewedAt: LocalDateTime) = mapper.markViewed(sessionId, userId, viewedAt) > 0
}

package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.practice.domain.ExpiredRecording
import com.ssafy.eolssu.backend.feature.practice.domain.UnsavedRecordingRepository
import java.time.LocalDateTime

class MyBatisUnsavedRecordingRepository(
    private val mapper: PracticeSessionMapper,
) : UnsavedRecordingRepository {
    override fun findExpired(cutoff: LocalDateTime, afterSessionId: Long, limit: Int): List<ExpiredRecording> =
        mapper.findExpiredUnsavedRecordings(cutoff, afterSessionId, limit).map { ExpiredRecording(it.sessionId, it.recordingUrl) }

    override fun clear(recording: ExpiredRecording, cutoff: LocalDateTime): Boolean =
        mapper.clearUnsavedRecording(recording.sessionId, recording.key, cutoff) == 1

    override fun restore(recording: ExpiredRecording): Boolean =
        mapper.restoreRecording(recording.sessionId, recording.key) == 1
}

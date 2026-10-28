package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import com.ssafy.eolssu.backend.feature.practice.domain.UploadedRecording

class MyBatisRecordingUploadRepository(
    private val mapper: PracticeSessionMapper,
) : RecordingUploadRepository {
    override fun findOwned(sessionId: Long, userId: Long) =
        mapper.findUploadTarget(sessionId, userId)?.run { RecordingUploadTarget(status, failReason, startedAt) }

    override fun markUploaded(sessionId: Long, userId: Long, recording: UploadedRecording) = with(recording) {
        mapper.markRecordingUploaded(
            sessionId, userId, key, metadata.bytes, metadata.durationMs, metadata.sampleRate, metadata.channels, uploadedAt,
        ) == 1
    }
}

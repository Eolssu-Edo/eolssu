package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import com.ssafy.eolssu.backend.feature.practice.domain.UploadedRecording
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate

class MyBatisRecordingUploadRepository(
    private val mapper: PracticeSessionMapper,
    private val transactions: TransactionTemplate,
    private val events: ApplicationEventPublisher,
) : RecordingUploadRepository {
    override fun findOwned(sessionId: Long, userId: Long) =
        mapper.findUploadTarget(sessionId, userId)?.run { RecordingUploadTarget(status, failReason, startedAt) }

    // 분석 시작(AnalysisOnUpload)은 커밋 뒤 이벤트만 받는다. 트랜잭션 밖에서 발행하면 버려지므로 갱신과 같은 트랜잭션에서 발행한다.
    override fun markUploaded(sessionId: Long, userId: Long, recording: UploadedRecording): Boolean = transactions.execute {
        val updated = with(recording) {
            mapper.markRecordingUploaded(
                sessionId, userId, key, metadata.bytes, metadata.durationMs, metadata.sampleRate, metadata.channels, uploadedAt,
            ) == 1
        }
        if (updated) events.publishEvent(RecordingUploaded(sessionId, recording.timingId))
        updated
    } == true
}

package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory

/**
 * 기록과 녹음을 지운다(REC-04). 본인 기록인지 확인한 뒤 녹음 파일을 먼저 지우고, 그다음 DB에 지운 시각을 남긴다.
 * 파일을 지우지 못하면 DB를 바꾸지 않는다. 저장소를 부르는 동안 DB 연결·행 잠금을 잡지 않도록 트랜잭션을 쓰지 않고,
 * 동시 삭제·저장은 마지막 조건부 갱신이 가른다. 지운 기록에 다시 보내면 404다.
 */
class DeletePracticeRecord(
    private val records: PracticeRecords,
    /** 저장소 키의 녹음을 지운다. 없는 키도 성공이다. 설정이 녹음 저장소(PRA-02)를 연결한다. */
    private val deleteRecording: (key: String) -> Unit,
    private val clock: Clock,
) {
    fun execute(userId: Long, sessionId: Long): DeleteRecordOutcome {
        val record = records.findDeletable(sessionId, userId) ?: return DeleteRecordOutcome.NotFound
        val key = record.recordingKey
        if (key != null) {
            try {
                deleteRecording(key)
            } catch (e: RuntimeException) {
                log.warn("Recording deletion for session {} failed ({})", sessionId, e.javaClass.simpleName)
                return DeleteRecordOutcome.Unavailable
            }
        }
        // 응답의 deletedAt이 저장값과 같도록 초 단위로 남긴다.
        val deletedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        if (!records.markDeleted(sessionId, userId, LocalDateTime.ofInstant(deletedAt, ZoneOffset.UTC))) return DeleteRecordOutcome.NotFound
        return DeleteRecordOutcome.Deleted(recordingDeleted = key != null, deletedAt = deletedAt)
    }

    private companion object {
        val log = LoggerFactory.getLogger(DeletePracticeRecord::class.java)
    }
}

sealed interface DeleteRecordOutcome {
    data object NotFound : DeleteRecordOutcome

    /** 녹음 저장소를 쓸 수 없어 아무것도 지우지 않았다. */
    data object Unavailable : DeleteRecordOutcome

    data class Deleted(val recordingDeleted: Boolean, val deletedAt: Instant) : DeleteRecordOutcome
}

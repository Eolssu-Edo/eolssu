package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingCheck
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFileValidator
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import com.ssafy.eolssu.backend.feature.practice.domain.UploadedRecording
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory

/** 녹음 업로드(PRA-02). 검사 순서는 docs/specs/recording-file.md '업로드' 표다. */
class UploadRecording(
    private val repository: RecordingUploadRepository,
    private val validator: RecordingFileValidator,
    private val storage: RecordingStorage,
    private val clock: Clock,
    private val newObjectId: () -> UUID = UUID::randomUUID,
) {
    fun upload(
        userId: Long, sessionId: Long, file: Path, sizeBytes: Long, timingId: EvaluationTimingId? = null,
    ): RecordingUploadResult {
        // 만료 판정의 "지금"은 요청을 받은 시각이다.
        val target = repository.findOwned(sessionId, userId) ?: return RecordingUploadResult.SESSION_NOT_FOUND
        if (target.isExpired(now())) return RecordingUploadResult.SESSION_EXPIRED
        if (target.status != RecordingUploadTarget.RECORDING) return RecordingUploadResult.SESSION_NOT_RECORDING

        val metadata = when (val check = validator.validate(file, sizeBytes)) {
            is RecordingCheck.Accepted -> check.metadata
            RecordingCheck.TooLarge -> return RecordingUploadResult.TOO_LARGE
            RecordingCheck.UnsupportedFormat -> return RecordingUploadResult.UNSUPPORTED_FORMAT
            RecordingCheck.InvalidDuration -> return RecordingUploadResult.INVALID_DURATION
        }

        // 요청마다 새 키라 같은 세션에 동시에 올려도 서로의 객체를 덮어쓰지 않는다.
        val key = "recordings/$sessionId/${newObjectId()}.m4a"
        try {
            storage.put(key, file)
        } catch (e: RecordingStorageException) {
            log.warn("Recording storage write failed session={} error={}", sessionId, e.javaClass.simpleName)
            return RecordingUploadResult.STORAGE_UNAVAILABLE
        }

        val updated = try {
            repository.markUploaded(sessionId, userId, UploadedRecording(key, metadata, now(), timingId))
        } catch (e: RuntimeException) {
            deleteOwnObject(sessionId, key)
            throw e
        }
        if (!updated) {
            // 다른 요청이 먼저 저장했거나 배치가 만료시켰다. 먼저 성공한 요청의 객체는 건드리지 않는다.
            deleteOwnObject(sessionId, key)
            val current = repository.findOwned(sessionId, userId) ?: return RecordingUploadResult.SESSION_NOT_FOUND
            return if (current.isTimedOut()) RecordingUploadResult.SESSION_EXPIRED else RecordingUploadResult.SESSION_NOT_RECORDING
        }
        return RecordingUploadResult.ACCEPTED
    }

    private fun deleteOwnObject(sessionId: Long, key: String) {
        try {
            storage.delete(key)
        } catch (e: RuntimeException) {
            // 지우지 못한 객체는 버킷 수명 주기 규칙(30일)이 지운다.
            log.warn("Recording object cleanup failed session={} key={} error={}", sessionId, key, e.javaClass.simpleName)
        }
    }

    private fun now(): LocalDateTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private companion object {
        val log = LoggerFactory.getLogger(UploadRecording::class.java)
    }
}

enum class RecordingUploadResult {
    ACCEPTED,
    SESSION_NOT_FOUND,
    SESSION_EXPIRED,
    SESSION_NOT_RECORDING,
    TOO_LARGE,
    UNSUPPORTED_FORMAT,
    INVALID_DURATION,
    STORAGE_UNAVAILABLE,
}

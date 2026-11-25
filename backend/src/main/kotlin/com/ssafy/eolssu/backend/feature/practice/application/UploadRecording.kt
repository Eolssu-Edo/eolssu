package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingCheck
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFileValidator
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFormat
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingMetadata
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingTranscoder
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import com.ssafy.eolssu.backend.feature.practice.domain.UploadedRecording
import java.nio.file.Files
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
    private val transcoder: RecordingTranscoder,
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

        val check = validator.validate(file, sizeBytes)
        if (check !is RecordingCheck.Accepted) return check.rejection()
        if (check.format == RecordingFormat.AAC_MP4) return store(userId, sessionId, file, check.metadata, timingId)

        // 웹 녹음의 WAV는 AAC m4a로 바꿔, 변환본을 다시 검사한 뒤 변환본과 그 메타데이터를 저장한다.
        val converted = Files.createTempFile("recording-", ".m4a")
        try {
            if (!transcoder.toAac(file, converted)) {
                log.warn("Recording WAV conversion failed session={}", sessionId)
                return RecordingUploadResult.UNSUPPORTED_FORMAT
            }
            val convertedCheck = validator.validate(converted, Files.size(converted))
            if (convertedCheck !is RecordingCheck.Accepted || convertedCheck.format != RecordingFormat.AAC_MP4) {
                return convertedCheck.rejection()
            }
            return store(userId, sessionId, converted, convertedCheck.metadata, timingId)
        } finally {
            Files.deleteIfExists(converted)
        }
    }

    private fun store(
        userId: Long, sessionId: Long, file: Path, metadata: RecordingMetadata, timingId: EvaluationTimingId?,
    ): RecordingUploadResult {
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

    // 변환본이 AAC m4a로 읽히지 않는 경우(Accepted이지만 WAV)도 형식 위반이다.
    private fun RecordingCheck.rejection(): RecordingUploadResult = when (this) {
        RecordingCheck.TooLarge -> RecordingUploadResult.TOO_LARGE
        RecordingCheck.InvalidDuration -> RecordingUploadResult.INVALID_DURATION
        RecordingCheck.UnsupportedFormat, is RecordingCheck.Accepted -> RecordingUploadResult.UNSUPPORTED_FORMAT
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

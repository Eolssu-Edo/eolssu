package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.RECORDING_UPLOAD_WINDOW
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingSessionExpiryRepository
import com.ssafy.eolssu.backend.feature.practice.domain.UPLOAD_TIMEOUT
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class RecordingUploadExpiryService(
    private val repository: RecordingSessionExpiryRepository,
    private val clock: Clock,
) {
    fun expireStaleRecordings(): Int {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        return repository.failRecordingStartedBefore(now.minus(RECORDING_UPLOAD_WINDOW), UPLOAD_TIMEOUT)
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun runScheduled() {
        val changed = expireStaleRecordings()
        if (changed > 0) log.info("Expired {} recording sessions without upload", changed)
    }

    private companion object {
        val log = LoggerFactory.getLogger(RecordingUploadExpiryService::class.java)
    }
}

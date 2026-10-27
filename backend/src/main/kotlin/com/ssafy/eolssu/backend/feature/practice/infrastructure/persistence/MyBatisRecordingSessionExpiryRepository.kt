package com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingSessionExpiryRepository
import java.time.LocalDateTime

class MyBatisRecordingSessionExpiryRepository(
    private val mapper: PracticeSessionMapper,
) : RecordingSessionExpiryRepository {
    override fun failRecordingStartedBefore(cutoff: LocalDateTime, failReason: String): Int =
        mapper.failRecordingStartedBefore(cutoff, failReason)
}

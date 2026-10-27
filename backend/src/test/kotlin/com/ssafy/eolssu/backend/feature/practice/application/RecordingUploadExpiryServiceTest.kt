package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingSessionExpiryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RecordingUploadExpiryServiceTest {
    private class RecordingRepository(private val changed: Int) : RecordingSessionExpiryRepository {
        val calls = mutableListOf<Pair<LocalDateTime, String>>()

        override fun failRecordingStartedBefore(cutoff: LocalDateTime, failReason: String): Int {
            calls += cutoff to failReason
            return changed
        }
    }

    @Test
    fun `fails RECORDING sessions started more than 40 minutes before now in UTC`() {
        val repository = RecordingRepository(changed = 2)
        val clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.ofHours(9))

        val changed = RecordingUploadExpiryService(repository, clock).expireStaleRecordings()

        assertEquals(2, changed)
        assertEquals(listOf(LocalDateTime.parse("2026-10-27T09:20:00") to "UPLOAD_TIMEOUT"), repository.calls)
    }
}

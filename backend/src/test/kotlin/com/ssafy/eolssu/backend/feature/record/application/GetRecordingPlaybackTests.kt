package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.domain.MemoChange
import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import com.ssafy.eolssu.backend.feature.record.domain.RecordSession
import com.ssafy.eolssu.backend.feature.record.domain.RecordSort
import com.ssafy.eolssu.backend.feature.record.domain.RecordingFile
import com.ssafy.eolssu.backend.feature.record.domain.SongRecordSummary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GetRecordingPlaybackTests {
    private val signed = mutableListOf<Pair<String, Duration>>()
    private var storageFails = false
    private val records = object : PracticeRecords {
        override fun lockStatus(sessionId: Long, userId: Long): String? = error("not used")

        override fun findSession(sessionId: Long, userId: Long): RecordSession? = error("not used")

        override fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange) = error("not used")

        override fun songSummaries(userId: Long): List<SongRecordSummary> = error("not used")

        override fun songRecords(userId: Long, songId: Long, sort: RecordSort, offset: Long, limit: Int): List<RecordSession> = error("not used")

        override fun findRecording(sessionId: Long, userId: Long) =
            RecordingFile("recordings/501/a.m4a", 48000).takeIf { sessionId == 501L && userId == 1L }
    }
    private val playback = GetRecordingPlayback(
        records,
        { key, ttl ->
            if (storageFails) throw IllegalStateException("storage down")
            signed += key to ttl
            "https://storage.test/$key?X-Amz-Signature=secret"
        },
        Duration.ofMinutes(15),
        Clock.fixed(Instant.parse("2026-11-02T04:00:00.700Z"), ZoneOffset.UTC),
    )

    @Test
    fun `signs the member's own recording and reports an expiry that is never later than the signature`() {
        val ready = assertIs<PlaybackOutcome.Ready>(playback.execute(1, 501))
        assertEquals(
            RecordingPlayback(501, "https://storage.test/recordings/501/a.m4a?X-Amz-Signature=secret", Instant.parse("2026-11-02T04:15:00Z"), 48000),
            ready.playback,
        )
        assertEquals(listOf("recordings/501/a.m4a" to Duration.ofMinutes(15)), signed)
        // 서명 URL은 녹음 접근 토큰이라 문자열로 남지 않는다.
        assertFalse("secret" in ready.toString())
    }

    @Test
    fun `does not ask the storage when the member has no such recording`() {
        assertEquals(PlaybackOutcome.NotFound, playback.execute(2, 501))
        assertEquals(PlaybackOutcome.NotFound, playback.execute(1, 999))
        assertTrue(signed.isEmpty())
    }

    @Test
    fun `answers unavailable when the storage cannot sign`() {
        storageFails = true
        assertEquals(PlaybackOutcome.Unavailable, playback.execute(1, 501))
    }
}

package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.RecordingStorageProperties
import com.ssafy.eolssu.backend.feature.record.UnusedPracticeRecords
import com.ssafy.eolssu.backend.feature.record.application.PlaybackOutcome
import com.ssafy.eolssu.backend.feature.record.domain.RecordingFile
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class PracticeRecordConfigTests {
    private val records = object : UnusedPracticeRecords() {
        override fun findRecording(sessionId: Long, userId: Long) = RecordingFile("recordings/501/a.m4a", 48000)
    }
    private val storage = object : RecordingStorage {
        override fun put(key: String, file: Path): Unit = error("not used")

        override fun delete(key: String): Unit = error("not used")

        override fun readUrl(key: String, ttl: Duration) = "https://storage.test/$key?expires-in=${ttl.seconds}"
    }

    @Test
    fun `playback signs a fifteen minute address with the S3 compatible storage`() {
        val playback = PracticeRecordConfig().getRecordingPlayback(records, storage, RecordingStorageProperties(type = "s3"))
        val ready = assertIs<PlaybackOutcome.Ready>(playback.execute(1, 501))
        assertEquals("https://storage.test/recordings/501/a.m4a?expires-in=900", ready.playback.url)
    }

    // 로컬 저장소의 읽기 주소는 AI가 쓰는 Compose 내부 주소라 앱에 내주지 않는다(infra/storage/README.md).
    @Test
    fun `playback never hands out the local storage read address`() {
        val playback = PracticeRecordConfig().getRecordingPlayback(records, storage, RecordingStorageProperties(type = "local"))
        assertEquals(PlaybackOutcome.Unavailable, playback.execute(1, 501))
    }
}

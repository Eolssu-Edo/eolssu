package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.application.InMemoryRecordingUploadRepository.Session
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.ACCEPTED
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.INVALID_DURATION
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.SESSION_EXPIRED
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.SESSION_NOT_FOUND
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.SESSION_NOT_RECORDING
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.STORAGE_UNAVAILABLE
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.TOO_LARGE
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult.UNSUPPORTED_FORMAT
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbe
import com.ssafy.eolssu.backend.feature.practice.domain.AudioProbeResult
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingFileValidator
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingMetadata
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingTranscodeException
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingTranscoder
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class UploadRecordingTests {
    private val now = LocalDateTime.parse("2026-10-28T03:00:00")
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repository = InMemoryRecordingUploadRepository()
    private val storage = InMemoryRecordingStorage()
    private val ids = ArrayDeque(listOf(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000002")))
    private var probeResult: AudioProbeResult? = aac(seconds = 48.0)

    /** 변환본(업로드한 파일이 아닌 경로)을 검사한 결과. */
    private var convertedProbeResult: AudioProbeResult? = convertedAac(seconds = 600.0)
    private val validator = RecordingFileValidator(AudioProbe { if (it == file) probeResult else convertedProbeResult })

    /** 변환기에 넘어간 (원본, 변환본 경로). 기본 변환은 변환본에 2바이트를 쓴다. */
    private val conversions = mutableListOf<Pair<Path, Path>>()
    private var convert: (Path) -> Boolean = { target -> Files.write(target, byteArrayOf(7, 7)); true }
    private val transcoder = RecordingTranscoder { source, target -> conversions += source to target; convert(target) }
    private val service = UploadRecording(repository, validator, storage, transcoder, clock, { ids.removeFirst() })
    private val file: Path = Files.createTempFile("recording-", ".m4a").also { Files.write(it, byteArrayOf(1, 2, 3)) }

    @AfterEach
    fun deleteFile() {
        file.deleteIfExists()
    }

    private fun session(id: Long = SESSION, userId: Long = USER, status: String = "RECORDING", failReason: String? = null, startedAt: LocalDateTime = now.minusMinutes(1)) {
        repository.sessions[id] = Session(userId, RecordingUploadTarget(status, failReason, startedAt))
    }

    private fun upload(sessionId: Long = SESSION, userId: Long = USER, size: Long = 3) = service.upload(userId, sessionId, file, size)

    @Test
    fun `stores the original under a per-request key and moves the session to PENDING`() {
        session()

        assertEquals(ACCEPTED, upload())

        val key = "recordings/$SESSION/00000000-0000-0000-0000-000000000001.m4a"
        assertContentEquals(byteArrayOf(1, 2, 3), storage.objects[key])
        val stored = repository.sessions.getValue(SESSION)
        assertEquals("PENDING", stored.target.status)
        assertEquals(1, stored.attempt)
        assertEquals(key, stored.recording?.key)
        assertEquals(RecordingMetadata(bytes = 3, durationMs = 48_000, sampleRate = 48_000, channels = 1), stored.recording?.metadata)
        assertEquals(now, stored.recording?.uploadedAt)
        assertEquals(listOf(SESSION), repository.published)
        // AAC m4a는 바꾸지 않고 올린 그대로 저장한다.
        assertEquals(emptyList(), conversions)
    }

    @Test
    fun `converts PCM16 WAV to AAC m4a and stores the converted file with its metadata`() {
        session()
        probeResult = wav(seconds = 600.0)

        assertEquals(ACCEPTED, upload(size = 19_200_044))

        val (source, target) = conversions.single()
        assertEquals(file, source)
        val key = "recordings/$SESSION/00000000-0000-0000-0000-000000000001.m4a"
        assertContentEquals(byteArrayOf(7, 7), storage.objects[key])
        val stored = repository.sessions.getValue(SESSION)
        assertEquals("PENDING", stored.target.status)
        assertEquals(key, stored.recording?.key)
        // 메타데이터는 업로드한 WAV가 아니라 저장한 변환본의 값이다.
        assertEquals(RecordingMetadata(bytes = 2, durationMs = 600_000, sampleRate = 16_000, channels = 1), stored.recording?.metadata)
        assertEquals(listOf(SESSION), repository.published)
        assertFalse(target.exists(), "변환본 임시 파일은 요청이 끝나면 지운다")
    }

    @Test
    fun `checks size, format and duration of the uploaded WAV before converting`() {
        session()
        probeResult = wav(seconds = 3.0)
        assertEquals(TOO_LARGE, upload(size = 20L * 1024 * 1024 + 1))
        probeResult = wav(seconds = 3.0).copy(audioCodec = "pcm_f32le")
        assertEquals(UNSUPPORTED_FORMAT, upload())
        probeResult = wav(seconds = 601.0)
        assertEquals(INVALID_DURATION, upload())

        assertEquals(emptyList(), conversions)
        assertNothingStored()
    }

    @Test
    fun `answers 415 when the WAV cannot be converted or the converted file is not valid AAC m4a`() {
        session()
        probeResult = wav(seconds = 3.0)
        convert = { false }
        assertEquals(UNSUPPORTED_FORMAT, upload())

        convert = { target -> Files.write(target, byteArrayOf(7)); true }
        convertedProbeResult = null
        assertEquals(UNSUPPORTED_FORMAT, upload())
        convertedProbeResult = wav(seconds = 3.0)
        assertEquals(UNSUPPORTED_FORMAT, upload())
        // 변환본도 같은 길이 규칙으로 다시 검사한다.
        convertedProbeResult = convertedAac(seconds = 601.0)
        assertEquals(INVALID_DURATION, upload())

        assertNothingStored()
        assertEquals("RECORDING", repository.sessions.getValue(SESSION).target.status)
        assertEquals(4, conversions.size)
        assertEquals(emptyList(), conversions.filter { it.second.exists() })

        // 거부된 뒤에도 같은 세션으로 다시 올릴 수 있다.
        convertedProbeResult = convertedAac(seconds = 3.0)
        assertEquals(ACCEPTED, upload())
    }

    @Test
    fun `a conversion tool failure propagates without storing and removes the converted file`() {
        session()
        probeResult = wav(seconds = 3.0)
        convert = { throw RecordingTranscodeException("ffmpeg missing") }

        assertFailsWith<RecordingTranscodeException> { upload() }

        assertNothingStored()
        assertEquals("RECORDING", repository.sessions.getValue(SESSION).target.status)
        assertFalse(conversions.single().second.exists())
    }

    @Test
    fun `a storage failure after converting answers 503 and removes the converted file`() {
        session()
        probeResult = wav(seconds = 3.0)
        storage.failPuts = true

        assertEquals(STORAGE_UNAVAILABLE, upload())

        assertNothingStored()
        assertFalse(conversions.single().second.exists())
    }

    @Test
    fun `answers 404 for a missing session or another member's session`() {
        session()
        assertEquals(SESSION_NOT_FOUND, upload(sessionId = 999))
        assertEquals(SESSION_NOT_FOUND, upload(userId = 2))
        assertNothingStored()
    }

    @Test
    fun `answers 410 after 40 minutes or once the batch timed the session out, before 409`() {
        session(startedAt = now.minusMinutes(40))
        assertEquals(ACCEPTED, upload()) // 정확히 40분은 아직 만료가 아니다

        session(id = 2, startedAt = now.minusMinutes(40).minusNanos(1_000))
        assertEquals(SESSION_EXPIRED, upload(sessionId = 2))
        session(id = 3, status = "FAILED", failReason = "UPLOAD_TIMEOUT")
        assertEquals(SESSION_EXPIRED, upload(sessionId = 3))
        assertEquals(1, storage.objects.size)
    }

    @Test
    fun `answers 409 when the session is no longer recording`() {
        for ((id, status) in listOf(2L to "PENDING", 3L to "DONE")) session(id = id, status = status)
        session(id = 4, status = "FAILED", failReason = "TIMEOUT")

        for (id in 2L..4L) assertEquals(SESSION_NOT_RECORDING, upload(sessionId = id))
        assertNothingStored()
    }

    @Test
    fun `checks session state before the file`() {
        session(status = "DONE")
        probeResult = null
        assertEquals(SESSION_NOT_RECORDING, upload(size = 30_000_000))
    }

    @Test
    fun `rejects files that break the recording format without storing or changing the session`() {
        session()
        assertEquals(TOO_LARGE, upload(size = 20L * 1024 * 1024 + 1))
        probeResult = AudioProbeResult(setOf("mp3"), "mp3", 44_100, 2, 3.0)
        assertEquals(UNSUPPORTED_FORMAT, upload())
        probeResult = aac(seconds = 601.0)
        assertEquals(INVALID_DURATION, upload())

        assertNothingStored()
        assertEquals("RECORDING", repository.sessions.getValue(SESSION).target.status)
    }

    @Test
    fun `answers 503 when the storage write fails and the same session can upload again`() {
        session()
        storage.failPuts = true
        assertEquals(STORAGE_UNAVAILABLE, upload())
        assertNothingStored()

        storage.failPuts = false
        assertEquals(ACCEPTED, upload())
    }

    @Test
    fun `deletes its own object when the database update fails and keeps the session recording`() {
        session()
        repository.failUpdates = true

        assertFailsWith<IllegalStateException> { upload() }

        assertEquals(emptyMap(), storage.objects)
        assertEquals("RECORDING", repository.sessions.getValue(SESSION).target.status)
        assertEquals(emptyList(), repository.published)
        repository.failUpdates = false
        assertEquals(ACCEPTED, upload())
    }

    @Test
    fun `the losing concurrent upload deletes only its own object`() {
        session()
        val winner = "recordings/$SESSION/winner.m4a"
        repository.beforeUpdate = {
            repository.beforeUpdate = {}
            storage.objects[winner] = byteArrayOf(9)
            repository.sessions.getValue(SESSION).target = repository.sessions.getValue(SESSION).target.copy(status = "PENDING")
        }

        assertEquals(SESSION_NOT_RECORDING, upload())

        assertEquals(setOf(winner), storage.objects.keys)
        assertEquals(emptyList(), repository.published)
    }

    @Test
    fun `answers 410 when the batch expires the session during the upload`() {
        session()
        repository.beforeUpdate = {
            repository.sessions.getValue(SESSION).target = repository.sessions.getValue(SESSION).target.copy(status = "FAILED", failReason = "UPLOAD_TIMEOUT")
        }

        assertEquals(SESSION_EXPIRED, upload())
        assertEquals(emptyMap(), storage.objects)
    }

    @Test
    fun `answers 404 when the session disappears during the upload`() {
        session()
        repository.beforeUpdate = { repository.sessions.remove(it) } // 탈퇴 일괄 삭제 등

        assertEquals(SESSION_NOT_FOUND, upload())
        assertEquals(emptyMap(), storage.objects)
    }

    private fun assertNothingStored() {
        assertEquals(emptyMap(), storage.objects)
        assertNull(repository.sessions[SESSION]?.recording)
    }

    private fun aac(seconds: Double) = AudioProbeResult(setOf("mov", "mp4", "m4a"), "aac", 48_000, 1, seconds)

    private fun wav(seconds: Double) = AudioProbeResult(setOf("wav"), "pcm_s16le", 16_000, 1, seconds)

    private fun convertedAac(seconds: Double) = AudioProbeResult(setOf("mov", "mp4", "m4a"), "aac", 16_000, 1, seconds)

    private companion object {
        const val SESSION = 501L
        const val USER = 1L
    }
}

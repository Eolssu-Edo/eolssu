package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/**
 * 업로드가 끝날 때까지 녹음을 기기에 두고, 결과에 따라 지우거나 다시 보내게 하는지 가짜 업로드·가짜 파일로 검사한다
 * (PRA-03 '녹음 다시 보내기', PRA-02 업로드 오류 코드와 40분 만료).
 */
class RecordingUploadsTest {
    private val store = FakeKeyValueStore()
    private val files = FakeRecordingFiles()
    private val uploader = FakeUploader()
    private var now = 1_000_000L
    private fun uploads() = RecordingUploads(uploader, store, files, clock = { now })

    private val recording = PendingRecording(sessionId = "41", path = "rec/practice-1.m4a", startedAtMillis = now)

    @Test
    fun sentRecordingIsDeleted() = immediate {
        val uploads = uploads()
        assertEquals(SendResult.Sent, uploads.send(recording))
        assertEquals(listOf("41" to "kept/practice-1.m4a"), uploader.calls)
        assertEquals(setOf<String>(), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun unreachableServerKeepsRecordingForResend() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        assertEquals(SendResult.CanResend, uploads.send(recording))
        assertEquals(setOf("kept/practice-1.m4a"), files.existing)
        assertEquals(recording.copy(path = "kept/practice-1.m4a"), uploads.pending())

        assertEquals(SendResult.Sent, uploads.resend())
        assertEquals(listOf("41", "41"), uploader.calls.map { it.first })
        assertEquals(setOf<String>(), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun uploaderExceptionCountsAsUnreachable() = immediate {
        uploader.fail = true
        assertEquals(SendResult.CanResend, uploads().send(recording))
        assertEquals(setOf("kept/practice-1.m4a"), files.existing)
    }

    @Test
    fun pendingRecordingSurvivesRestart() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)

        val reopened = uploads()
        assertEquals("41", reopened.pending()?.sessionId)
        assertEquals(SendResult.Sent, reopened.resend())
        assertNull(uploads().pending())
    }

    @Test
    fun expiredSessionDeletesRecording() = immediate {
        uploader.outcomes += UploadOutcome.Expired
        val uploads = uploads()
        assertEquals(SendResult.Expired, uploads.send(recording))
        assertEquals(setOf<String>(), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun rejectedFileIsNotOfferedForResend() = immediate {
        uploader.outcomes += UploadOutcome.Rejected
        val uploads = uploads()
        assertEquals(SendResult.Rejected, uploads.send(recording))
        assertEquals(setOf<String>(), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun recordingPastUploadWindowIsDroppedWithoutSending() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)

        now += 40.minutes.inWholeMilliseconds
        val reopened = uploads()
        assertNull(reopened.pending())
        assertEquals(setOf<String>(), files.existing)
        assertEquals(SendResult.Expired, reopened.resend())
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun recordingStillInsideUploadWindowCanBeResent() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)

        now += 40.minutes.inWholeMilliseconds - 1
        assertEquals("41", uploads().pending()?.sessionId)
    }

    @Test
    fun recordingThatCannotBeKeptIsNotSent() = immediate {
        files.keepFails = true
        assertEquals(SendResult.Rejected, uploads().send(recording))
        assertEquals(listOf<Pair<String, String>>(), uploader.calls)
    }

    @Test
    fun newRecordingReplacesWaitingOne() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        uploads.send(recording)
        uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now))
        assertEquals(setOf("kept/practice-2.m4a"), files.existing)
        assertEquals("42", uploads.pending()?.sessionId)
    }

    @Test
    fun resendWithoutPendingRecordingIsExpired() = immediate {
        assertEquals(SendResult.Expired, uploads().resend())
    }
}

/** [keep]은 `rec/` 파일을 `kept/`로 옮긴 것처럼 기록한다. */
internal class FakeRecordingFiles : RecordingFiles {
    val existing = mutableSetOf<String>()
    var keepFails = false

    override fun keep(path: String): String? {
        if (keepFails) return null
        val kept = path.replace("rec/", "kept/")
        existing += kept
        return kept
    }

    override fun delete(path: String) {
        existing -= path
    }
}

/** 차례대로 [outcomes]를 돌려주고, 다 쓰면 성공이다. [fail]이면 예외를 던진다. */
internal class FakeUploader : RecordingUploader {
    val outcomes = ArrayDeque<UploadOutcome>()
    val calls = mutableListOf<Pair<String, String>>()
    var fail = false

    override suspend fun upload(sessionId: String, path: String): UploadOutcome {
        calls += sessionId to path
        if (fail) error("no network")
        return outcomes.removeFirstOrNull() ?: UploadOutcome.Uploaded
    }
}

/** 가짜 업로드·파일은 멈추지 않으므로 정지 함수를 그 자리에서 끝까지 돌린다. */
private fun immediate(block: suspend () -> Unit) {
    var result: Result<Unit>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    checkNotNull(result) { "끝나지 않은 코루틴" }.getOrThrow()
}

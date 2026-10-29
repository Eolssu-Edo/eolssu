package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlinx.coroutines.CompletableDeferred
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
    private val files = FakeRecordingFiles("rec/practice-1.m4a", "rec/practice-2.m4a")
    private val uploader = FakeUploader()
    private var now = 1_000_000L
    private fun uploads() = RecordingUploads(uploader, store, files, clock = { now })

    private val recording = PendingRecording(sessionId = "41", path = "rec/practice-1.m4a", startedAtMillis = now)
    private val window = 40.minutes.inWholeMilliseconds

    @Test
    fun sentRecordingIsDeleted() = immediate {
        val uploads = uploads()
        assertEquals(SendResult.Sent, uploads.send(recording))
        assertEquals(listOf("41" to "kept/practice-1.m4a"), uploader.calls)
        assertEquals(setOf("rec/practice-2.m4a"), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun unreachableServerKeepsRecordingForResend() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        assertEquals(SendResult.CanResend, uploads.send(recording))
        assertEquals(setOf("kept/practice-1.m4a", "rec/practice-2.m4a"), files.existing)
        assertEquals(recording.copy(path = "kept/practice-1.m4a"), uploads.pending())

        assertEquals(SendResult.Sent, uploads.resend(recording))
        assertEquals(listOf("41", "41"), uploader.calls.map { it.first })
        assertEquals(setOf("rec/practice-2.m4a"), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun uploaderExceptionCountsAsUnreachable() = immediate {
        uploader.fail = true
        assertEquals(SendResult.CanResend, uploads().send(recording))
        assertEquals("kept/practice-1.m4a", uploads().pending()?.path)
    }

    @Test
    fun pendingRecordingSurvivesRestart() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)

        val reopened = uploads()
        assertEquals("41", reopened.pending()?.sessionId)
        assertEquals(SendResult.Sent, reopened.resend(recording))
        assertNull(uploads().pending())
    }

    @Test
    fun expiredSessionDeletesRecording() = immediate {
        uploader.outcomes += UploadOutcome.Expired
        val uploads = uploads()
        assertEquals(SendResult.Expired, uploads.send(recording))
        assertEquals(setOf("rec/practice-2.m4a"), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun rejectedFileIsNotOfferedForResend() = immediate {
        uploader.outcomes += UploadOutcome.Rejected
        val uploads = uploads()
        assertEquals(SendResult.Rejected, uploads.send(recording))
        assertEquals(setOf("rec/practice-2.m4a"), files.existing)
        assertNull(uploads.pending())
    }

    @Test
    fun exactlyFortyMinutesIsStillInsideTheUploadWindow() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)

        now += window
        assertEquals("41", uploads().pending()?.sessionId)
    }

    @Test
    fun pastTheWindowHidesTheOfferButKeepsTheFileForTheServerToDecide() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Uploaded
        uploads().send(recording)

        now += window + 1
        val reopened = uploads()
        assertNull(reopened.pending())
        assertEquals(true, "kept/practice-1.m4a" in files.existing)
        assertEquals(SendResult.Sent, reopened.resend(recording))
        assertEquals(2, uploader.calls.size)
    }

    @Test
    fun pastTheLocalClockMarginDeletesWithoutSending() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)

        now += window + LocalClockMargin.inWholeMilliseconds + 1
        val reopened = uploads()
        assertNull(reopened.pending())
        assertEquals(false, "kept/practice-1.m4a" in files.existing)
        assertEquals(SendResult.Expired, reopened.resend(recording))
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun recordingThatCannotBeMovedIsSentFromWhereItIs() = immediate {
        files.keepFails = true
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        assertEquals(SendResult.CanResend, uploads.send(recording))
        assertEquals(listOf("41" to "rec/practice-1.m4a"), uploader.calls)
        assertEquals("rec/practice-1.m4a", uploads.pending()?.path)
    }

    @Test
    fun missingRecordingIsNotSentAndKeepsTheWaitingOne() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        uploads.send(recording)
        files.existing -= "rec/practice-2.m4a"

        assertEquals(SendResult.Missing, uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now)))
        assertEquals(1, uploader.calls.size)
        assertEquals("41", uploads.pending()?.sessionId)
        assertEquals(true, "kept/practice-1.m4a" in files.existing)
    }

    @Test
    fun recordingThatVanishedBeforeResendIsMissing() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        uploads.send(recording)
        files.existing -= "kept/practice-1.m4a"

        assertEquals(SendResult.Missing, uploads.resend(recording))
        assertEquals(1, uploader.calls.size)
        assertNull(uploads.pending())
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
    fun sendingTheWaitingRecordingAgainKeepsIt() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        uploads.send(recording)

        assertEquals(SendResult.Sent, uploads.send(uploads.pending()!!))
        assertEquals(listOf("kept/practice-1.m4a", "kept/practice-1.m4a"), uploader.calls.map { it.second })
    }

    @Test
    fun lateResultOfAnOlderUploadKeepsTheNewerRecording() {
        val uploads = uploads()
        val gate = CompletableDeferred<Unit>()
        uploader.gate = gate
        val first = launchNow { uploads.send(recording) }

        uploader.gate = null
        uploader.outcomes += UploadOutcome.Unreachable
        launchNow { uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now)) }
        gate.complete(Unit)

        assertEquals(SendResult.Sent, first())
        assertEquals("42", uploads.pending()?.sessionId)
        assertEquals(true, "kept/practice-2.m4a" in files.existing)
    }

    @Test
    fun malformedRecordIsClearedAndLeftoverFilesRemoved() {
        store.values["pending_recording_upload"] = "broken"
        files.existing += "kept/orphan.m4a"
        assertNull(uploads().pending())
        assertEquals(setOf("rec/practice-1.m4a", "rec/practice-2.m4a"), files.existing)
    }

    @Test
    fun sendingRemovesLeftoverFiles() = immediate {
        files.existing += "kept/orphan.m4a"
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)
        assertEquals(setOf("kept/practice-1.m4a", "rec/practice-2.m4a"), files.existing)
    }

    @Test
    fun resendWithoutWaitingRecordingIsMissing() = immediate {
        assertEquals(SendResult.Missing, uploads().resend(recording))
        assertEquals(listOf<Pair<String, String>>(), uploader.calls)
    }

    @Test
    fun resendForAnotherSessionDoesNotSendTheNewerRecording() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        uploads.send(recording)
        uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now))

        assertEquals(SendResult.Missing, uploads.resend(recording))
        assertEquals(2, uploader.calls.size)
        assertEquals("42", uploads.pending()?.sessionId)
        assertEquals(true, "kept/practice-2.m4a" in files.existing)
    }

    @Test
    fun exactlyAtTheEndOfTheLocalClockMarginTheFileIsStillKept() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)

        now += window + LocalClockMargin.inWholeMilliseconds
        uploads().pending()
        assertEquals(true, "kept/practice-1.m4a" in files.existing)
    }

    @Test
    fun leftoverFilesAreRemovedWhenNothingIsWaiting() {
        store.values["pending_recording_upload"] = ""
        files.existing += "kept/orphan.m4a"
        assertNull(uploads().pending())
        assertEquals(setOf("rec/practice-1.m4a", "rec/practice-2.m4a"), files.existing)
    }
}

/** 녹음기 파일은 `rec/`, 보관한 파일은 `kept/`에 있는 것처럼 기록한다. [existing]에 없는 파일은 없는 파일이다. */
internal class FakeRecordingFiles(vararg recorded: String) : RecordingFiles {
    val existing = recorded.toMutableSet()
    var keepFails = false

    override fun keep(path: String): String? {
        if (keepFails || path !in existing) return null
        if (path.startsWith("kept/")) return path
        val kept = path.replace("rec/", "kept/")
        existing -= path
        existing += kept
        return kept
    }

    override fun exists(path: String): Boolean = path in existing

    override fun delete(path: String) {
        existing -= path
    }

    override fun deleteKeptExcept(path: String?) {
        existing.removeAll { it.startsWith("kept/") && it != path }
    }
}

/** 차례대로 [outcomes]를 돌려주고, 다 쓰면 성공이다. [fail]이면 예외를 던지고, [gate]가 있으면 열릴 때까지 기다린다. */
internal class FakeUploader : RecordingUploader {
    val outcomes = ArrayDeque<UploadOutcome>()
    val calls = mutableListOf<Pair<String, String>>()
    var fail = false
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun upload(sessionId: String, path: String): UploadOutcome {
        calls += sessionId to path
        val outcome = outcomes.removeFirstOrNull() ?: UploadOutcome.Uploaded
        gate?.await()
        if (fail) error("no network")
        return outcome
    }
}

/** 가짜 업로드·파일은 멈추지 않으므로 정지 함수를 그 자리에서 끝까지 돌린다. */
private fun immediate(block: suspend () -> Unit) {
    launchNow(block)()
}

/** 정지 함수를 시작하고, 끝난 뒤 결과를 꺼내는 함수를 돌려준다. 아직 안 끝났으면 꺼낼 때 실패한다. */
private fun <T> launchNow(block: suspend () -> T): () -> T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return { checkNotNull(result) { "끝나지 않은 코루틴" }.getOrThrow() }
}

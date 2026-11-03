package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.network.SignedInMember
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
    private val member = SignedInMember(store).apply { set("A") }
    private val files = FakeRecordingFiles("rec/practice-1.m4a", "rec/practice-2.m4a")
    private val uploader = FakeUploader()
    private var now = 1_000_000L
    private fun uploads() = RecordingUploads(uploader, store, files, clock = { now }, member = member)

    private val recording = PendingRecording(sessionId = "41", path = "rec/practice-1.m4a", startedAtMillis = now, memberId = "A")
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
    fun resendFindsTheRecordingAfterTheAppFolderMoved() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)
        // iOS는 앱을 업데이트하면 앱 폴더 경로가 바뀔 수 있다. 저장한 경로는 그대로이고 파일만 새 경로에 있다.
        files.existing -= "kept/practice-1.m4a"
        files.existing += "moved/practice-1.m4a"
        files.moved["kept/practice-1.m4a"] = "moved/practice-1.m4a"

        assertEquals(SendResult.Sent, uploads().resend(recording))
        assertEquals("41" to "moved/practice-1.m4a", uploader.calls.last())
        assertEquals(setOf("rec/practice-2.m4a"), files.existing)
        assertNull(uploads().pending())
    }

    @Test
    fun pendingRecordingKeepsSongAndBpmForTheNextRun() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording.copy(songId = "2", bpm = 66))

        val reopened = uploads().pending()
        assertEquals("2", reopened?.songId)
        assertEquals(66, reopened?.bpm)
    }

    @Test
    fun recordWithoutOwnerIsPreservedWithoutOfferingToSend() = immediate {
        store.putString("pending_recording_upload", listOf("41", "$now", "kept/practice-1.m4a").joinToString("\n"))
        files.existing += "kept/practice-1.m4a"
        val uploads = uploads()
        assertNull(uploads.pending())
        uploads.cleanUp()
        assertEquals(setOf("rec/practice-1.m4a", "rec/practice-2.m4a", "kept/practice-1.m4a"), files.existing)
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

        assertEquals(SendResult.Missing, uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now, memberId = "A")))
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
        uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now, memberId = "A"))
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
        launchNow { uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now, memberId = "A")) }
        gate.complete(Unit)

        assertEquals(SendResult.Sent, first())
        assertEquals("42", uploads.pending()?.sessionId)
        assertEquals(true, "kept/practice-2.m4a" in files.existing)
    }

    @Test
    fun malformedLegacyRecordAndOwnerlessFilesArePreservedAtStart() {
        store.values["pending_recording_upload"] = "broken"
        files.existing += "kept/orphan.m4a"
        val uploads = uploads()
        uploads.cleanUp()
        assertNull(uploads.pending())
        assertEquals("broken", store.values["pending_recording_upload"])
        assertEquals(setOf("rec/practice-1.m4a", "rec/practice-2.m4a", "kept/orphan.m4a"), files.existing)
    }

    @Test
    fun cleanUpAtStartKeepsTheWaitingRecording() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)
        files.existing += "kept/orphan.m4a"

        uploads().cleanUp()
        assertEquals(setOf("kept/practice-1.m4a", "rec/practice-2.m4a", "kept/orphan.m4a"), files.existing)
    }

    @Test
    fun readingWhileSendingDoesNotDeleteTheJustKeptRecording() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        files.onKeep = { uploads.pending() }

        assertEquals(SendResult.CanResend, uploads.send(recording))
        assertEquals(true, "kept/practice-1.m4a" in files.existing)
        assertEquals("kept/practice-1.m4a", uploads.pending()?.path)
    }

    @Test
    fun sendingPreservesFilesWithUnknownOwner() = immediate {
        files.existing += "kept/orphan.m4a"
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)
        assertEquals(setOf("kept/practice-1.m4a", "rec/practice-2.m4a", "kept/orphan.m4a"), files.existing)
    }

    @Test
    fun resendWithoutWaitingRecordingIsMissing() = immediate {
        assertEquals(SendResult.Missing, uploads().resend(recording))
        assertEquals(listOf<Pair<String, String>>(), uploader.calls)
    }

    @Test
    fun resendWithoutWaitingRecordingAtExactlyFortyMinutesIsMissing() = immediate {
        now += window
        assertEquals(SendResult.Missing, uploads().resend(recording))
    }

    @Test
    fun resendAfterANewerRecordingPastTheWindowIsExpired() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        uploads.send(recording)
        now += window + 1
        uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now, memberId = "A"))

        assertEquals(SendResult.Expired, uploads.resend(recording))
        assertEquals(2, uploader.calls.size)
        assertEquals("42", uploads.pending()?.sessionId)
    }

    @Test
    fun resendForAnotherSessionDoesNotSendTheNewerRecording() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Unreachable
        val uploads = uploads()
        uploads.send(recording)
        uploads.send(PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = now, memberId = "A"))

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
    fun ownerlessFilesArePreservedAtStartWhenNothingIsWaiting() {
        store.values["pending_recording_upload"] = ""
        files.existing += "kept/orphan.m4a"
        val uploads = uploads()
        assertNull(uploads.pending())
        assertEquals(true, "kept/orphan.m4a" in files.existing)

        uploads.cleanUp()
        assertEquals(setOf("rec/practice-1.m4a", "rec/practice-2.m4a", "kept/orphan.m4a"), files.existing)
    }

    @Test
    fun logoutAndAnotherAccountCannotSendOrDeleteTheOwnersRecording() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)
        val owned = checkNotNull(uploads().pending())
        val savedFiles = files.existing.toSet()

        for (nextMember in listOf(null, "B")) {
            member.changing { member.set(nextMember) }
            val reopened = uploads()
            reopened.cleanUp()
            assertNull(reopened.pending())
            assertEquals(SendResult.NotOwner, reopened.resend(owned))
            assertEquals(SendResult.NotOwner, reopened.send(owned))
            assertEquals(1, uploader.calls.size)
            assertEquals(savedFiles, files.existing)
            assertEquals(emptyList(), files.deleted)
            assertEquals(0, files.bulkDeleteCalls)
        }

        member.changing { member.set("A") }
        assertEquals(owned, uploads().pending())
        assertEquals(SendResult.Sent, uploads().resend(owned))
        assertEquals(2, uploader.calls.size)
    }

    @Test
    fun anotherAccountsNewRecordingDoesNotReplaceTheOwnersWaitingRecording() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)
        val owned = checkNotNull(uploads().pending())
        member.changing { member.set("B") }

        uploader.outcomes += UploadOutcome.Unreachable
        val other = PendingRecording("42", "rec/practice-2.m4a", now, memberId = "B")
        assertEquals(SendResult.CanResend, uploads().send(other))
        assertEquals("42", uploads().pending()?.sessionId)
        assertEquals(setOf("kept/practice-1.m4a", "kept/practice-2.m4a"), files.existing)

        member.changing { member.set("A") }
        assertEquals(owned, uploads().pending())
        assertEquals(SendResult.Sent, uploads().resend(owned))
        member.changing { member.set("B") }
        assertEquals("42", uploads().pending()?.sessionId)
        assertEquals(true, "kept/practice-2.m4a" in files.existing)
    }

    @Test
    fun anotherAccountDoesNotExpireTheOwnersFile() = immediate {
        uploader.outcomes += UploadOutcome.Unreachable
        uploads().send(recording)
        val owned = checkNotNull(uploads().pending())
        member.changing { member.set("B") }
        now += window + LocalClockMargin.inWholeMilliseconds + 1

        uploads().cleanUp()
        assertNull(uploads().pending())
        assertEquals(SendResult.NotOwner, uploads().resend(owned))
        assertEquals(true, owned.path in files.existing)
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun legacyRecordingCannotAcquireTheCurrentlySignedInOwner() = immediate {
        val legacy = recording.copy(path = "kept/practice-1.m4a", memberId = null)
        val saved = listOf("41", "$now", "2", "60", legacy.path).joinToString("\n")
        store.putString("pending_recording_upload", saved)
        files.existing += legacy.path

        assertNull(uploads().pending())
        assertEquals(SendResult.NotOwner, uploads().send(legacy))
        assertEquals(SendResult.NotOwner, uploads().resend(legacy))
        uploads().cleanUp()
        assertEquals(saved, store.getString("pending_recording_upload"))
        assertEquals(true, legacy.path in files.existing)
        assertEquals(0, uploader.calls.size)
        assertEquals(emptyList(), files.deleted)
        assertEquals(0, files.bulkDeleteCalls)
    }

    @Test
    fun accountChangeWaitsUntilTheOwnersUploadCompletes() {
        val gate = CompletableDeferred<Unit>()
        uploader.gate = gate
        uploader.outcomes += UploadOutcome.Unreachable
        val send = launchNow { uploads().send(recording) }
        val changeAccount = launchNow { member.changing { member.set("B") } }
        assertEquals("A", member.id)
        gate.complete(Unit)

        assertEquals(SendResult.CanResend, send())
        changeAccount()
        assertEquals("B", member.id)
        assertNull(uploads().pending())
        assertEquals(true, "kept/practice-1.m4a" in files.existing)
    }
}

/**
 * 녹음기 파일은 `rec/`, 보관한 파일은 `kept/`에 있는 것처럼 기록한다. [existing]에 없는 파일은 없는 파일이다.
 * [onKeep]은 파일을 옮긴 직후에 불러, 보관과 기록 저장 사이에 끼어드는 일을 흉내 낸다.
 */
internal class FakeRecordingFiles(vararg recorded: String) : RecordingFiles {
    val existing = recorded.toMutableSet()
    val deleted = mutableListOf<String>()
    var bulkDeleteCalls = 0
    var keepFails = false
    var onKeep: () -> Unit = {}
    /** 저장된 경로 → 앱 폴더가 옮겨진 뒤의 실제 경로. */
    val moved = mutableMapOf<String, String>()

    override fun keep(path: String): String? {
        if (keepFails || path !in existing) return null
        if (path.startsWith("kept/")) return path
        val kept = path.replace("rec/", "kept/")
        existing -= path
        existing += kept
        onKeep()
        return kept
    }

    override fun exists(path: String): Boolean = path in existing

    override fun locate(path: String): String? = (moved[path] ?: path).takeIf { it in existing }

    override fun delete(path: String) {
        deleted += path
        existing -= path
    }

    override fun deleteKeptExcept(path: String?) {
        bulkDeleteCalls++
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

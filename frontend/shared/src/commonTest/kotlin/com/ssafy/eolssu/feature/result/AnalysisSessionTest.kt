package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.FakeRecordingFiles
import com.ssafy.eolssu.data.practice.FakeUploader
import com.ssafy.eolssu.data.practice.PendingRecording
import com.ssafy.eolssu.data.practice.RecordingUploads
import com.ssafy.eolssu.data.practice.UploadOutcome
import com.ssafy.eolssu.data.song.SampleSongs
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/**
 * 분석 대기 화면의 조회·멈춤·시간 초과·복귀를 가짜 시계와 가짜 저장소로 검사한다(PRA-03 상태 조회와 시간 초과).
 * 코루틴은 바로 실행되는 범위에서 돌리고, 기다림은 [FakeTime]이 시계를 넘길 때만 풀린다.
 */
class AnalysisSessionTest {
    private val time = FakeTime()
    private val repository = FakeEvaluations()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val session = AnalysisSession(scope, repository, clock = { time.now }, sleep = time::sleep)
    private val state get() = session.state.value

    private fun start() {
        scope.launch { session.start(SampleSongs.first(), bpm = 60) }
    }

    @Test
    fun pollsEveryTwoSecondsAndStopsWhenDone() {
        start()
        assertEquals(1, repository.queries)
        time.advanceBy(2_000)
        assertEquals(2, repository.queries)
        repository.status = { AnalysisStatus.Done("evaluation-1") }
        time.advanceBy(2_000)
        assertEquals("evaluation-1", state.evaluationId)
        time.advanceBy(10_000)
        assertEquals(3, repository.queries)
    }

    @Test
    fun stopsOnServerFailure() {
        repository.status = { AnalysisStatus.Failed(AnalysisFailure.NoSound) }
        start()
        assertEquals(AnalysisFailure.NoSound, state.failure)
        time.advanceBy(10_000)
        assertEquals(1, repository.queries)
    }

    @Test
    fun seventySecondsWithoutResponseShowsTimeout() {
        repository.status = { error("network") }
        start()
        time.advanceBy(69_999)
        assertNull(state.failure)
        time.advanceBy(1)
        assertEquals(AnalysisFailure.Timeout, state.failure)
    }

    @Test
    fun returningQueriesOnceAndResumesPollingWhilePending() {
        start()
        time.advanceBy(1_000)
        session.pause()
        time.advanceBy(300_000)
        assertEquals(1, repository.queries)
        session.resume()
        assertEquals(2, repository.queries)
        time.advanceBy(2_000)
        assertEquals(3, repository.queries)
        assertNull(state.failure)
    }

    @Test
    fun goingToBackgroundDuringTheRequestDoesNotStartPollingUntilReturn() {
        val requested = CompletableDeferred<Unit>()
        repository.requestGate = requested
        start()
        session.pause()
        requested.complete(Unit)
        time.advanceBy(100_000)
        assertEquals(0, repository.queries)
        assertNull(state.failure)
        session.resume()
        assertEquals(1, repository.queries)
    }

    @Test
    fun retryAfterTimeoutAsksTheServerAndPollsAgain() {
        repository.status = { error("network") }
        start()
        time.advanceBy(70_000)
        assertEquals(AnalysisFailure.Timeout, state.failure)
        repository.status = { AnalysisStatus.Pending }
        val before = repository.queries
        session.retry()
        assertEquals(1, repository.retries)
        assertNull(state.failure)
        assertEquals(before + 1, repository.queries)
        time.advanceBy(2_000)
        assertEquals(before + 2, repository.queries)
    }

    @Test
    fun cancellationFromTheRepositoryIsNoResponseNotAStop() {
        repository.status = { throw CancellationException("요청 취소") }
        start()
        time.advanceBy(2_000)
        assertEquals(2, repository.queries)
        time.advanceBy(68_000)
        assertEquals(AnalysisFailure.Timeout, state.failure)
    }

    @Test
    fun aStatusRequestThatNeverAnswersEndsInTimeoutAtSeventySeconds() {
        repository.hangs = true
        start()
        time.advanceBy(69_999)
        assertNull(state.failure)
        time.advanceBy(1)
        assertEquals(AnalysisFailure.Timeout, state.failure)
        assertEquals(1, repository.queries)
    }

    @Test
    fun returningAfterTimeoutKeepsTheFailureAndDoesNotPollAgain() {
        repository.status = { error("network") }
        start()
        time.advanceBy(70_000)
        val before = repository.queries
        session.pause()
        session.resume()
        time.advanceBy(10_000)
        assertEquals(before, repository.queries)
        assertEquals(AnalysisFailure.Timeout, state.failure)
    }

    @Test
    fun cancelledRequestIsServerErrorAndCanBeRetried() {
        repository.requestError = CancellationException("요청 취소")
        start()
        assertEquals(AnalysisFailure.ServerError, state.failure)
        assertEquals(false, state.retrying)
    }

    private val uploader = FakeUploader()
    private var uploadNow = 0L
    private val uploads = RecordingUploads(
        uploader, FakeKeyValueStore(), FakeRecordingFiles("rec/practice-1.m4a", "rec/practice-2.m4a"), clock = { uploadNow },
    )
    private val uploadSession = AnalysisSession(scope, repository, clock = { time.now }, sleep = time::sleep, uploads = uploads)
    private val uploadState get() = uploadSession.state.value

    private fun startWithRecording() {
        val recording = PendingRecording(sessionId = "41", path = "rec/practice-1.m4a", startedAtMillis = 0L)
        scope.launch { uploadSession.start(SampleSongs.first(), bpm = 60, recording = recording) }
    }

    /** 이 화면의 녹음을 보내지 못한 뒤 다른 연습의 녹음이 그 자리를 차지한다. */
    private fun replaceWithNewerRecording() {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Unreachable
        startWithRecording()
        val newer = PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = uploadNow)
        scope.launch { uploads.send(newer) }
    }

    @Test
    fun failedUploadOffersResendAndAnalyzesAfterSending() {
        uploader.outcomes += UploadOutcome.Unreachable
        startWithRecording()
        assertEquals(UploadProblem.CanResend, uploadState.upload)
        assertEquals(0, repository.queries)

        uploadSession.resend()
        assertNull(uploadState.upload)
        assertEquals(false, uploadState.resending)
        assertEquals(1, repository.queries)
        assertEquals(2, uploader.calls.size)
    }

    @Test
    fun resendThatFailsAgainStillOffersResend() {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Unreachable
        startWithRecording()
        uploadSession.resend()
        assertEquals(UploadProblem.CanResend, uploadState.upload)
        assertEquals(false, uploadState.resending)
        assertEquals(0, repository.queries)
    }

    @Test
    fun expiredUploadShowsUploadTimeout() {
        uploader.outcomes += UploadOutcome.Expired
        startWithRecording()
        assertNull(uploadState.upload)
        assertEquals(AnalysisFailure.UploadTimeout, uploadState.failure)
        assertEquals(0, repository.queries)
    }

    @Test
    fun rejectedUploadOnlyOffersPlayingAgain() {
        uploader.outcomes += UploadOutcome.Rejected
        startWithRecording()
        assertEquals(UploadProblem.Rejected, uploadState.upload)
        uploadSession.resend()
        assertEquals(1, uploader.calls.size)
        assertEquals(0, repository.queries)
    }

    @Test
    fun sentRecordingGoesStraightToAnalysis() {
        startWithRecording()
        assertNull(uploadState.upload)
        assertEquals(1, repository.queries)
    }

    @Test
    fun resendIsIgnoredWhileOneIsInFlight() {
        uploader.outcomes += UploadOutcome.Unreachable
        startWithRecording()
        val gate = CompletableDeferred<Unit>()
        uploader.gate = gate
        uploadSession.resend()
        assertEquals(true, uploadState.resending)
        uploadSession.resend()
        assertEquals(2, uploader.calls.size)

        gate.complete(Unit)
        assertNull(uploadState.upload)
        assertEquals(false, uploadState.resending)
    }

    @Test
    fun resendAfterANewerRecordingReplacedThisOneOnlyOffersPlayingAgain() {
        replaceWithNewerRecording()
        uploadSession.resend()
        assertEquals(UploadProblem.Missing, uploadState.upload)
        assertEquals(listOf("41", "42"), uploader.calls.map { it.first })
        assertEquals(0, repository.queries)
    }

    @Test
    fun resendAfterReplacementPastTheUploadWindowShowsUploadTimeout() {
        uploadNow = 40.minutes.inWholeMilliseconds + 1
        replaceWithNewerRecording()
        uploadSession.resend()
        assertNull(uploadState.upload)
        assertEquals(AnalysisFailure.UploadTimeout, uploadState.failure)
        assertEquals(listOf("41", "42"), uploader.calls.map { it.first })
    }

    @Test
    fun missingRecordingOnlyOffersPlayingAgain() {
        val session = AnalysisSession(
            scope, repository, clock = { time.now }, sleep = time::sleep,
            uploads = RecordingUploads(uploader, FakeKeyValueStore(), FakeRecordingFiles(), clock = { 0L }),
        )
        val recording = PendingRecording(sessionId = "41", path = "rec/practice-1.m4a", startedAtMillis = 0L)
        scope.launch { session.start(SampleSongs.first(), bpm = 60, recording = recording) }
        assertEquals(UploadProblem.Missing, session.state.value.upload)
        assertEquals(0, uploader.calls.size)
        assertEquals(0, repository.queries)
    }
}

/** 시계를 넘길 때 그 시각까지 기다리던 코루틴을 순서대로 깨운다. */
private class FakeTime {
    var now = 0L
        private set
    private val sleepers = mutableListOf<Pair<Long, CancellableContinuation<Unit>>>()

    suspend fun sleep(millis: Long) = suspendCancellableCoroutine { sleepers += (now + millis) to it }

    fun advanceBy(millis: Long) {
        val target = now + millis
        while (true) {
            sleepers.removeAll { it.second.isCancelled }
            val next = sleepers.filter { it.first <= target }.minByOrNull { it.first } ?: break
            sleepers.remove(next)
            now = next.first
            next.second.resume(Unit)
        }
        now = target
    }
}

private class FakeEvaluations : EvaluationRepository {
    var status: () -> AnalysisStatus = { AnalysisStatus.Pending }
    var requestGate: CompletableDeferred<Unit>? = null
    var requestError: Exception? = null
    var hangs = false
    var queries = 0
    var retries = 0

    override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String {
        requestGate?.await()
        requestError?.let { throw it }
        return "analysis-1"
    }

    override suspend fun analysisStatus(analysisId: String): AnalysisStatus {
        queries++
        if (hangs) awaitCancellation()
        return status()
    }

    override suspend fun retryAnalysis(analysisId: String) {
        retries++
    }

    override fun result(evaluationId: String): EvaluationResult? = null
}

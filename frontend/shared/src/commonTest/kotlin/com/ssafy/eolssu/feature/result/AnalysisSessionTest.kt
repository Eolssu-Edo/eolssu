package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.diagnostics.EvaluationTiming
import com.ssafy.eolssu.core.diagnostics.PerfPhase
import com.ssafy.eolssu.core.diagnostics.PerfReason
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceEvent
import com.ssafy.eolssu.core.diagnostics.PerformanceFlow
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.practice.NoRecordingToAnalyze
import com.ssafy.eolssu.data.practice.FakeRecordingFiles
import com.ssafy.eolssu.data.practice.FakeUploader
import com.ssafy.eolssu.data.practice.PendingRecording
import com.ssafy.eolssu.data.practice.PracticeSessionRepository
import com.ssafy.eolssu.data.practice.RecordingUploader
import com.ssafy.eolssu.data.practice.RecordingUploads
import com.ssafy.eolssu.data.practice.RetryOutcome
import com.ssafy.eolssu.data.practice.SessionStart
import com.ssafy.eolssu.data.practice.UploadOutcome
import com.ssafy.eolssu.data.song.SampleSongs
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
    fun pollsEverySecondAtFirstAndStopsWhenDone() {
        start()
        assertEquals(1, repository.queries)
        time.advanceBy(1_000)
        assertEquals(2, repository.queries)
        repository.status = { AnalysisStatus.Done("evaluation-1") }
        time.advanceBy(1_000)
        assertEquals("evaluation-1", state.evaluationId)
        time.advanceBy(10_000)
        assertEquals(3, repository.queries)
    }

    /** 웹에서 분석 주소로 바로 들어오거나 새로 고치면 보낼 녹음이 없다. 서버 오류가 아니라 '다시 연주'를 안내한다. */
    @Test
    fun enteringWithoutARecordingAsksToPlayAgainInsteadOfShowingAServerError() {
        repository.requestError = NoRecordingToAnalyze()
        start()
        assertEquals(UploadProblem.Missing, state.upload)
        assertNull(state.failure)
        time.advanceBy(10_000)
        assertEquals(0, repository.queries)
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
        time.advanceBy(500)
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
        time.advanceBy(1_000)
        assertEquals(before + 2, repository.queries)
    }

    /** 시간 초과로 멈춘 분석에서 다시 시도한다. */
    private fun retryAfterTimeout(outcome: () -> RetryOutcome) {
        repository.status = { error("network") }
        start()
        time.advanceBy(70_000)
        repository.retryOutcome = outcome
    }

    @Test
    fun retryConflictAsksStatusAgainAndShowsThatState() {
        retryAfterTimeout { RetryOutcome.Conflict }
        repository.status = { AnalysisStatus.Failed(AnalysisFailure.NoSound) }
        session.retry()
        assertEquals(AnalysisFailure.NoSound, state.failure)
        assertEquals(false, state.retrying)

        retryAfterTimeout { RetryOutcome.Conflict }
        repository.status = { AnalysisStatus.Done("41") }
        session.retry()
        assertEquals("41", state.evaluationId)
    }

    @Test
    fun busyServerKeepsRetryButtonWithWaitNotice() {
        retryAfterTimeout { RetryOutcome.Busy }
        val before = repository.queries
        session.retry()
        assertEquals(AnalysisFailure.Timeout, state.failure)
        assertEquals(true, state.retryBusy)
        assertEquals(false, state.retrying)
        assertEquals(true, state.canRetry())
        time.advanceBy(10_000)
        assertEquals(before, repository.queries)

        repository.retryOutcome = { RetryOutcome.Started }
        repository.status = { AnalysisStatus.Pending }
        session.retry()
        assertEquals(false, state.retryBusy)
        assertNull(state.failure)
    }

    @Test
    fun missingSessionOrLostLoginLeavesTheScreen() {
        retryAfterTimeout { RetryOutcome.NotFound }
        session.retry()
        assertEquals(AnalysisExit.SessionGone, state.exit)

        retryAfterTimeout { RetryOutcome.Unauthorized }
        session.retry()
        assertEquals(AnalysisExit.NeedsLogin, state.exit)
    }

    @Test
    fun retryWithoutResponseStaysRetryableServerError() {
        retryAfterTimeout { error("network") }
        session.retry()
        assertEquals(AnalysisFailure.ServerError, state.failure)
        assertEquals(true, state.canRetry())
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
        member = SignedInMember(FakeKeyValueStore()).apply { set("A") },
    )
    private val uploadSession = AnalysisSession(scope, repository, clock = { time.now }, sleep = time::sleep, uploads = uploads)
    private val uploadState get() = uploadSession.state.value

    private fun startWithRecording() {
        val recording = PendingRecording(sessionId = "41", path = "rec/practice-1.m4a", startedAtMillis = 0L, memberId = "A")
        scope.launch { uploadSession.start(SampleSongs.first(), bpm = 60, recording = recording) }
    }

    /** 이 화면의 녹음을 보내지 못한 뒤 다른 연습의 녹음이 그 자리를 차지한다. */
    private fun replaceWithNewerRecording() {
        uploader.outcomes += UploadOutcome.Unreachable
        uploader.outcomes += UploadOutcome.Unreachable
        startWithRecording()
        val newer = PendingRecording(sessionId = "42", path = "rec/practice-2.m4a", startedAtMillis = uploadNow, memberId = "A")
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
    fun resendKeepsTheSpecificUploadFailureUntilSendingSucceeds() {
        uploader.outcomes += UploadOutcome.TimedOut
        uploader.outcomes += UploadOutcome.ConnectionFailed
        uploader.outcomes += UploadOutcome.ServerError
        startWithRecording()
        assertEquals(UploadProblem.TimedOut, uploadState.upload)
        assertEquals(0, repository.queries)

        uploadSession.resend()
        assertEquals(UploadProblem.ConnectionFailed, uploadState.upload)
        uploadSession.resend()
        assertEquals(UploadProblem.ServerError, uploadState.upload)
        uploadSession.resend()

        assertNull(uploadState.upload)
        assertEquals(false, uploadState.resending)
        assertEquals(1, repository.queries)
        assertEquals(List(4) { "41" to "kept/practice-1.m4a" }, uploader.calls)
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
    fun uploadProgressShowsWhileSendingAndStaysWholeWhileAnalyzing() {
        uploader.progress = listOf(0.5f)
        uploader.gate = CompletableDeferred()
        startWithRecording()
        assertEquals(0.5f, uploadState.uploadProgress)

        uploader.gate?.complete(Unit)
        assertEquals(1f, uploadState.uploadProgress)
        assertEquals(1, repository.queries)
        time.advanceBy(1_000)
        assertEquals(1f, uploadState.uploadProgress)
    }

    @Test
    fun analysisCompletionHandsOverEvaluationOnTheFirstDoneWithoutWaiting() {
        // 전송을 마친 뒤 분석 중(PENDING)에는 평가 ID가 없어 완료(100%)로 보이지 않고, 첫 DONE 조회에서 바로 넘긴다.
        startWithRecording()
        assertEquals(1f, uploadState.uploadProgress)
        assertNull(uploadState.evaluationId)
        time.advanceBy(999)
        assertNull(uploadState.evaluationId)

        repository.status = { AnalysisStatus.Done("41") }
        time.advanceBy(1)
        assertEquals("41", uploadState.evaluationId)
        assertNull(uploadState.failure)
    }

    @Test
    fun failedUploadClearsProgressAndResendStartsFromZero() {
        uploader.outcomes += UploadOutcome.ConnectionFailed
        uploader.progress = listOf(0.5f)
        startWithRecording()
        assertNull(uploadState.uploadProgress)

        uploader.progress = emptyList()
        uploader.gate = CompletableDeferred()
        uploadSession.resend()
        assertEquals(0f, uploadState.uploadProgress)
    }

    @Test
    fun analysisWithoutUploadHasNoProgress() {
        start()
        assertNull(state.uploadProgress)
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
            uploads = RecordingUploads(uploader, FakeKeyValueStore(), FakeRecordingFiles(), clock = { 0L }, member = SignedInMember(FakeKeyValueStore()).apply { set("A") }),
        )
        val recording = PendingRecording(sessionId = "41", path = "rec/practice-1.m4a", startedAtMillis = 0L, memberId = "A")
        scope.launch { session.start(SampleSongs.first(), bpm = 60, recording = recording) }
        assertEquals(UploadProblem.Missing, session.state.value.upload)
        assertEquals(0, uploader.calls.size)
        assertEquals(0, repository.queries)
    }

    private val sessions = FakeSessions()
    private val takeSession = AnalysisSession(
        scope, repository, clock = { time.now }, sleep = time::sleep, uploads = uploads, sessions = sessions,
    )
    private val take = RecordedTake(path = "rec/practice-1.m4a", key = "take-1")

    private fun startWithTake() {
        scope.launch { takeSession.start(SampleSongs.first().copy(scoreVersion = 1), bpm = 60, take = take) }
    }

    @Test
    fun evaluationTimingFollowsResendAndRetryThroughFirstDone() {
        val events = mutableListOf<PerformanceEvent>()
        val previousSink = PerformanceLog.sink
        PerformanceLog.sink = { events += it }
        val visit = PerformanceVisit(PerformanceFlow(), PerfTarget.ANALYSIS)
        val uploadIds = mutableListOf<String>()
        val sender = object : RecordingUploader {
            override suspend fun upload(sessionId: String, path: String, onProgress: (Float) -> Unit): UploadOutcome {
                uploadIds += checkNotNull(currentCoroutineContext()[EvaluationTiming]).id
                time.advanceBy(125)
                return if (uploadIds.size == 1) UploadOutcome.Unreachable else UploadOutcome.Uploaded
            }
        }
        val timedUploads = RecordingUploads(
            sender, FakeKeyValueStore(), FakeRecordingFiles(take.path), { uploadNow },
            SignedInMember(FakeKeyValueStore()).apply { set("A") },
        )
        sessions.onCreate = { time.advanceBy(50) }
        val measured = AnalysisSession(
            CoroutineScope(Dispatchers.Unconfined + visit), repository,
            clock = { time.now }, sleep = time::sleep, uploads = timedUploads, sessions = sessions,
        )

        try {
            scope.launch { measured.start(SampleSongs.first().copy(scoreVersion = 1), bpm = 60, take = take) }
            assertEquals(UploadProblem.CanResend, measured.state.value.upload)
            assertEquals(0, repository.queries)

            measured.resend()
            assertEquals(1, repository.queries)
            measured.pause()
            measured.resume()
            assertEquals(2, repository.queries)

            repository.status = { AnalysisStatus.Failed(AnalysisFailure.ServerError) }
            time.advanceBy(1_000)
            repository.retryOutcome = {
                time.advanceBy(90)
                error("접수 응답 유실")
            }
            measured.retry()
            assertEquals(AnalysisFailure.ServerError, measured.state.value.failure)
            val acceptedId = assertNotNull(repository.retryTimingIds.single())
            repository.retryOutcome = {
                time.advanceBy(40)
                RetryOutcome.Accepted(started = false, timingId = acceptedId)
            }
            repository.status = { AnalysisStatus.Done("41", timingId = acceptedId) }
            measured.retry()
            measured.resume()
            assertEquals("41", measured.state.value.evaluationId)

            val resultVisit = PerformanceVisit(visit.flow, PerfTarget.RESULT)
            resultVisit.milestone(PerfPhase.SCORE_DRAWN)
            resultVisit.milestone(PerfPhase.SCORE_DRAWN)
            val stages = events.filter { it.phase != null }
            assertEquals(
                listOf(PerfPhase.SESSION_CREATE, PerfPhase.RECORDING_UPLOAD, PerfPhase.RECORDING_UPLOAD,
                    PerfPhase.FIRST_STATUS_REQUEST, PerfPhase.ANALYSIS_RETRY, PerfPhase.ANALYSIS_RETRY,
                    PerfPhase.ANALYSIS_READY, PerfPhase.SCORE_DRAWN),
                stages.map { it.phase },
            )
            assertEquals(1, stages.map { it.flow }.distinct().size)
            assertEquals(50L, stages.first().durationMs)
            val uploadEvents = stages.filter { it.phase == PerfPhase.RECORDING_UPLOAD }
            assertEquals(listOf(125L, 125L), uploadEvents.map { it.durationMs })
            assertEquals(listOf(PerfReason.FAILED, PerfReason.READY), uploadEvents.map { it.outcome })
            assertEquals(listOf(PerfReason.INITIAL, PerfReason.RETRY), uploadEvents.map { it.reason })
            assertEquals(uploadIds, uploadEvents.map { it.evaluationTimingId })
            assertEquals(1, uploadIds.distinct().size)
            val retryEvents = stages.filter { it.phase == PerfPhase.ANALYSIS_RETRY }
            assertEquals(listOf(90L, 40L), retryEvents.map { it.durationMs })
            assertEquals(listOf(PerfReason.FAILED, PerfReason.ALREADY_PENDING), retryEvents.map { it.outcome })
            assertNotEquals(uploadIds.first(), acceptedId)
            assertNotEquals(acceptedId, assertNotNull(repository.retryTimingIds.last()))
            assertEquals(listOf(acceptedId, acceptedId), retryEvents.map { it.evaluationTimingId })
            assertEquals(acceptedId, stages.single { it.phase == PerfPhase.ANALYSIS_READY }.evaluationTimingId)
        } finally {
            measured.pause()
            PerformanceLog.sink = previousSink
        }
    }

    @Test
    fun takeCreatesSessionUploadsAndPollsThatSession() {
        startWithTake()
        assertEquals(listOf(Triple(SampleSongs.first().id, 60, "take-1")), sessions.calls)
        assertEquals(listOf("41" to "kept/practice-1.m4a"), uploader.calls)
        assertEquals(listOf("41"), repository.queriedIds)
        assertNull(takeSession.state.value.upload)
    }

    @Test
    fun sectionTakeCreatesSectionSessionWithParentAndResendsTheSameSection() {
        val section = SessionSection(startNoteIdx = 12, endNoteIdx = 27, parentSessionId = "30")
        sessions.results += SessionStart.Unreachable
        scope.launch { takeSession.start(SampleSongs.first().copy(scoreVersion = 1), bpm = 48, take = take, section = section) }
        assertEquals(UploadProblem.CanResend, takeSession.state.value.upload)

        takeSession.resend()
        assertEquals(listOf<SessionSection?>(section, section), sessions.sections)
        assertEquals(listOf("41" to "kept/practice-1.m4a"), uploader.calls)
        assertEquals(listOf("41"), repository.queriedIds)
    }

    @Test
    fun unreachableSessionCreationOffersResendWithSameKey() {
        sessions.results += SessionStart.Unreachable
        startWithTake()
        assertEquals(UploadProblem.CanResend, takeSession.state.value.upload)
        assertEquals(0, uploader.calls.size)

        takeSession.resend()
        assertEquals(listOf("take-1", "take-1"), sessions.calls.map { it.third })
        assertEquals(listOf("41" to "kept/practice-1.m4a"), uploader.calls)
        assertNull(takeSession.state.value.upload)
        assertEquals(listOf("41"), repository.queriedIds)
    }

    @Test
    fun rejectedSessionCreationOnlyOffersPlayingAgain() {
        sessions.results += SessionStart.Rejected
        startWithTake()
        assertEquals(UploadProblem.Rejected, takeSession.state.value.upload)
        takeSession.resend()
        assertEquals(1, sessions.calls.size)
        assertEquals(0, uploader.calls.size)
    }

    @Test
    fun pendingRecordingFromLastRunIsSentForItsSession() {
        uploader.outcomes += UploadOutcome.Unreachable
        scope.launch { uploads.send(PendingRecording("41", "rec/practice-1.m4a", startedAtMillis = 0L, songId = "arirang", bpm = 60, memberId = "A")) }

        scope.launch { takeSession.startPending(SampleSongs.first(), bpm = 60, sessionId = "41") }
        assertNull(takeSession.state.value.upload)
        assertEquals(listOf("41", "41"), uploader.calls.map { it.first })
        assertEquals(listOf("41"), repository.queriedIds)
    }

    @Test
    fun pendingRecordingThatIsGoneOnlyOffersPlayingAgain() {
        scope.launch { takeSession.startPending(SampleSongs.first(), bpm = 60, sessionId = "41") }
        assertEquals(UploadProblem.Missing, takeSession.state.value.upload)
        assertEquals(0, uploader.calls.size)
    }
}

/** 세션 생성 결과를 차례로 돌려준다. 결과가 없으면 세션 "41"을 만든다. */
private class FakeSessions : PracticeSessionRepository {
    val results = ArrayDeque<SessionStart>()
    val calls = mutableListOf<Triple<String, Int, String>>()
    val sections = mutableListOf<SessionSection?>()
    var onCreate: () -> Unit = {}

    override suspend fun create(songId: String, bpm: Int, idempotencyKey: String, section: SessionSection?): SessionStart {
        onCreate()
        calls += Triple(songId, bpm, idempotencyKey)
        sections += section
        return results.removeFirstOrNull() ?: SessionStart.Created("41", startedAtMillis = 0L, memberId = "A")
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
    val queriedIds = mutableListOf<String>()
    var retries = 0
    val retryTimingIds = mutableListOf<String?>()

    override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String {
        requestGate?.await()
        requestError?.let { throw it }
        return "analysis-1"
    }

    override suspend fun analysisStatus(analysisId: String): AnalysisStatus {
        queries++
        queriedIds += analysisId
        if (hangs) awaitCancellation()
        return status()
    }

    var retryOutcome: () -> RetryOutcome = { RetryOutcome.Started }

    override suspend fun retryAnalysis(analysisId: String): RetryOutcome {
        retries++
        retryTimingIds += currentCoroutineContext()[EvaluationTiming]?.id
        return retryOutcome()
    }

    override suspend fun result(evaluationId: String): EvaluationResult? = null
}

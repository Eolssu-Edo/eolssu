package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class NotifyingSheetAudioRepositoryTest {
    @Test fun completionContinuesWithoutTheRequestingScreenAndIsAnnouncedOnce() = runTest {
        val api = AudioApi()
        val member = member()
        val repository = NotifyingSheetAudioRepository(api, member)
        backgroundScope.launch { repository.monitor() }
        repository.request("12")
        runCurrent()
        api.current = audio(SheetAudioStatus.Done)
        advanceTimeBy(1_000); runCurrent()
        val notice = repository.completions.first().single()
        assertEquals("12", notice.sheetId)
        assertEquals(member.session.value, notice.session)
        repository.consume(notice)
        repeat(3) { repository.status("12") }
        repository.request("12") // server reuses the completed file
        assertTrue(repository.completions.first().isEmpty())
    }

    @Test fun historicalDoneAndRefreshedSignedLinksDoNotAnnounceButNewProgressDoes() = runTest {
        val api = AudioApi().apply { current = audio(SheetAudioStatus.Done) }
        val repository = NotifyingSheetAudioRepository(api, member())
        repository.status("12")
        repository.request("12")
        assertTrue(repository.completions.first().isEmpty())
        api.current = audio(SheetAudioStatus.Running)
        repository.status("12") // discover a conversion started before this screen opened
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        repeat(3) {
            api.current = api.current.copy(audio = api.current.audio?.copy(url = "https://example.test/$it.m4a"))
            repository.status("12")
        }
        assertEquals(1, repository.completions.first().size)
    }

    @Test fun failureAndDeletionDoNotSucceedAndSameSettingsRetryIsANewAttempt() = runTest {
        val api = AudioApi()
        val repository = NotifyingSheetAudioRepository(api, member())
        repository.request("12")
        api.current = audio(SheetAudioStatus.Failed)
        repository.status("12")
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        assertTrue(repository.completions.first().isEmpty())
        api.current = audio(SheetAudioStatus.Queued)
        repository.request("12")
        api.lookup = SheetAudioLookup.None
        repository.status("12")
        api.lookup = null
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        assertTrue(repository.completions.first().isEmpty())
        api.current = audio(SheetAudioStatus.Queued)
        repository.request("12")
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        assertEquals(1, repository.completions.first().size)
    }

    @Test fun mismatchedResultIsNotTheObservedConversion() = runTest {
        val api = AudioApi()
        val repository = NotifyingSheetAudioRepository(api, member())
        repository.request("12")
        api.current = audio(SheetAudioStatus.Done).copy(scoreVersion = "1")
        repository.status("12")
        assertTrue(repository.completions.first().isEmpty())
        api.current = audio(SheetAudioStatus.Queued)
        repository.request("12")
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        assertEquals(1, repository.completions.first().size)
    }

    @Test fun accountChangesAndSameMemberReloginDiscardPendingAndQueuedCompletions() = runTest {
        val api = AudioApi()
        val member = member()
        val repository = NotifyingSheetAudioRepository(api, member)
        repository.request("12")
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        val old = repository.completions.first().single()
        member.set("8")
        assertTrue(repository.completions.first().isEmpty())
        assertTrue(!repository.isCurrent(old))
        api.current = audio(SheetAudioStatus.Queued)
        repository.request("12")
        member.set(null)
        member.set("9")
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        assertTrue(repository.completions.first().isEmpty())
        api.current = audio(SheetAudioStatus.Running)
        repository.request("12")
        api.current = audio(SheetAudioStatus.Done)
        repository.status("12")
        assertEquals("9", repository.completions.first().single().session.memberId)
    }

    @Test fun responseArrivingAfterAccountChangeCannotPublishOldCompletion() = runTest {
        val api = AudioApi()
        val member = member()
        val repository = NotifyingSheetAudioRepository(api, member)
        repository.request("12")
        val response = CompletableDeferred<Unit>()
        api.beforeStatus = { response.await() }
        api.current = audio(SheetAudioStatus.Done)
        val lookup = backgroundScope.launch { repository.status("12") }
        runCurrent()
        member.set("9")
        response.complete(Unit)
        runCurrent()
        assertTrue(lookup.isCancelled)
        assertTrue(repository.completions.first().isEmpty())
    }

    @Test fun pausedMonitorResumesObservedWorkWithoutReplayingConsumedCompletion() = runTest {
        val api = AudioApi()
        val repository = NotifyingSheetAudioRepository(api, member())
        repository.request("12")
        val monitor = backgroundScope.launch { repository.monitor() }
        runCurrent()
        monitor.cancelAndJoin()
        api.current = audio(SheetAudioStatus.Done)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0, api.statusCalls)
        backgroundScope.launch { repository.monitor() }
        runCurrent()
        advanceTimeBy(1_000); runCurrent()
        repository.consume(repository.completions.first().single())
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, api.statusCalls)
        assertTrue(repository.completions.first().isEmpty())
    }

    @Test fun fiveUnavailableRepliesStopPollingAndExplicitRediscoveryCanResume() = runTest {
        val api = AudioApi().apply { lookup = SheetAudioLookup.Unavailable }
        val repository = NotifyingSheetAudioRepository(api, member())
        repository.request("12")
        backgroundScope.launch { repository.monitor() }
        runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(5, api.statusCalls)
        assertTrue(repository.completions.first().isEmpty())
        api.lookup = null
        repository.status("12")
        api.current = audio(SheetAudioStatus.Done)
        runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, repository.completions.first().size)
    }

    @Test fun concurrentScreenAndAppQueriesCannotRearmAnAlreadyCompletedAttempt() = runTest {
        val releaseRunning = CompletableDeferred<Unit>()
        var calls = 0
        val api = object : SheetAudioRepository {
            override suspend fun request(sheetId: String, settings: SheetAudioSettings) = SheetAudioRequest.Accepted(audio(SheetAudioStatus.Queued))
            override suspend fun status(sheetId: String): SheetAudioLookup = if (++calls == 1) {
                releaseRunning.await()
                SheetAudioLookup.Found(audio(SheetAudioStatus.Running))
            } else SheetAudioLookup.Found(audio(SheetAudioStatus.Done))
        }
        val repository = NotifyingSheetAudioRepository(api, member())
        repository.request("12")
        val screen = backgroundScope.launch { repository.status("12") }
        runCurrent()
        val app = backgroundScope.launch { repository.status("12") }
        runCurrent()
        releaseRunning.complete(Unit)
        runCurrent()
        screen.join(); app.join()
        repository.status("12")
        assertEquals(1, repository.completions.first().size, "late RUNNING must not turn a completed attempt back into new work")
    }

    private fun member() = SignedInMember(FakeKeyValueStore()).apply { set("8") }

    private class AudioApi : SheetAudioRepository {
        var current = audio(SheetAudioStatus.Queued)
        var lookup: SheetAudioLookup? = null
        var statusCalls = 0
        var beforeStatus: suspend () -> Unit = {}
        override suspend fun request(sheetId: String, settings: SheetAudioSettings) = SheetAudioRequest.Accepted(current)
        override suspend fun status(sheetId: String): SheetAudioLookup {
            ++statusCalls
            beforeStatus()
            return lookup ?: SheetAudioLookup.Found(current)
        }
    }

    companion object {
        private fun audio(status: SheetAudioStatus) = SheetAudio(
            "12", status, "2", "2", false, "1", false, 60, pollAfterMs = 1_000,
            audio = if (status == SheetAudioStatus.Done) SheetAudioFile("https://example.test/a.m4a", Long.MAX_VALUE,
                "audio/mp4", 1, "a.m4a") else null,
        )
    }
}

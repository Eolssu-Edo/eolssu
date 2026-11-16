package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class NotifyingSheetConversionRepositoryTest {
    @Test fun uploadCompletionIsObservedWithoutReturningToTheUploadingScreen() = runTest {
        val api = ConversionApi()
        val member = SignedInMember(FakeKeyValueStore()).apply { set("8") }
        val repository = NotifyingSheetConversionRepository(api, api, member)
        backgroundScope.launch { repository.monitor() }
        repository.upload(input, "upload-1")
        runCurrent()
        api.current = SheetConversion("12", SheetConversionStatus.Done, issueCount = 4)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf("12"), repository.completions.first().map { it.sheetId },
            "accepted PDF/image conversion must notify even without opening its status screen")
    }

    @Test fun historicalDoneIsQuietAndRepeatedReadsAndUploadReplayDoNotRepeatCompletion() = runTest {
        val api = ConversionApi().apply { current = SheetConversion("12", SheetConversionStatus.Done) }
        val repository = repository(api)
        repository.status("12")
        repository.upload(input, "old-upload")
        assertTrue(repository.completions.first().isEmpty())
        api.current = SheetConversion("12", SheetConversionStatus.Running)
        repository.status("12") // discover an actual new attempt, including one started elsewhere
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        val notice = repository.completions.first().single()
        repository.consume(notice)
        repeat(3) { repository.status("12"); repository.upload(input, "old-upload") }
        assertTrue(repository.completions.first().isEmpty())
    }

    @Test fun failureIsQuietAndAnAcceptedRetryCanComplete() = runTest {
        val api = ConversionApi()
        val repository = repository(api)
        repository.upload(input, "upload-1")
        api.current = SheetConversion("12", SheetConversionStatus.Failed)
        repository.status("12")
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        assertTrue(repository.completions.first().isEmpty())
        api.current = SheetConversion("12", SheetConversionStatus.Queued)
        repository.retry("12")
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        assertEquals(1, repository.completions.first().size)
    }

    @Test fun deletionIsFinalAndLateDoneCannotResurrectItsNotification() = runTest {
        val api = ConversionApi()
        val repository = repository(api)
        repository.upload(input, "upload-1")
        api.lookup = SheetConversionLookup.NotFound
        repository.status("12")
        api.lookup = null
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        assertTrue(repository.completions.first().isEmpty())
        api.current = SheetConversion("12", SheetConversionStatus.Running)
        repository.status("12")
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        assertTrue(repository.completions.first().isEmpty())
    }

    @Test fun accountSwitchAndSameMemberReloginClearTrackingAndLateReplies() = runTest {
        val api = ConversionApi()
        val member = SignedInMember(FakeKeyValueStore()).apply { set("8") }
        val repository = NotifyingSheetConversionRepository(api, api, member)
        repository.upload(input, "upload-1")
        val release = CompletableDeferred<Unit>()
        api.beforeStatus = { release.await() }
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        val late = backgroundScope.launch { repository.status("12") }
        runCurrent()
        member.set(null); member.set("8")
        release.complete(Unit); runCurrent()
        assertTrue(late.isCancelled)
        assertTrue(repository.completions.first().isEmpty())
        api.beforeStatus = {}
        api.current = SheetConversion("12", SheetConversionStatus.Running)
        repository.status("12")
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        val old = repository.completions.first().single()
        member.set("9")
        assertTrue(!repository.isCurrent(old))
        assertTrue(repository.completions.first().isEmpty())
    }

    @Test fun pauseKeepsWorkAndConsumesOnlyOnceAfterResume() = runTest {
        val api = ConversionApi()
        val repository = repository(api)
        repository.upload(input, "upload-1")
        val monitor = backgroundScope.launch { repository.monitor() }
        runCurrent(); monitor.cancelAndJoin()
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0, api.statusCalls)
        backgroundScope.launch { repository.monitor() }
        runCurrent(); advanceTimeBy(3_000); runCurrent()
        repository.consume(repository.completions.first().single())
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, api.statusCalls)
        assertTrue(repository.completions.first().isEmpty())
    }

    @Test fun repeatedLookupFailuresStopUntilScreenRediscovery() = runTest {
        val api = ConversionApi().apply { lookup = SheetConversionLookup.Unavailable }
        val repository = repository(api)
        repository.upload(input, "upload-1")
        backgroundScope.launch { repository.monitor() }
        runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(5, api.statusCalls)
        assertTrue(repository.completions.first().isEmpty())
        api.lookup = null
        repository.status("12")
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertEquals(1, repository.completions.first().size)
    }

    @Test fun simultaneousScreenAndAppQueriesDoNotRearmAnOlderRunningResponse() = runTest {
        val api = ConversionApi()
        val repository = repository(api)
        repository.upload(input, "upload-1")
        val release = CompletableDeferred<Unit>()
        api.beforeStatus = { if (api.statusCalls == 1) release.await() }
        val screen = backgroundScope.launch { repository.status("12") }
        runCurrent()
        val app = backgroundScope.launch { repository.status("12") }
        runCurrent()
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        release.complete(Unit); runCurrent()
        screen.join(); app.join()
        assertEquals(1, repository.completions.first().size)
    }

    @Test fun waitingScreenAcknowledgesItsCompletionSoTheAppBannerStaysQuiet() = runTest {
        val api = ConversionApi()
        val repository = repository(api)
        repository.upload(input, "upload-1")
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        val notice = repository.completions.first().single()
        val invalidated = backgroundScope.launch { repository.awaitInvalidation(notice) }
        runCurrent()
        repository.acknowledgeCompletion("12")
        runCurrent()
        assertTrue(repository.completions.first().isEmpty(), "대기 화면이 편집으로 데려갔으면 완료 알림을 남기지 않는다")
        assertTrue(!repository.isCurrent(notice), "이미 띄우던 배너도 더는 유효하지 않다")
        assertTrue(invalidated.isCompleted, "띄우던 배너를 닫는다")

        api.current = SheetConversion("12", SheetConversionStatus.Running)
        repository.status("12")
        api.current = SheetConversion("12", SheetConversionStatus.Done)
        repository.status("12")
        assertTrue(repository.isCurrent(repository.completions.first().single()), "다음 변환의 완료는 다시 알린다")
    }

    private fun repository(api: ConversionApi) = NotifyingSheetConversionRepository(api, api,
        SignedInMember(FakeKeyValueStore()).apply { set("8") })

    private class ConversionApi : SheetConversionRepository, SheetImportRepository {
        var current = SheetConversion("12", SheetConversionStatus.Queued, pollAfterMs = 1_000)
        var lookup: SheetConversionLookup? = null
        var statusCalls = 0
        var beforeStatus: suspend () -> Unit = {}
        override suspend fun upload(input: SheetImportInput, key: String) = SheetImportResult.Accepted("12")
        override suspend fun status(sheetId: String): SheetConversionLookup {
            ++statusCalls
            val response = lookup ?: SheetConversionLookup.Found(current)
            beforeStatus()
            return response
        }
        override suspend fun retry(sheetId: String) = SheetConversionRetry.Accepted(current)
    }

    companion object {
        private val input = SheetImportInput("알림 확인", "STAFF", "1", null, null, emptyList())
    }
}

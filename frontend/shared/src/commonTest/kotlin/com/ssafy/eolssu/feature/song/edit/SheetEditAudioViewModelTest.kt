package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.data.song.SheetWorkingCopy
import com.ssafy.eolssu.data.song.SheetWrite
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.data.song.SheetAudio
import com.ssafy.eolssu.data.song.SheetAudioFailure
import com.ssafy.eolssu.data.song.SheetAudioFile
import com.ssafy.eolssu.data.song.SheetAudioLookup
import com.ssafy.eolssu.data.song.SheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudioRequest
import com.ssafy.eolssu.data.song.SheetAudioSettings
import com.ssafy.eolssu.data.song.SheetAudioStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** 미리보기의 음원 흐름: 요청 → 폴링 → 준비·실패, 만료 URL 재조회, 악보 바뀜 재생성. 서버는 대본대로 답하는 가짜 저장소다. */
@OptIn(ExperimentalCoroutinesApi::class)
class SheetEditAudioViewModelTest {
    private class FakeSheetAudioRepository : SheetAudioRepository {
        val requests = mutableListOf<Pair<String, SheetAudioSettings>>()
        var statusCalls = 0
        var onRequest: (SheetAudioSettings) -> SheetAudioRequest = { SheetAudioRequest.Unavailable }

        /** 조회 순서대로 꺼내 쓰고 마지막 답은 반복한다. */
        val statuses = ArrayDeque<SheetAudioLookup>()

        override suspend fun request(sheetId: String, settings: SheetAudioSettings): SheetAudioRequest {
            requests += sheetId to settings
            return onRequest(settings)
        }

        override suspend fun status(sheetId: String): SheetAudioLookup {
            statusCalls++
            return if (statuses.size > 1) statuses.removeFirst() else statuses.first()
        }
    }

    private fun file(expiresAt: Long = 10_000_000, url: String = "https://r2.example/12.m4a") =
        SheetAudioFile(url, expiresAt, "audio/mp4", 1000, "a.m4a", sha256 = "ab".repeat(32), durationMs = 18550)

    private fun audio(
        status: SheetAudioStatus,
        outdated: Boolean = false,
        files: Boolean = status == SheetAudioStatus.Done,
        failure: SheetAudioFailure? = null,
        pollAfterMs: Long = 1000,
        expiresAt: Long = 10_000_000,
        url: String = "https://r2.example/12.m4a",
    ) = SheetAudio(
        sheetId = "12", status = status, scoreVersion = "2", currentScoreVersion = if (outdated) "3" else "2", outdated = outdated,
        instrumentId = "1", withJangdan = true, bpm = 60, failureCode = failure, pollAfterMs = pollAfterMs.takeIf { status.inProgress },
        audio = if (files) file(expiresAt, url) else null,
        midi = if (files) file(expiresAt, "https://r2.example/12.mid").copy(contentType = "audio/midi", fileName = "a.mid", sha256 = null) else null,
    )

    private fun found(status: SheetAudioStatus, outdated: Boolean = false, files: Boolean = status == SheetAudioStatus.Done, failure: SheetAudioFailure? = null) =
        SheetAudioLookup.Found(audio(status, outdated, files, failure))

    private fun TestScope.viewModel(repository: FakeSheetAudioRepository, source: EditSource = EditSource.Mine("12"),
        now: () -> Long = { 0L }, documents: SheetDocumentRepository? = null): SheetEditViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return SheetEditViewModel(source, EditView.Jeongganbo, SampleMySheetRepository(), repository, now = now, documents = documents)
    }

    @Test fun previewWithoutGeneratedAudioDoesNotClaimPlayback() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += SheetAudioLookup.None }
        val vm = viewModel(repository)
        try {
            runCurrent()
            assertNull(vm.previewAudioFile())
            assertNull(vm.previewPlaybackId())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun previewUsesOnlyTheCurrentSavedScoreAndJangdanSettings() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += found(SheetAudioStatus.Done) }
        val documents = object : SheetDocumentRepository {
            override suspend fun get(sheetId: String) = SheetDocument(
                sheetId, "미리듣기", "1", 60, "2", smallScoreData(), null, "EVALUABLE", "DONE", emptyList(),
            )
            override suspend fun save(sheetId: String, copy: SheetWorkingCopy, draft: Boolean) = SheetWrite.Failed
        }
        val vm = viewModel(repository, documents = documents)
        try {
            runCurrent()
            assertEquals("https://r2.example/12.m4a", vm.previewAudioFile()?.url)
            vm.toggleWithJangdan()
            assertNull(vm.previewAudioFile())
            assertEquals(SheetEditMessage.PreviewNeedsAudio, vm.uiState.value.message)
            assertTrue(vm.uiState.value.audioSettingsChanged)
            repository.onRequest = { settings ->
                SheetAudioRequest.Accepted(audio(SheetAudioStatus.Done).copy(withJangdan = settings.withJangdan))
            }
            vm.requestAudio(); runCurrent()
            assertEquals(false, repository.requests.single().second.withJangdan)
            assertNotNull(vm.previewAudioFile())
            vm.toggleWithJangdan()

            repository.statuses.clear()
            repository.statuses += found(SheetAudioStatus.Done, outdated = true)
            vm.refreshAudio(); runCurrent()
            assertNull(vm.previewAudioFile())
            assertNotNull(vm.audioFile(SheetAudioKind.M4a), "옛 음원은 기존 음원 칸에서 계속 쓸 수 있다")

            vm.selectCell(2)
            vm.put(ScoreNote.Pitch(Yul.Taeju))
            assertNull(vm.previewAudioFile())
            assertEquals(SheetEditMessage.AudioNeedsSave, vm.uiState.value.message)
        } finally {
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test fun openingASavedSheetReadsItsAudioAndKeepsPollingUntilDone() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += found(SheetAudioStatus.Running); statuses += found(SheetAudioStatus.Done) }
        val vm = viewModel(repository)
        try {
            runCurrent()
            assertIs<SheetAudioUi.Generating>(vm.uiState.value.audio)
            assertNull(vm.uiState.value.message, "화면에 들어와 읽은 상태는 알리지 않는다")
            advanceTimeBy(1000); runCurrent()
            val ready = assertIs<SheetAudioUi.Ready>(vm.uiState.value.audio)
            assertEquals("https://r2.example/12.m4a", ready.audio.audio?.url)
            assertEquals(SheetEditMessage.AudioReady, vm.uiState.value.message)
            assertEquals(2, repository.statusCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun requestSendsSettingsThenPollsUntilDoneAndAnnounces() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += SheetAudioLookup.None }
        repository.onRequest = { SheetAudioRequest.Accepted(audio(SheetAudioStatus.Queued, pollAfterMs = 2000)) }
        val vm = viewModel(repository)
        try {
            runCurrent()
            assertEquals(SheetAudioUi.None, vm.uiState.value.audio)
            vm.requestAudio(); runCurrent()
            val (sheetId, settings) = repository.requests.single()
            assertEquals("12", sheetId)
            assertEquals(SheetAudioSettings(instrumentId = null, withJangdan = true, bpm = vm.uiState.value.draft.bpm), settings)
            assertIs<SheetAudioUi.Generating>(vm.uiState.value.audio)
            assertEquals(SheetEditMessage.AudioRequested, vm.uiState.value.message)
            vm.consumeMessage()

            repository.statuses.clear()
            repository.statuses += found(SheetAudioStatus.Running)
            repository.statuses += found(SheetAudioStatus.Done)
            advanceTimeBy(1999); runCurrent()
            assertEquals(1, repository.statusCalls, "pollAfterMs 전에는 묻지 않는다")
            advanceTimeBy(1); runCurrent()
            assertEquals(2, repository.statusCalls)
            assertEquals(SheetAudioStatus.Running, assertIs<SheetAudioUi.Generating>(vm.uiState.value.audio).audio.status)
            advanceTimeBy(1000); runCurrent()
            assertIs<SheetAudioUi.Ready>(vm.uiState.value.audio)
            assertEquals(SheetEditMessage.AudioReady, vm.uiState.value.message)
            assertTrue(vm.uiState.value.audio.latest?.audio != null)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun existingAudioOn200IsReadyAtOnceWithoutPolling() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += SheetAudioLookup.None }
        repository.onRequest = { SheetAudioRequest.Accepted(audio(SheetAudioStatus.Done)) }
        val vm = viewModel(repository)
        try {
            runCurrent()
            vm.requestAudio(); runCurrent()
            assertIs<SheetAudioUi.Ready>(vm.uiState.value.audio)
            assertEquals(SheetEditMessage.AudioReady, vm.uiState.value.message)
            advanceTimeBy(10_000); runCurrent()
            assertEquals(1, repository.statusCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun outdatedAudioCanBeRegeneratedWhileThePreviousFileStaysPlayable() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += found(SheetAudioStatus.Done, outdated = true) }
        repository.onRequest = { SheetAudioRequest.Accepted(audio(SheetAudioStatus.Queued, outdated = true, files = true)) }
        val vm = viewModel(repository)
        try {
            runCurrent()
            val ready = assertIs<SheetAudioUi.Ready>(vm.uiState.value.audio)
            assertTrue(ready.audio.outdated)
            assertTrue(vm.uiState.value.audio.canRequest, "악보가 바뀌면 다시 만들 수 있다")
            vm.requestAudio(); runCurrent()
            val generating = assertIs<SheetAudioUi.Generating>(vm.uiState.value.audio)
            assertNotNull(generating.audio.audio, "다시 만드는 동안 이전 파일을 계속 쓸 수 있다")
            assertNotNull(vm.audioFile(SheetAudioKind.M4a))
            assertEquals(1, repository.statusCalls, "만료 전이라 이전 파일 URL을 그대로 쓴다")

            repository.statuses.clear()
            repository.statuses += SheetAudioLookup.Found(audio(SheetAudioStatus.Done, url = "https://r2.example/12-v3.m4a"))
            advanceTimeBy(1000); runCurrent()
            val regenerated = assertIs<SheetAudioUi.Ready>(vm.uiState.value.audio)
            assertEquals("https://r2.example/12-v3.m4a", regenerated.audio.audio?.url, "새 음원이 이전 파일을 대신한다")
            assertEquals(SheetEditMessage.AudioReady, vm.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun requestErrorsRestoreThePreviousAudioAndExplain() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += found(SheetAudioStatus.Done, outdated = true) }
        val vm = viewModel(repository)
        try {
            runCurrent()
            val expected = mapOf(
                SheetAudioRequest.InProgress to SheetEditMessage.AudioInProgress,
                SheetAudioRequest.TooLong to SheetEditMessage.AudioTooLong,
                SheetAudioRequest.NotReady to SheetEditMessage.AudioNotReady,
                SheetAudioRequest.NotFound to SheetEditMessage.AudioNotFound,
                SheetAudioRequest.InvalidRequest to SheetEditMessage.AudioInvalid,
                SheetAudioRequest.TooManyRequests to SheetEditMessage.AudioBusy,
                SheetAudioRequest.Unavailable to SheetEditMessage.AudioBusy,
            )
            for ((result, message) in expected) {
                repository.onRequest = { result }
                vm.consumeMessage()
                vm.requestAudio(); runCurrent()
                assertEquals(message, vm.uiState.value.message, "$result")
                val ready = assertIs<SheetAudioUi.Ready>(vm.uiState.value.audio, "$result")
                assertTrue(ready.audio.outdated)
            }
            assertEquals(expected.size, repository.requests.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedGenerationKeepsThePreviousFileAndAllowsRetry() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += SheetAudioLookup.None }
        repository.onRequest = { SheetAudioRequest.Accepted(audio(SheetAudioStatus.Queued)) }
        val vm = viewModel(repository)
        try {
            runCurrent()
            vm.requestAudio(); runCurrent()
            repository.statuses.clear()
            repository.statuses += found(SheetAudioStatus.Failed, files = true, failure = SheetAudioFailure.TooLarge)
            advanceTimeBy(1000); runCurrent()
            val failed = assertIs<SheetAudioUi.Failed>(vm.uiState.value.audio)
            assertEquals(SheetAudioFailure.TooLarge, failed.audio.failureCode)
            assertNotNull(failed.audio.audio, "실패해도 이전 파일은 남는다")
            assertEquals(SheetEditMessage.AudioFailed, vm.uiState.value.message)
            assertTrue(vm.uiState.value.audio.canRequest)
            advanceTimeBy(10_000); runCurrent()
            assertEquals(2, repository.statusCalls, "실패 뒤에는 더 묻지 않는다")
        } finally { Dispatchers.resetMain() }
    }

    @Test fun expiringUrlIsRefreshedBeforePlaybackOrDownload() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += found(SheetAudioStatus.Done) }
        var now = 1_000_000L
        val vm = viewModel(repository, now = { now })
        try {
            runCurrent()
            assertEquals("https://r2.example/12.m4a", vm.audioFile(SheetAudioKind.M4a)?.url)
            assertEquals(1, repository.statusCalls, "만료 전에는 다시 묻지 않는다")

            now = 10_000_000 - 20_000
            repository.statuses.clear()
            repository.statuses += SheetAudioLookup.Found(audio(SheetAudioStatus.Done, expiresAt = 20_000_000, url = "https://r2.example/12-fresh.m4a"))
            assertEquals("https://r2.example/12-fresh.m4a", vm.audioFile(SheetAudioKind.M4a)?.url)
            assertEquals(2, repository.statusCalls)
            assertEquals("a.mid", vm.audioFile(SheetAudioKind.Midi)?.fileName)
            assertEquals(2, repository.statusCalls, "새 URL은 두 파일에 함께 온다")

            repository.statuses.clear()
            repository.statuses += SheetAudioLookup.Unavailable
            now = 20_000_000
            assertNull(vm.audioFile(SheetAudioKind.M4a))
            assertEquals(SheetEditMessage.AudioBusy, vm.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun pollingGivesUpAfterRepeatedMissingResponsesUntilRefreshed() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += SheetAudioLookup.None }
        repository.onRequest = { SheetAudioRequest.Accepted(audio(SheetAudioStatus.Queued)) }
        val vm = viewModel(repository)
        try {
            runCurrent()
            vm.requestAudio(); runCurrent()
            repository.statuses.clear()
            repository.statuses += SheetAudioLookup.Unavailable
            advanceTimeBy(4000); runCurrent()
            assertIs<SheetAudioUi.Generating>(vm.uiState.value.audio)
            advanceTimeBy(1000); runCurrent()
            assertIs<SheetAudioUi.Unknown>(vm.uiState.value.audio)
            assertEquals(SheetEditMessage.AudioStatusUnknown, vm.uiState.value.message)
            assertEquals(6, repository.statusCalls)
            advanceTimeBy(10_000); runCurrent()
            assertEquals(6, repository.statusCalls, "포기한 뒤에는 묻지 않는다")

            repository.statuses.clear()
            repository.statuses += found(SheetAudioStatus.Done)
            vm.refreshAudio(); runCurrent()
            assertIs<SheetAudioUi.Ready>(vm.uiState.value.audio)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun sampleSheetsAndUnsavedDraftsOnlyShowNoticesWithoutServerCalls() = runTest {
        val repository = FakeSheetAudioRepository().apply { statuses += found(SheetAudioStatus.Done) }
        val sample = viewModel(repository, EditSource.Mine("sample-arirang"))
        try {
            runCurrent()
            sample.requestAudio(); runCurrent()
            assertEquals(SheetEditMessage.ComingSoon, sample.uiState.value.message)
            assertEquals(SheetAudioUi.None, sample.uiState.value.audio)
            assertNull(sample.audioFile(SheetAudioKind.M4a))
        } finally { Dispatchers.resetMain() }

        val copy = viewModel(repository, EditSource.CopySong("arirang"))
        try {
            runCurrent()
            copy.requestAudio(); runCurrent()
            assertEquals(SheetEditMessage.AudioNeedsSave, copy.uiState.value.message)
            copy.consumeMessage()
            copy.requestSave(); copy.consumeMessage()
            copy.requestAudio(); runCurrent()
            assertEquals(SheetEditMessage.ComingSoon, copy.uiState.value.message, "저장 API 연결 전에는 서버 악보가 없다")
        } finally { Dispatchers.resetMain() }
        assertTrue(repository.requests.isEmpty())
        assertEquals(0, repository.statusCalls)
    }
}

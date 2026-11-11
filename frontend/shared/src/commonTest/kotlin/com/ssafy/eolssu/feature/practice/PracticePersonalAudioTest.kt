package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.DeviceSheetAudio
import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.ApiReferenceCatalogRepository
import com.ssafy.eolssu.data.song.ApiSheetDocumentRepository
import com.ssafy.eolssu.data.song.InstrumentCatalog
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SheetAudio
import com.ssafy.eolssu.data.song.SheetAudioFile
import com.ssafy.eolssu.data.song.SheetAudioLookup
import com.ssafy.eolssu.data.song.SheetAudioRepository
import com.ssafy.eolssu.data.song.SheetAudioRequest
import com.ssafy.eolssu.data.song.SheetAudioSettings
import com.ssafy.eolssu.data.song.SheetAudioStatus
import com.ssafy.eolssu.data.song.SheetPracticeRepository
import com.ssafy.eolssu.data.song.smallScoreSong
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PracticePersonalAudioTest {
    @Test fun downloadAppearsBeforePlayAndEnablesLocalPlaybackOnlyAfterSaving() = practiceTest { vm, fixture, player ->
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudioControl)
        assertEquals(emptyList(), fixture.requests)
        vm.startModel()
        runCurrent()
        assertEquals(0, player.loads)
        assertEquals(emptyList(), fixture.requests)

        val downloadFinished = CompletableDeferred<Unit>()
        fixture.downloadFinished = downloadFinished
        vm.downloadPersonalAudio()
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio)
        assertEquals(listOf(SheetAudioSettings(instrumentId = "9", withJangdan = false, bpm = 60)), fixture.requests)
        assertEquals(0, fixture.saves)

        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, fixture.saves)
        assertEquals(PracticePersonalAudio.Downloading, vm.uiState.value.personalAudio)
        vm.startModel()
        assertEquals(0, player.loads)

        downloadFinished.complete(Unit)
        runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(PracticePlayerNotice.PersonalAudioReady, vm.uiState.value.playerNotice)
        assertNull(vm.uiState.value.personalAudioControl)
        assertEquals(0, player.loads)

        vm.startModel()
        runCurrent()
        assertEquals(1, player.loads)
        assertEquals(fixture.saved?.localFile, player.requestedSource?.localFile)
    }

    @Test fun changedScoreRequiresReopeningInsteadOfRetrying() = practiceTest { vm, fixture, _ ->
        fixture.request = SheetAudioRequest.Accepted(fixture.done.copy(currentScoreVersion = "2"))
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.VersionChanged, vm.uiState.value.personalAudio)
        assertEquals(PracticePlayerNotice.PersonalScoreChanged, vm.uiState.value.playerNotice)
        assertEquals(0, fixture.saves)
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(1, fixture.requests.size)
    }

    @Test fun rejectsAnotherVersionsFileEvenWhenTheScoreIsStillCurrent() = practiceTest { vm, fixture, player ->
        fixture.request = SheetAudioRequest.Accepted(fixture.done.copy(scoreVersion = "2"))
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Failed, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.saves)
        vm.startModel()
        runCurrent()
        assertEquals(0, player.loads)
    }

    @Test fun refusesExpiredLinkAndCanRetry() = practiceTest { vm, fixture, _ ->
        fixture.request = SheetAudioRequest.Accepted(fixture.done.copy(audio = fixture.done.audio?.copy(urlExpiresAt = 0)))
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Failed, vm.uiState.value.personalAudio)
        assertEquals(PracticePlayerNotice.PersonalDownloadFailed, vm.uiState.value.playerNotice)
        assertEquals(0, fixture.saves)

        fixture.request = SheetAudioRequest.Accepted(fixture.done)
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(1, fixture.saves)
    }

    @Test fun leavingCancelsWaitingAndReenteringDoesNotRequestAgain() = practiceTest { vm, fixture, _ ->
        vm.downloadPersonalAudio()
        runCurrent()
        vm.onScreenHidden()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, fixture.statusReads)
        assertEquals(0, fixture.saves)

        vm.onScreenShown()
        runCurrent()
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudio)
        assertEquals(1, fixture.requests.size)

        fixture.request = SheetAudioRequest.InProgress
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(1, fixture.statusReads)
        assertEquals(1, fixture.saves)
    }

    private fun practiceTest(block: suspend TestScope.(PracticeViewModel, Fixture, FakeAudioPlayer) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val views = ViewModelStore()
        val client = createHttpClient(MockEngine { error("Only the fake audio repository should be called") }, ServerAddress.Production)
        try {
            val storage = FakeKeyValueStore()
            val member = SignedInMember(storage).apply { set("7") }
            val song = smallScoreSong().copy(id = "42", instrumentId = "9", scoreVersion = 1)
            val fixture = Fixture()
            val repository = SheetPracticeRepository(ApiSheetDocumentRepository(client), fixture,
                ApiReferenceCatalogRepository(client), InstrumentCatalog(client), member, fixture, { testScheduler.currentTime })
            val player = FakeAudioPlayer()
            val vm = PracticeViewModel(song.id, true, SampleSongRepository(), PracticeSettingsStore(storage),
                PracticeTimeMeter({ 0L }), PracticeTimeQueue(storage, null, member, backgroundScope),
                modelAudioRepository = repository, audioPlayer = player, mine = true, member = member,
                loadPersonalSheet = { song }, personalAudio = repository)
            views.put("practice", vm)
            runCurrent()
            block(vm, fixture, player)
        } finally {
            views.clear()
            client.close()
            Dispatchers.resetMain()
        }
    }

    private class Fixture : SheetAudioDeviceStore, SheetAudioRepository {
        val done = SheetAudio("42", SheetAudioStatus.Done, "1", "1", false, "9", false, 60,
            audio = SheetAudioFile("https://example.invalid/song.m4a", 60_000, "audio/mp4", 100, "song.m4a",
                "a".repeat(64), 3_300))
        var request: SheetAudioRequest = SheetAudioRequest.Accepted(done.copy(status = SheetAudioStatus.Queued, audio = null, pollAfterMs = 500))
        val requests = mutableListOf<SheetAudioSettings>()
        var statusReads = 0
        var saves = 0
        var saved: DeviceSheetAudio? = null
        var downloadFinished: CompletableDeferred<Unit>? = null

        override val available = true
        override suspend fun request(sheetId: String, settings: SheetAudioSettings): SheetAudioRequest {
            assertEquals("42", sheetId)
            requests += settings
            return request
        }
        override suspend fun status(sheetId: String): SheetAudioLookup {
            assertEquals("42", sheetId)
            statusReads++
            return SheetAudioLookup.Found(done)
        }
        override suspend fun find(accountId: String, sheetId: String, scoreVersion: String): DeviceSheetAudio? {
            assertEquals(Triple("7", "42", "1"), Triple(accountId, sheetId, scoreVersion))
            return saved
        }
        override suspend fun save(accountId: String, sheetId: String, scoreVersion: String, download: SheetAudioDownload): DeviceSheetAudio {
            assertEquals(Triple("7", "42", "1"), Triple(accountId, sheetId, scoreVersion))
            saves++
            downloadFinished?.await()
            return DeviceSheetAudio(LocalAudioFile("/app/files/42.m4a"), download.instrumentId, download.bpm,
                download.withJangdan, download.sha256, download.durationMs, download.performance).also { saved = it }
        }
        override suspend fun delete(accountId: String, sheetId: String) = error("Unexpected delete")
    }
}

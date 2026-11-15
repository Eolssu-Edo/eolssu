package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.DeviceSheetAudio
import com.ssafy.eolssu.core.audio.LocalAudioFile
import com.ssafy.eolssu.core.audio.SheetAudioDeviceStore
import com.ssafy.eolssu.core.audio.SheetAudioDownload
import com.ssafy.eolssu.core.audio.SheetAudioPerformance
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
import com.ssafy.eolssu.data.song.previewSong
import com.ssafy.eolssu.data.song.previewJson
import com.ssafy.eolssu.data.song.SourceHash
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class PracticePersonalAudioTest {
    @Test fun estimatedLengthIsAvailableBeforeGenerationWithoutChangingTheRhythmClock() = practiceTest(estimatedMs = 6_500.0) { vm, fixture, player ->
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudio)
        assertEquals(7, vm.uiState.value.totalSeconds)
        vm.selectRhythm(); runCurrent()
        assertEquals(3, vm.uiState.value.totalSeconds)
        vm.selectModel(); runCurrent()
        assertEquals(7, vm.uiState.value.totalSeconds)
        val downloadFinished = CompletableDeferred<Unit>()
        fixture.downloadFinished = downloadFinished
        vm.downloadPersonalAudio(); runCurrent()
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio)
        assertEquals(7, vm.uiState.value.totalSeconds)
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Downloading, vm.uiState.value.personalAudio)
        assertEquals(7, vm.uiState.value.totalSeconds)
        downloadFinished.complete(Unit); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(3, vm.uiState.value.totalSeconds, "Legacy audio without a valid sidecar keeps the written fallback")
        assertEquals(0, player.loads)
    }

    @Test fun cachedPerformanceIsReadyBeforePlayAndSurvivesReentry() = practiceTest(performed = true, cached = true) { vm, _, player ->
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(7, vm.uiState.value.totalSeconds)
        assertNotNull(vm.uiState.value.modelPerformance)
        assertFalse(vm.uiState.value.playing)
        assertEquals(0, player.loads)
        vm.seekToFraction(0.75f); runCurrent()
        vm.onScreenHidden(); runCurrent()
        vm.onScreenShown(); runCurrent()
        assertEquals(7, vm.uiState.value.totalSeconds)
        assertEquals(4_875.0, vm.uiState.value.elapsedMs, 0.001)
        assertEquals(0, player.loads)
        vm.startModel(); runCurrent()
        assertEquals(4_875L, player.requestedPosition)
    }

    @Test fun downloadedPerformanceDrivesFirstSeekWithoutPlaying() = practiceTest(performed = true) { vm, fixture, player ->
        fixture.request = SheetAudioRequest.Accepted(fixture.done)
        vm.downloadPersonalAudio(); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(7, vm.uiState.value.totalSeconds)
        assertEquals(0, player.loads)

        vm.seekToFraction(0.75f); runCurrent()
        assertEquals(4_875.0, vm.uiState.value.elapsedMs, 0.001)
        assertEquals(75f, vm.uiState.value.progressPercent, 0.001f)
        assertFalse(vm.uiState.value.playing)
        assertEquals(0, player.loads)
        vm.startModel(); runCurrent()
        assertEquals(4_875L, player.requestedPosition, "First playback must retain the selected performed occurrence")
    }

    @Test fun preparedPerformanceScalesDisplayOnceAndKeepsTheFirstMediaPosition() = practiceTest(performed = true, cached = true) { vm, _, player ->
        vm.openPanel(PracticePanel.Tempo)
        vm.updateDraft { it.copy(step = TempoStep.Slow) }; runCurrent()
        assertEquals(48, vm.uiState.value.bpm)
        assertEquals(8, vm.uiState.value.totalSeconds)
        vm.seekToFraction(0.5f); runCurrent()
        assertEquals(4_062.5, vm.uiState.value.elapsedMs, 0.001)
        assertEquals(50f, vm.uiState.value.progressPercent, 0.001f)
        vm.seekModel(500); runCurrent()
        assertEquals(4_562.5, vm.uiState.value.elapsedMs, 0.001)
        vm.seekModel(-500); runCurrent()
        assertEquals(4_062.5, vm.uiState.value.elapsedMs, 0.001)
        assertEquals(0, player.loads)
        vm.startModel(); runCurrent()
        assertEquals(3_250L, player.requestedPosition)
    }

    @Test fun returningFromPausedRhythmKeepsTheNewScorePosition() = practiceTest(performed = true, cached = true) { vm, _, player ->
        vm.selectModel(); runCurrent()
        assertEquals(7, vm.uiState.value.totalSeconds)
        vm.seekToFraction(0.25f); runCurrent()
        vm.selectRhythm(); runCurrent()
        assertEquals(3, vm.uiState.value.totalSeconds, "Rhythm keeps the written clock")
        vm.seekModel(500); runCurrent()
        assertEquals(7.5, vm.uiState.value.currentTick!!, 0.001)
        vm.selectModel(); runCurrent()
        assertEquals(7, vm.uiState.value.totalSeconds)
        assertEquals(7.5, vm.uiState.value.currentTick!!, 0.001)
        assertEquals(0, player.loads)
        vm.startModel(); runCurrent()
        assertEquals(2_125L, player.requestedPosition)
    }

    @Test fun unsupportedGuidanceReturnsToEditingInsteadOfRetryingGeneration() = practiceTest { vm, fixture, _ ->
        fixture.request = SheetAudioRequest.UnsupportedPitches(com.ssafy.eolssu.data.song.AuthoringPitchRejection("SOGEUM",
            listOf(com.ssafy.eolssu.data.song.AuthoringPitchIssue("c0-j0-r0-u0", "c0-j0", "이", 1, listOf("FINGERING_UNKNOWN"))),
            "UNSUPPORTED_PITCHES"))
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.UnsupportedPitches, vm.uiState.value.personalAudio)
        assertEquals(PracticePlayerNotice.PersonalUnsupportedPitches, vm.uiState.value.playerNotice)
        assertEquals(0, fixture.saves)
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(1, fixture.requests.size)
    }
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

    @Test fun unreadableSavedDownloadKeepsRetryAvailableUntilTheFileCanBeLoaded() = practiceTest { vm, fixture, player ->
        fixture.request = SheetAudioRequest.Accepted(fixture.done)
        fixture.savedLookupUnavailable = true
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(1, fixture.saves)
        assertEquals(PracticePersonalAudio.Failed, vm.uiState.value.personalAudio)
        assertEquals(PracticePersonalAudio.Failed, vm.uiState.value.personalAudioControl)
        assertEquals(PracticePlayerNotice.PersonalDownloadFailed, vm.uiState.value.playerNotice)
        assertEquals(0, player.loads)

        fixture.savedLookupUnavailable = false
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(PracticePlayerNotice.PersonalAudioReady, vm.uiState.value.playerNotice)
        assertEquals(1, fixture.requests.size)
        assertEquals(1, fixture.saves)
        vm.startModel()
        runCurrent()
        assertEquals(1, player.loads)
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

    private fun practiceTest(performed: Boolean = false, cached: Boolean = false, estimatedMs: Double? = null,
        block: suspend TestScope.(PracticeViewModel, Fixture, FakeAudioPlayer) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val views = ViewModelStore()
        val client = createHttpClient(MockEngine { error("Only the fake audio repository should be called") }, ServerAddress.Production)
        try {
            val storage = FakeKeyValueStore()
            val member = SignedInMember(storage).apply { set("7") }
            val song = (if (performed) previewSong() else smallScoreSong()).copy(id = "42", instrumentId = "9", scoreVersion = 1,
                estimatedModelDurationMs = estimatedMs)
            val fixture = Fixture(performed)
            if (cached) {
                val audio = requireNotNull(fixture.done.audio)
                fixture.saved = DeviceSheetAudio(LocalAudioFile("/app/files/42.m4a"), "9", 60, false,
                    "a".repeat(64), requireNotNull(audio.durationMs), audio.performance)
            }
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

    private class Fixture(performed: Boolean = false) : SheetAudioDeviceStore, SheetAudioRepository {
        val done = SheetAudio("42", SheetAudioStatus.Done, "1", "1", false, "9", false, 60,
            audio = SheetAudioFile("https://example.invalid/song.m4a", 60_000, "audio/mp4", 100, "song.m4a",
                "a".repeat(64), if (performed) 7_050 else 3_300,
                performance = if (performed) SheetAudioPerformance(SourceHash, Json.parseToJsonElement(previewJson()).jsonObject) else null))
        var request: SheetAudioRequest = SheetAudioRequest.Accepted(done.copy(status = SheetAudioStatus.Queued, audio = null, pollAfterMs = 500))
        val requests = mutableListOf<SheetAudioSettings>()
        var statusReads = 0
        var saves = 0
        var saved: DeviceSheetAudio? = null
        var savedLookupUnavailable = false
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
            return saved.takeUnless { savedLookupUnavailable }
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

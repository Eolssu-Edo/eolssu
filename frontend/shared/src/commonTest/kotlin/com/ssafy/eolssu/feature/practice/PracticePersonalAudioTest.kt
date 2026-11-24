package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.AudioPlaybackStatus
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
import com.ssafy.eolssu.data.song.SheetAudioFailure
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
import com.ssafy.eolssu.feature.practice.component.personalAudioLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_sheet_audio_create
import eolssu.shared.generated.resources.practice_sheet_audio_download
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
import kotlin.test.assertTrue

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
        assertEquals(PracticePersonalAudio.Queued, vm.uiState.value.personalAudio)
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
        assertEquals(PracticePersonalAudio.Queued, vm.uiState.value.personalAudio)
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

    @Test fun keepsWaitingForTheCurrentVersionWhileAnOlderKeptFileIsOutdated() = practiceTest { vm, fixture, _ ->
        // SHT-13: outdated describes the kept older file, even while the current version is being generated.
        val olderFile = fixture.done.audio?.copy(url = "https://example.invalid/older.m4a", sha256 = "b".repeat(64))
        fixture.request = SheetAudioRequest.Accepted(fixture.done.copy(
            status = SheetAudioStatus.Running, outdated = true, audio = olderFile, pollAfterMs = 500))
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.saves)

        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, fixture.statusReads)
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(1, fixture.saves)
        assertEquals("a".repeat(64), fixture.saved?.sha256)
    }

    @Test fun refusesACompletedFileThatIsStillOutdated() = practiceTest { vm, fixture, _ ->
        fixture.request = SheetAudioRequest.Accepted(fixture.done.copy(outdated = true))
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Failed, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.saves)
    }

    @Test fun pollingKeepsTheCurrentRequestWhileTheKeptFileIsOutdatedButStopsForOtherSettings() = practiceTest { vm, fixture, _ ->
        val generating = fixture.done.copy(status = SheetAudioStatus.Running, outdated = true,
            audio = fixture.done.audio?.copy(sha256 = "b".repeat(64)), pollAfterMs = 500)
        fixture.request = SheetAudioRequest.InProgress
        fixture.statuses += listOf(generating, generating, fixture.done)
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3, fixture.statusReads)
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(1, fixture.saves)
    }

    @Test fun pollingStopsWhenAnotherSettingsRequestIsGenerating() = practiceTest { vm, fixture, _ ->
        val generating = fixture.done.copy(status = SheetAudioStatus.Running, outdated = true, audio = null, pollAfterMs = 500)
        fixture.request = SheetAudioRequest.Accepted(generating)
        fixture.statuses += generating.copy(bpm = 61)
        vm.downloadPersonalAudio()
        runCurrent()
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, fixture.statusReads)
        assertEquals(PracticePersonalAudio.Failed, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.saves)
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
        assertEquals(2, fixture.statusReads, "Re-entry checks for a running generation once before the InProgress lookup")
        assertEquals(1, fixture.saves)
    }

    @Test fun openingWhileTheServerGeneratesShowsGeneratingAndSavesWithoutRequesting() = practiceTest(
        serverStatuses = listOf(Generating, Generating, Generating.copy(status = SheetAudioStatus.Running)),
    ) { vm, fixture, _ ->
        assertEquals(PracticePersonalAudio.Queued, vm.uiState.value.personalAudio, "Server queue shows as waiting")
        assertEquals(PracticePlayerNotice.None, vm.uiState.value.playerNotice, "The spinner row alone explains the wait")
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio, "RUNNING shows as generating")
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.requests.size, "Opening practice must not request another generation")
        assertEquals(1, fixture.saves)
    }

    @Test fun reenteringWhileTheServerStillGeneratesResumesWaiting() = practiceTest(
        serverStatuses = listOf(Generating, Generating),
    ) { vm, fixture, _ ->
        vm.onScreenHidden(); runCurrent()
        fixture.statuses.addAll(listOf(Generating, Generating))
        vm.onScreenShown(); runCurrent()
        assertEquals(PracticePersonalAudio.Queued, vm.uiState.value.personalAudio)
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.requests.size)
    }

    @Test fun generationFinishedWithOtherSettingsReturnsToTheDownloadButton() = practiceTest(
        serverStatuses = listOf(Generating, Generating, Generating.copy(status = SheetAudioStatus.Done, bpm = 90)),
    ) { vm, fixture, _ ->
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudio)
        assertEquals(PracticePlayerNotice.None, vm.uiState.value.playerNotice)
        assertEquals(0, fixture.saves)
    }

    @Test fun failedGenerationFoundOnOpeningOffersRetry() = practiceTest(
        serverStatuses = listOf(Generating, Generating, Generating.copy(status = SheetAudioStatus.Failed, pollAfterMs = null)),
    ) { vm, fixture, _ ->
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Failed, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.saves)
    }

    @Test fun deletedDeviceFileFallsBackToDownloadAndRestoresWithoutPlayingAMissingFile() = practiceTest(cached = true) { vm, fixture, player ->
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        vm.onScreenHidden(); runCurrent()
        fixture.saved = null // 사용자가 기기에서 음원 파일을 지움
        vm.onScreenShown(); runCurrent()
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudio)
        assertEquals(0, player.loads)
        fixture.request = SheetAudioRequest.Accepted(fixture.done)
        vm.downloadPersonalAudio(); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(1, fixture.requests.size)
        assertEquals(1, fixture.saves)
    }

    @Test fun editedSheetIgnoresTheOldVersionFileAndDownloadsTheNewVersion() = practiceTest(
        scoreVersion = 2,
        // 편집 전(판본 1) 음원이 기기에 있고, 서버의 마지막 음원도 판본 1이다.
        deviceFiles = mapOf("1" to DeviceSheetAudio(LocalAudioFile("/app/files/42-v1.m4a"), "9", 60, false, "a".repeat(64), 3_300, null)),
        serverStatuses = listOf(SheetAudio("42", SheetAudioStatus.Done, "1", "2", true, "9", false, 60)),
    ) { vm, fixture, player ->
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudio, "The v1 file must not count for v2")
        vm.downloadPersonalAudio(); runCurrent()
        assertEquals(PracticePersonalAudio.Queued, vm.uiState.value.personalAudio)
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals("/app/files/42-v2.m4a", fixture.saved?.localFile?.path)
        vm.startModel(); runCurrent()
        assertEquals("/app/files/42-v2.m4a", player.requestedSource?.localFile?.path)
    }

    @Test fun editedSheetWhoseNewVersionIsGeneratingWaitsInsteadOfOfferingTheOldFile() = practiceTest(
        scoreVersion = 2,
        deviceFiles = mapOf("1" to DeviceSheetAudio(LocalAudioFile("/app/files/42-v1.m4a"), "9", 60, false, "a".repeat(64), 3_300, null)),
        serverStatuses = List(2) { SheetAudio("42", SheetAudioStatus.Running, "2", "2", true, "9", false, 60, pollAfterMs = 500) },
    ) { vm, fixture, _ ->
        assertEquals(PracticePersonalAudio.Preparing, vm.uiState.value.personalAudio)
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(0, fixture.requests.size)
        assertEquals("/app/files/42-v2.m4a", fixture.saved?.localFile?.path)
    }

    @Test fun webCreatesTheAudioAndStreamsItsSignedLinkWithoutSaving() = practiceTest(streams = true) { vm, fixture, player ->
        // S15P31E102-1261: 웹은 기기에 받지 않으므로 '음원 만들기'로 생성만 요청하고, 완료되면 서명 URL을 바로 튼다.
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudioControl)
        assertTrue(vm.uiState.value.personalAudioStreams)
        assertEquals(Res.string.practice_sheet_audio_create, personalAudioLabel(PracticePersonalAudio.Missing, streams = true))
        assertEquals(Res.string.practice_sheet_audio_download, personalAudioLabel(PracticePersonalAudio.Missing, streams = false))

        vm.downloadPersonalAudio(); runCurrent()
        assertEquals(PracticePersonalAudio.Queued, vm.uiState.value.personalAudio)
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(PracticePlayerNotice.PersonalAudioReady, vm.uiState.value.playerNotice)
        assertEquals(listOf(SheetAudioSettings(instrumentId = "9", withJangdan = false, bpm = 60)), fixture.requests)
        assertEquals(0, fixture.saves, "웹은 기기에 저장하지 않는다")
        assertEquals(0, player.loads)

        vm.startModel(); runCurrent()
        assertEquals(1, player.loads)
        assertEquals(StreamUrl, player.requestedSource?.url)
        assertNull(player.requestedSource?.localFile)
    }

    @Test fun webPlayAfterMovingFetchesTheLinkAgainAndOffersCreatingOnceTheServerDeletedTheFile() = practiceTest(
        streams = true, cached = true,
    ) { vm, fixture, player ->
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio, "링크가 살아 있으면 열자마자 들을 수 있다")
        vm.startModel(); runCurrent()
        assertEquals(StreamUrl, player.requestedSource?.url)
        player.emit(AudioPlaybackStatus.Playing, 500); runCurrent()
        vm.toggleModel(); runCurrent()
        assertEquals(ModelAudioStatus.Paused, vm.uiState.value.modelAudioStatus)
        vm.toggleModel(); runCurrent()
        assertEquals(1, player.loads, "제자리에서 이어 들으면 링크를 다시 받지 않는다")

        player.emit(AudioPlaybackStatus.Playing, 600); runCurrent()
        vm.toggleModel(); runCurrent()
        vm.seekModel(500); runCurrent()
        val reads = fixture.statusReads
        fixture.statuses += fixture.done.copy(audio = fixture.done.audio?.copy(url = "$StreamUrl?fresh"))
        vm.startModel(); runCurrent()
        assertEquals(reads + 1, fixture.statusReads, "새로 열 때는 파일이 아직 있는지 SHT-13으로 확인한다")
        assertEquals(2, player.loads)
        assertEquals("$StreamUrl?fresh", player.requestedSource?.url)

        // 완료 10분 뒤 서버가 파일을 지웠다.
        player.emit(AudioPlaybackStatus.Playing, 1_200); runCurrent()
        vm.toggleModel(); runCurrent()
        vm.seekModel(500); runCurrent()
        fixture.statuses += Expired(fixture.done)
        vm.startModel(); runCurrent()
        assertEquals(2, player.loads, "지워진 파일의 링크를 틀지 않는다")
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudioControl)
        assertEquals(0, fixture.saves)
    }

    @Test fun webReentryAfterTheServerDeletedTheFileOffersCreatingAgain() = practiceTest(streams = true, cached = true) { vm, fixture, player ->
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        vm.onScreenHidden(); runCurrent()
        fixture.statuses += listOf(Expired(fixture.done), Expired(fixture.done))
        vm.onScreenShown(); runCurrent()
        assertEquals(PracticePersonalAudio.Missing, vm.uiState.value.personalAudioControl)
        assertEquals(0, player.loads)

        vm.downloadPersonalAudio(); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals(PracticePersonalAudio.Ready, vm.uiState.value.personalAudio)
        assertEquals(1, fixture.requests.size)
        assertEquals(0, fixture.saves)
    }

    private fun practiceTest(streams: Boolean = false, performed: Boolean = false, cached: Boolean = false, estimatedMs: Double? = null,
        serverStatuses: List<SheetAudio> = listOf(NoCurrentGeneration), scoreVersion: Int = 1,
        deviceFiles: Map<String, DeviceSheetAudio> = emptyMap(),
        block: suspend TestScope.(PracticeViewModel, Fixture, FakeAudioPlayer) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val views = ViewModelStore()
        val client = createHttpClient(MockEngine { error("Only the fake audio repository should be called") }, ServerAddress.Production)
        try {
            val storage = FakeKeyValueStore()
            val member = SignedInMember(storage).apply { set("7") }
            val song = (if (performed) previewSong() else smallScoreSong()).copy(id = "42", instrumentId = "9", scoreVersion = scoreVersion,
                estimatedModelDurationMs = estimatedMs)
            val fixture = Fixture(performed, scoreVersion.toString(), streams)
            fixture.files.putAll(deviceFiles)
            if (!cached) fixture.statuses.addAll(serverStatuses)
            // 웹(스트리밍)의 '받아 둔 음원'은 서버에 아직 있는 완료 파일이다(Fixture.done).
            if (cached && !streams) {
                val audio = requireNotNull(fixture.done.audio)
                fixture.saved = DeviceSheetAudio(LocalAudioFile("/app/files/42.m4a"), "9", 60, false,
                    "a".repeat(64), requireNotNull(audio.durationMs), audio.performance)
            }
            val repository = SheetPracticeRepository(ApiSheetDocumentRepository(client), fixture,
                ApiReferenceCatalogRepository(client), InstrumentCatalog(client), member, fixture, { testScheduler.currentTime },
                streamsAudio = streams)
            val player = FakeAudioPlayer()
            val vm = PracticeViewModel(song.id, true, SampleSongRepository(), PracticeSettingsStore(storage),
                PracticeTimeMeter({ 0L }), PracticeTimeQueue(storage, null, member, backgroundScope),
                modelAudioRepository = repository, audioPlayer = player, mine = true, member = member,
                loadPersonalSheet = { song }, personalAudio = repository)
            views.put("practice", vm)
            runCurrent()
            // 열 때의 진행 중 생성 확인(SHT-13)은 아래 각 검사의 다운로드 조회 횟수에서 뺀다.
            if (serverStatuses == listOf(NoCurrentGeneration) && scoreVersion == 1) fixture.statusReads = 0
            block(vm, fixture, player)
        } finally {
            views.clear()
            client.close()
            Dispatchers.resetMain()
        }
    }

    private companion object {
        /** 연습 화면을 열 때 서버가 이 악보 음원을 만들고 있다(편집기 등에서 요청, S15P31E102-1039). */
        val Generating = SheetAudio("42", SheetAudioStatus.Queued, "1", "1", false, "9", false, 60, pollAfterMs = 500)
        /** 열 때 서버에 진행 중인 생성이 없다(이전 판본의 완료 음원). */
        val NoCurrentGeneration = SheetAudio("42", SheetAudioStatus.Done, "0", "1", true, "9", false, 60)
        const val StreamUrl = "https://example.invalid/song.m4a"
        /** 완료 10분이 지나 서버가 파일을 지운 뒤의 SHT-13 응답. */
        fun Expired(done: SheetAudio) = done.copy(status = SheetAudioStatus.Failed, failureCode = SheetAudioFailure.Expired, audio = null)
    }

    /** [streams]이면 웹처럼 기기 저장소가 없다. */
    private class Fixture(performed: Boolean = false, val version: String = "1", streams: Boolean = false) : SheetAudioDeviceStore, SheetAudioRepository {
        val done = SheetAudio("42", SheetAudioStatus.Done, version, version, false, "9", false, 60,
            audio = SheetAudioFile(StreamUrl, 60_000, "audio/mp4", 100, "song.m4a",
                "a".repeat(64), if (performed) 7_050 else 3_300,
                performance = if (performed) SheetAudioPerformance(SourceHash, Json.parseToJsonElement(previewJson()).jsonObject) else null))
        var request: SheetAudioRequest = SheetAudioRequest.Accepted(done.copy(status = SheetAudioStatus.Queued, audio = null, pollAfterMs = 500))
        val requests = mutableListOf<SheetAudioSettings>()
        val statuses = ArrayDeque<SheetAudio>()
        var statusReads = 0
        var saves = 0
        /** 확정 판본별 기기 저장본. 앱은 계정·악보·판본이 모두 맞는 파일만 쓴다. */
        val files = mutableMapOf<String, DeviceSheetAudio>()
        var saved: DeviceSheetAudio?
            get() = files[version]
            set(value) { if (value == null) files.remove(version) else files[version] = value }
        var savedLookupUnavailable = false
        var downloadFinished: CompletableDeferred<Unit>? = null

        override val available = !streams
        override suspend fun request(sheetId: String, settings: SheetAudioSettings): SheetAudioRequest {
            assertEquals("42", sheetId)
            requests += settings
            return request
        }
        override suspend fun status(sheetId: String): SheetAudioLookup {
            assertEquals("42", sheetId)
            statusReads++
            return SheetAudioLookup.Found(statuses.removeFirstOrNull() ?: done)
        }
        override suspend fun find(accountId: String, sheetId: String, scoreVersion: String): DeviceSheetAudio? {
            assertEquals(Triple("7", "42", version), Triple(accountId, sheetId, scoreVersion))
            return files[scoreVersion].takeUnless { savedLookupUnavailable }
        }
        override suspend fun save(accountId: String, sheetId: String, scoreVersion: String, download: SheetAudioDownload): DeviceSheetAudio {
            assertEquals(Triple("7", "42", version), Triple(accountId, sheetId, scoreVersion))
            saves++
            downloadFinished?.await()
            return DeviceSheetAudio(LocalAudioFile("/app/files/42-v$version.m4a"), download.instrumentId, download.bpm,
                download.withJangdan, download.sha256, download.durationMs, download.performance).also { saved = it }
        }
        override suspend fun delete(accountId: String, sheetId: String) = error("Unexpected delete")
    }
}

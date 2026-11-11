package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.audio.*
import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeJangdanTest {
    @Test fun timelineSeekingPreservesRhythmPlaybackAndEndRestartsFromZero() = practice { vm, _, _, _ ->
        vm.selectRhythm()
        vm.seekToFraction(0.5f)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(50f, vm.uiState.value.progressPercent, 0.001f)
        vm.startRhythm()
        runCurrent()
        assertTrue(vm.uiState.value.playing)
        vm.seekToFraction(0.75f)
        runCurrent()
        assertTrue(vm.uiState.value.playing)
        assertEquals(75f, vm.uiState.value.progressPercent, 0.001f)
        vm.seekToFraction(1f)
        runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(100f, vm.uiState.value.progressPercent, 0.001f)
        vm.toggleRhythm()
        runCurrent()
        assertTrue(vm.uiState.value.playing)
        assertEquals(0f, vm.uiState.value.progressPercent, 0.001f)
    }

    @Test fun settingsSaveImmediatelyAndRecordingCanToggleAccompaniment() = practice { vm, samples, _, settings ->
        vm.openPanel(PracticePanel.Notation)
        vm.updateDraft { it.copy(showHangul = true) }; runCurrent()
        assertTrue(settings.settings.value.showHangul)
        vm.closePanel(); runCurrent()
        vm.requestEvaluation(); runCurrent()
        advanceTimeBy(3100); runCurrent()
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        vm.openPanel(PracticePanel.Jangdan)
        assertEquals(PracticePanel.Jangdan, vm.uiState.value.panel)
        vm.updateDraft { it.copy(jangdanSound = true) }; runCurrent()
        assertTrue(settings.settings.value.jangdanSound)
        val on = samples.hits.size
        advanceTimeBy(600); runCurrent()
        assertTrue(samples.hits.size > on)
        vm.updateDraft { it.copy(jangdanSound = false) }; runCurrent()
        val off = samples.hits.size
        advanceTimeBy(600); runCurrent()
        assertEquals(off, samples.hits.size)
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.elapsedMs > 1000)
    }

    @Test fun switchingModesKeepsPositionWithoutPlayingUntilPlayIsPressed() = practice { vm, samples, media, _ ->
        vm.startRhythm(); runCurrent()
        advanceTimeBy(750); runCurrent()
        vm.selectModel(); runCurrent()
        val tick = vm.uiState.value.currentTick!!
        assertEquals(3.0, tick, 0.001)
        assertEquals(PracticeMode.Model, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.playing)
        val sounds = samples.hits.size
        advanceTimeBy(500); runCurrent()
        assertEquals(tick, vm.uiState.value.currentTick)
        assertEquals(sounds, samples.hits.size)
        assertEquals(0, media.loads)
        vm.startModel(); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 750); runCurrent()
        assertEquals(tick, vm.uiState.value.currentTick!!, 0.001)
        vm.selectRhythm(); runCurrent()
        assertEquals(PracticeMode.Rhythm, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.playing)
        assertEquals(tick, vm.uiState.value.currentTick!!, 0.001)
        val pausedSounds = samples.hits.size
        advanceTimeBy(500); runCurrent()
        assertEquals(pausedSounds, samples.hits.size)
        vm.toggleRhythm(); runCurrent()
        assertTrue(vm.uiState.value.playing)
        advanceTimeBy(250); runCurrent()
        vm.toggleRhythm(); runCurrent()
        assertEquals(4.0, vm.uiState.value.currentTick!!, 0.001)
    }

    @Test fun cancelledEvaluationCountdownDoesNotRestartByItself() = practice { vm, _, _, _ ->
        vm.requestEvaluation(); runCurrent()
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
        vm.cancelCountdown(); runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(PracticeMode.Ready, vm.uiState.value.mode)
    }

    @Test fun loadingNeverShowsPlayingAndCancelledPreparationCannotStart() = runTest {
        val player = Samples().apply { pending = CompletableDeferred() }
        val session = JangdanSession(player, backgroundScope)
        val plan = planJangdan(mapping, 60, 4)!!
        session.start(plan, false, { 0.0 })
        runCurrent()
        assertEquals(JangdanStatus.Loading, session.status.value)
        assertEquals(0, player.begins)
        session.stop()
        player.pending!!.complete(true)
        runCurrent()
        assertEquals(JangdanStatus.Idle, session.status.value)
        assertEquals(0, player.begins)
        session.start(plan, false, { 0.0 })
        runCurrent()
        assertEquals(JangdanStatus.Playing, session.status.value)
        assertEquals(listOf(JangdanSample.Deong), player.hits)
    }

    @Test fun oneCursorDropsStaleHitsResetsTailsOnLoopAndReportsOutputFailure() = runTest {
        val player = Samples()
        val session = JangdanSession(player, backgroundScope)
        var tick = 0.0
        session.start(planJangdan(mapping, 60, 4), false, { tick })
        runCurrent()
        tick = 2.01
        advanceTimeBy(5); runCurrent()
        assertEquals(listOf(JangdanSample.Deong, JangdanSample.Kung), player.hits)
        advanceTimeBy(20); runCurrent()
        assertEquals(2, player.hits.size)
        val silence = player.silences
        tick = 0.0
        advanceTimeBy(5); runCurrent()
        assertEquals(silence + 1, player.silences)
        assertEquals(JangdanSample.Deong, player.hits.last())
        player.accept = false
        tick = 2.0
        advanceTimeBy(5); runCurrent()
        assertEquals(JangdanStatus.Failed, session.status.value)
    }

    @Test fun rhythmSharesScoreClockPausesSeeksChangesTempoAndStopsOnHidden() = practice { vm, samples, _, _ ->
        vm.startRhythm(); runCurrent()
        assertEquals(PracticeMode.Rhythm, vm.uiState.value.mode)
        assertTrue(vm.uiState.value.playing)
        assertEquals(listOf(JangdanSample.Deong), samples.hits)
        advanceTimeBy(500); runCurrent()
        assertEquals(2.0, vm.uiState.value.currentTick!!, 0.001)
        assertEquals(JangdanSample.Kung, samples.hits.last())
        vm.toggleRhythm(); runCurrent()
        val paused = vm.uiState.value.currentTick!!
        val count = samples.hits.size
        advanceTimeBy(500); runCurrent()
        assertEquals(paused, vm.uiState.value.currentTick)
        assertEquals(count, samples.hits.size)
        vm.seekModel(-500); runCurrent()
        assertEquals(0.0, vm.uiState.value.currentTick)
        vm.startRhythm(); runCurrent()
        vm.openPanel(PracticePanel.Tempo)
        vm.updateDraft { it.copy(step = TempoStep.Slow) }
        vm.applyTempo(); runCurrent()
        assertEquals(48, vm.uiState.value.bpm)
        advanceTimeBy(625); runCurrent()
        assertEquals(JangdanSample.Kung, samples.hits.last())
        vm.toggleRhythm(); runCurrent() // Capture the precise clock between 50ms UI samples.
        assertEquals(2.0, vm.uiState.value.currentTick!!, 0.001)
        vm.onScreenHidden(); runCurrent()
        assertFalse(vm.uiState.value.playing)
        val stopped = samples.hits.size
        advanceTimeBy(1500); runCurrent()
        assertEquals(stopped, samples.hits.size)
        assertTrue(samples.releases > 0)
    }

    @Test fun gutgeoriTempoChangeDuringPreparationLoadsOnlyTheNewLengthBeforePlaying() = practice(
        jangdan = JangdanPlayback("2", "굿거리", 0, 4, listOf("더러러러", ""), listOf(2)),
    ) { vm, samples, _, _ ->
        val original = CompletableDeferred<Boolean>()
        samples.pending = original
        vm.startRhythm(); runCurrent()
        assertEquals(44_100, samples.prepared.last().single().targetFrames)
        assertFalse(vm.uiState.value.playing)

        val adjusted = CompletableDeferred<Boolean>()
        samples.pending = adjusted
        vm.openPanel(PracticePanel.Tempo)
        vm.updateDraft { it.copy(step = TempoStep.Slow) }
        vm.applyTempo(); runCurrent()
        assertEquals(48, vm.uiState.value.bpm)
        val newClip = samples.prepared.last().single()
        assertEquals(55_125, newClip.targetFrames)

        original.complete(true); runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertTrue(samples.hits.isEmpty())
        adjusted.complete(true); runCurrent()
        assertTrue(vm.uiState.value.playing)
        assertEquals(listOf(newClip.sample), samples.hits)
    }

    @Test fun previewStopsForRecordingAndAccompanimentFollowsRecordingClock() = practice { vm, samples, media, settings ->
        vm.togglePreview(); runCurrent()
        assertTrue(vm.uiState.value.draft.previewing)
        settings.update { it.copy(jangdanSound = true) }; runCurrent()
        vm.startModel(); runCurrent()
        assertFalse(vm.uiState.value.draft.previewing)
        val preparedCount = samples.hits.size
        advanceTimeBy(1000); runCurrent()
        assertEquals(preparedCount, samples.hits.size, "Loading media cannot start accompaniment")
        media.emit(AudioPlaybackStatus.Playing, 500); runCurrent()
        assertEquals(JangdanSample.Kung, samples.hits.last())
        assertTrue(samples.modelFocus)
        vm.seekModel(-500); runCurrent()
        val beforeSeek = samples.hits.size
        advanceTimeBy(500); runCurrent()
        assertEquals(beforeSeek, samples.hits.size)
        media.emit(AudioPlaybackStatus.Playing, 0); runCurrent()
        assertEquals(JangdanSample.Deong, samples.hits.last())
        vm.requestEvaluation(); runCurrent()
        assertEquals(PracticeMode.Countdown, vm.uiState.value.mode)
        val beforeRecording = samples.hits.size
        vm.togglePreview()
        vm.startRhythm()
        advanceTimeBy(3300); runCurrent()
        assertEquals(PracticeMode.Recording, vm.uiState.value.mode)
        assertTrue(samples.hits.size > beforeRecording)
    }

    @Test fun pauseResumeDoesNotReplayBoundaryButExplicitSeekDoes() = practice { vm, samples, _, _ ->
        vm.startRhythm(); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals(JangdanSample.Kung, samples.hits.last())
        vm.toggleRhythm(); runCurrent()
        val atPause = samples.hits.size
        vm.toggleRhythm(); runCurrent()
        assertEquals(atPause, samples.hits.size, "Resume must not replay the boundary already heard")
        advanceTimeBy(500); runCurrent()
        assertEquals(JangdanSample.Deong, samples.hits.last())
        assertEquals(atPause + 1, samples.hits.size, "The next boundary must not be lost")
        vm.seekModel(-1000); runCurrent()
        assertEquals(JangdanSample.Deong, samples.hits.last())
        assertEquals(atPause + 2, samples.hits.size, "Explicit seek starts the target attack")
        vm.seekModel(5000); runCurrent()
        assertFalse(vm.uiState.value.playing, "Seeking to the end must stop, not silently loop")
        val atEnd = samples.hits.size
        vm.startRhythm(); runCurrent()
        assertEquals(atEnd + 1, samples.hits.size, "Play after completion starts the first attack")
    }

    @Test fun modelWaitsForJangdanBeforeStartingTheMelody() = practice { vm, samples, media, settings ->
        settings.update { it.copy(jangdanSound = true) }; runCurrent()
        val ready = CompletableDeferred<Boolean>()
        samples.pending = ready
        vm.startModel(); runCurrent()
        assertEquals(0, media.loads)
        assertFalse(vm.uiState.value.playing)

        ready.complete(true); runCurrent()
        assertEquals(1, media.loads)
        media.emit(AudioPlaybackStatus.Playing, 0); runCurrent()
        assertEquals(listOf(JangdanSample.Deong), samples.hits)
    }

    @Test fun modelSeekDoesNotReplayAnAttackBeforeItsLanding() = practice { vm, samples, media, settings ->
        settings.update { it.copy(jangdanSound = true) }; runCurrent()
        vm.startModel(); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 0); runCurrent()
        assertEquals(listOf(JangdanSample.Deong), samples.hits)

        vm.seekModel(510); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 510); runCurrent()
        assertEquals(listOf(JangdanSample.Deong), samples.hits)

        vm.seekModel(-10); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 500); runCurrent()
        assertEquals(listOf(JangdanSample.Deong, JangdanSample.Kung), samples.hits)
        assertEquals(1, samples.prepared.size, "A seek reuses the loaded samples")
    }

    @Test fun failedModelJangdanPreparationCanRetry() = practice { vm, samples, media, settings ->
        settings.update { it.copy(jangdanSound = true) }; runCurrent()
        samples.pending = CompletableDeferred(false)
        vm.startModel(); runCurrent()
        assertEquals(0, media.loads)
        assertEquals(JangdanStatus.Failed, vm.uiState.value.jangdanStatus)
        assertEquals(ModelAudioStatus.Failed, vm.uiState.value.modelAudioStatus)

        samples.pending = null
        vm.startModel(); runCurrent()
        assertEquals(1, media.loads)
        media.emit(AudioPlaybackStatus.Playing, 0); runCurrent()
        assertEquals(listOf(JangdanSample.Deong), samples.hits)
    }

    @Test fun modelResumeSkipsPlayedAttackAndSeekAllowsItAgain() = practice { vm, samples, media, settings ->
        settings.update { it.copy(jangdanSound = true) }; runCurrent()
        vm.startModel(); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 500); runCurrent()
        assertEquals(JangdanSample.Kung, samples.hits.last())
        vm.toggleModel(); runCurrent()
        val atPause = samples.hits.size
        vm.toggleModel(); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 500); runCurrent()
        assertEquals(atPause, samples.hits.size)
        vm.seekModel(-500); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 0); runCurrent()
        assertEquals(atPause + 1, samples.hits.size)
        assertEquals(JangdanSample.Deong, samples.hits.last())
        vm.toggleModel(); runCurrent()
        vm.toggleModel(); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 0); runCurrent()
        assertEquals(atPause + 1, samples.hits.size, "A seek does not disable later resume de-duplication")
        vm.restartModel(); runCurrent()
        media.emit(AudioPlaybackStatus.Playing, 0); runCurrent()
        assertEquals(atPause + 2, samples.hits.size, "Restart explicitly allows the first attack")
    }

    @Test fun outputFailureAndStopNeverKeepRhythmRunning() = practice { vm, samples, _, _ ->
        samples.accept = false
        vm.startRhythm(); runCurrent()
        assertFalse(vm.uiState.value.playing)
        assertEquals(JangdanStatus.Failed, vm.uiState.value.jangdanStatus)
        samples.accept = true
        vm.startRhythm(); runCurrent()
        assertTrue(vm.uiState.value.playing)
        vm.stopAll(); runCurrent()
        val before = samples.hits.size
        advanceTimeBy(1000); runCurrent()
        assertEquals(before, samples.hits.size)
    }

    private fun practice(
        jangdan: JangdanPlayback = mapping,
        block: suspend TestScope.(PracticeViewModel, Samples, FakeAudioPlayer, PracticeSettingsStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val views = ViewModelStore()
        try {
            val song = smallScoreSong().copy(id = "42", scoreVersion = 1, jangdanPlayback = jangdan)
            val repository = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String): Song = song
            }
            val kv = FakeKeyValueStore()
            val settings = PracticeSettingsStore(kv).apply { grantMic() }
            val samples = Samples()
            val media = FakeAudioPlayer()
            val vm = PracticeViewModel(song.id, false, repository, settings, PracticeTimeMeter({ 0L }),
                PracticeTimeQueue(kv, null, SignedInMember(kv), backgroundScope), recorder = FakePracticeRecorder(),
                playbackTimeSource = testScheduler.timeSource, jangdanPlayer = samples,
                modelAudioRepository = modelRepository { audioFixture() }, audioPlayer = media)
            views.put("practice", vm)
            runCurrent()
            block(vm, samples, media, settings)
        } finally { views.clear(); Dispatchers.resetMain() }
    }
}

private val mapping = JangdanPlayback("3", "세마치", 0, 4, listOf("덩", "쿵"), listOf(1, 1))

private class Samples : JangdanSamplePlayer {
    override val available = true
    override var interrupted = false
    var pending: CompletableDeferred<Boolean>? = null
    var begins = 0
    var releases = 0
    var silences = 0
    var accept = true
    var modelFocus = false
    val hits = mutableListOf<JangdanSample>()
    val prepared = mutableListOf<Set<JangdanClip>>()
    override suspend fun prepare(clips: Set<JangdanClip>): Boolean {
        prepared += clips
        return pending?.await() ?: true
    }
    override fun begin(accompanyingModel: Boolean): Boolean { begins++; modelFocus = accompanyingModel; return true }
    override fun play(clip: JangdanClip, gain: Float): Boolean { if (accept) hits += clip.sample; return accept }
    override fun silence() { silences++ }
    override fun stop() = silence()
    override fun release() { stop(); releases++ }
}

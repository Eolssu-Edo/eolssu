package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.practice.PracticeSettings
import com.ssafy.eolssu.data.song.SampleSongs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeStateTest {

    private val arirang = SampleSongs.first { it.id == "arirang" }
    private val milyang = SampleSongs.first { it.id == "milyang" }
    private val ready = PracticeUiState(song = arirang)

    @Test
    fun tempoStepsFollowBaseBpm() {
        assertEquals(listOf(36, 48, 60, 66, 72), TempoStep.entries.map { it.bpm(60) })
        assertEquals(TempoStep.Slow, TempoStep.nearest(60, 47))
        assertEquals(TempoStep.Normal, TempoStep.nearest(60, 60))
    }

    @Test
    fun modelPlaysFromStartAndStopSelectsHeardNote() {
        val playing = ready.copy(selected = 5).startModel()
        assertEquals(PracticeMode.Model, playing.mode)
        assertTrue(playing.playing)
        assertEquals(0, playing.position)

        val stopped = playing.copy(position = 7).stopModel()
        assertEquals(PracticeMode.Ready, stopped.mode)
        assertEquals(7, stopped.selected)
        assertEquals(0, stopped.position)
    }

    @Test
    fun replayAfterEndRestartsFromBeginning() {
        val ended = ready.startModel().copy(playing = false, position = ready.eventCount - 1)
        val replay = ended.toggleModel()
        assertTrue(replay.playing)
        assertEquals(0, replay.position)
    }

    @Test
    fun evaluationAsksMicOnceThenCountsDown() {
        val asked = ready.startModel().requestEvaluation(micGranted = false)
        assertEquals(PracticeMode.Ready, asked.mode)
        assertEquals(PracticePopup.Permission, asked.popup)

        val counting = ready.requestEvaluation(micGranted = true)
        assertEquals(PracticeMode.Countdown, counting.mode)
        assertEquals(3, counting.count)
        assertFalse(counting.resume)
    }

    @Test
    fun recordingToEndShowsEndPopup() {
        var state = ready.beginCountdown().beginRecording()
        repeat(state.eventCount) { state = state.advance(doneMs = 100.0, micLevel = 0.5f) }
        assertTrue(state.finished)
        assertEquals(PracticePopup.End, state.popup)
        assertEquals(100f, state.progressPercent)
    }

    @Test
    fun modelEndStopsAtLastEventWithFullTime() {
        var state = ready.startModel()
        repeat(state.eventCount) { state = state.advance(doneMs = 100.0, micLevel = 0f) }
        assertFalse(state.playing)
        assertEquals(state.eventCount - 1, state.position)
        assertEquals(state.totalSeconds * 1000.0, state.elapsedMs)
    }

    @Test
    fun abortPausesAndResumeContinuesFromSamePlace() {
        val recording = ready.beginRecording().copy(position = 12)
        val paused = recording.askAbort(fraction = 0.4f)
        assertEquals(PracticePopup.Abort, paused.popup)
        assertNull(paused.eventStartedAt)
        assertEquals(0.4f, paused.pausedFraction)

        val counting = paused.beginCountdown(resume = true)
        assertTrue(counting.resuming)
        assertTrue(counting.live)
        val resumed = counting.resumeRecording()
        assertEquals(PracticeMode.Recording, resumed.mode)
        assertEquals(12, resumed.position)
    }

    @Test
    fun cancellingResumeCountdownReturnsToAbortPopup() {
        val cancelled = ready.beginRecording().askAbort(0f).beginCountdown(resume = true).cancelCountdown()
        assertEquals(PracticeMode.Recording, cancelled.mode)
        assertEquals(PracticePopup.Abort, cancelled.popup)
        assertEquals(PracticeMode.Ready, ready.beginCountdown().cancelCountdown().mode)
    }

    @Test
    fun leavingScreenInterruptsOnlyRecording() {
        assertEquals(PracticeMode.Interrupted, ready.beginRecording().interrupt().mode)
        assertEquals(PracticePopup.Interrupted, ready.beginRecording().interrupt().popup)
        assertEquals(ready, ready.interrupt())
    }

    @Test
    fun selectingPitchOpensFingeringButMarksOnlySelectForHold() {
        // 아리랑 첫 각: 黃 ㅡ 太 …
        val pitch = ready.selectEvent(2)
        assertEquals(PracticePanel.Fingering, pitch.panel)
        assertEquals(Yul.Taeju, pitch.draft.note)

        val hold = ready.selectEvent(1)
        assertEquals(1, hold.selected)
        assertNull(hold.panel)
    }

    @Test
    fun panelsDoNotOpenWhileRecording() {
        assertNull(ready.beginRecording().openPanel(PracticePanel.Settings).panel)
        assertEquals(PracticePanel.Settings, ready.openPanel(PracticePanel.Settings).panel)
    }

    @Test
    fun fingeringNavigationStopsAtEnds() {
        val first = ready.copy(draft = PracticeDraft(note = Yul.Hwangjong))
        assertEquals(Yul.Hwangjong, first.moveFingering(-1).draft.note)
        assertEquals(Yul.Taeju, first.moveFingering(1).draft.note)
        val last = ready.copy(draft = PracticeDraft(note = Yul.CheongTaeju))
        assertEquals(Yul.CheongTaeju, last.moveFingering(1).draft.note)
    }

    @Test
    fun notationKeepsSolfegeOffWithoutData() {
        val draft = PracticeDraft(showHangul = true, showSolfege = true)
        assertTrue(PracticeSettings().applyNotation(draft, arirang).showSolfege)
        assertFalse(PracticeSettings().applyNotation(draft, milyang).showSolfege)
    }

    @Test
    fun tempoIsStoredPerSong() {
        val settings = PracticeSettings().applyTempo(PracticeDraft(step = TempoStep.Slowest), arirang)
        assertEquals(36, PracticeUiState(song = arirang, settings = settings).bpm)
        assertEquals(milyang.baseBpm, PracticeUiState(song = milyang, settings = settings).bpm)
    }
}

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
        val ended = ready.startModel().copy(playing = false, position = ready.eventCount - 1, eventElapsedMs = ready.eventDurationMs(ready.eventCount - 1))
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
        repeat(state.eventCount) { state = state.advance(doneMs = state.eventDurationMs(), micLevel = 0f) }
        assertFalse(state.playing)
        assertEquals(state.eventCount - 1, state.position)
        assertEquals(arirang.durationMs(), state.elapsedMs, 0.000001)
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
    fun recordingLimitEndsLikeReachingSongEnd() {
        val ended = ready.beginRecording().copy(position = 5, micLevel = 0.7f).endRecording()
        assertTrue(ended.finished)
        assertEquals(PracticePopup.End, ended.popup)
        assertEquals(5, ended.position)
        assertEquals(0f, ended.micLevel)
    }

    @Test
    fun finishedRecordingIsCheckedBeforeEvaluation() {
        val ended = ready.beginRecording().endRecording()
        assertEquals(ended, ended.checkRecording(saved = true, tooLarge = false))
        assertEquals(PracticePopup.TooLarge, ended.checkRecording(saved = true, tooLarge = true).popup)

        val failed = ended.checkRecording(saved = false, tooLarge = false)
        assertEquals(PracticeMode.Interrupted, failed.mode)
        assertEquals(PracticePopup.RecordFailed, failed.popup)
        assertFalse(failed.finished)
    }

    @Test
    fun silenceLongerThanThresholdShowsNoInputHint() {
        val recording = ready.copy(inputMeasured = true).beginRecording()
        val quiet = recording.recordLevel(0f, 1000.0).recordLevel(0f, 500.0)
        assertEquals(1500.0, quiet.noInputMs)
        assertFalse(quiet.noInputHint)

        val longer = quiet.recordLevel(0f, 100.0)
        assertTrue(longer.noInputHint)

        val heard = longer.recordLevel(0.4f, 100.0)
        assertTrue(heard.hadInput)
        assertEquals(0.0, heard.noInputMs)
        assertFalse(heard.noInputHint)
        assertTrue(heard.recordLevel(0f, 2000.0).noInputHint)
    }

    @Test
    fun noInputHintNeedsMeasuredInputAndRunningRecording() {
        val silent = ready.beginRecording().recordLevel(0f, 4000.0)
        assertFalse(silent.noInputHint)

        val measured = ready.copy(inputMeasured = true).beginRecording().recordLevel(0f, 4000.0)
        assertTrue(measured.noInputHint)
        assertFalse(measured.endRecording().noInputHint)
        assertEquals(0.0, measured.toReady().noInputMs)
        assertEquals(0.0, measured.endRecording().beginCountdown().beginRecording().noInputMs)
        // 연주 중이 아니면 측정 값을 반영하지 않는다.
        assertEquals(ready, ready.recordLevel(0f, 4000.0))
    }

    @Test
    fun endingWithoutAnyInputShowsNoInputPopupInsteadOfEvaluation() {
        val measured = ready.copy(inputMeasured = true).beginRecording()
        var silent = measured
        repeat(silent.eventCount) { silent = silent.recordLevel(0f, 100.0).advance(doneMs = 100.0, micLevel = 0f) }
        assertTrue(silent.finished)
        assertEquals(PracticePopup.EndNoInput, silent.popup)
        // 입력이 없는 녹음은 크기 초과로 바꾸지 않는다. 저장 실패는 그대로 알린다.
        assertEquals(silent, silent.checkRecording(saved = true, tooLarge = true))
        assertEquals(PracticePopup.RecordFailed, silent.checkRecording(saved = false, tooLarge = false).popup)

        // 중간에 한 번이라도 입력이 있었으면 평가한다.
        val played = measured.recordLevel(0.5f, 100.0).recordLevel(0f, 5000.0).endRecording()
        assertEquals(PracticePopup.End, played.popup)
    }

    @Test
    fun recordingsWithoutMeasuredInputUseServerSilenceDetection() {
        // 녹음은 지원하지만 입력 크기를 측정하지 않는 구현은 서버 NO_SOUND 판정에 맡긴다.
        var state = ready.beginRecording()
        repeat(state.eventCount) { state = state.advance(doneMs = 100.0, micLevel = 0f) }
        assertFalse(state.inputMeasured)
        assertFalse(state.hadInput)
        assertEquals(PracticePopup.End, state.popup)
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
    fun fingeringNavigationWalksScoreAndStopsAtEnds() {
        // 보드 운지법 패널의 이전·다음 음은 악보의 앞뒤 정간으로 움직인다.
        val first = ready.selectEvent(0)
        assertEquals(first, first.moveFingering(-1))
        val next = first.moveFingering(1)
        assertEquals(1, next.selected)
        assertEquals(ready.fingeringYulAt(1), next.draft.note)
        assertFalse(next.draft.noteRest)
        val last = ready.copy(selected = ready.eventCount - 1)
        assertEquals(last, last.moveFingering(1))
    }

    @Test
    fun fingeringYulFollowsPreviousPitchThroughHoldAndNullOnRest() {
        val events = arirang.timeline
        val hold = events.indexOfFirst { it.note == com.ssafy.eolssu.core.model.ScoreNote.Hold }
        assertTrue(hold > 0)
        assertEquals(ready.fingeringYulAt(hold - 1), ready.fingeringYulAt(hold))
        val rest = events.indexOfFirst { it.note == com.ssafy.eolssu.core.model.ScoreNote.Rest }
        if (rest >= 0) {
            assertNull(ready.fingeringYulAt(rest))
            assertTrue(ready.copy(selected = rest - 1).moveFingering(1).draft.noteRest)
        }
    }

    @Test
    fun micDenialStaysOnScreenWithDeniedOrBlockedPopup() {
        val denied = ready.requestEvaluation(micGranted = false).denyMic(blocked = false)
        assertEquals(PracticeMode.Ready, denied.mode)
        assertEquals(PracticePopup.PermDenied, denied.popup)
        assertEquals(PracticePopup.PermBlocked, ready.denyMic(blocked = true).popup)
    }

    @Test
    fun readySeekMovesSelectionByTime() {
        val forward = ready.copy(selected = 0).seekSelection(5_000)
        assertTrue(forward.selected > 0)
        assertEquals(0, forward.seekSelection(-60_000).selected)
        assertEquals(ready.eventCount - 1, ready.copy(selected = 0).seekSelection(600_000).selected)
    }

    @Test
    fun instrumentFollowsSettingThenSongCode() {
        assertEquals(com.ssafy.eolssu.core.model.fingering.GuideInstrument.DaegeumJeongak, ready.instrument)
        val danso = ready.copy(settings = PracticeSettings(instrumentId = "danso"))
        assertEquals(com.ssafy.eolssu.core.model.fingering.GuideInstrument.Danso, danso.instrument)
        assertTrue(ready.copy(settings = PracticeSettings(fingeringView = true)).fingeringView)
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

    @Test
    fun playbackRecordingAndJangdanPreviewCountWithoutInput() {
        assertEquals(emptySet(), ready.runningActivities)
        assertEquals(setOf(ContinuousActivity.PLAYBACK), ready.startModel().runningActivities)
        assertEquals(emptySet(), ready.startModel().toggleModel().runningActivities) // 일시정지
        assertEquals(setOf(ContinuousActivity.PLAYBACK), ready.copy(listening = Yul.Hwangjong).runningActivities)
        assertEquals(setOf(ContinuousActivity.RECORDING), ready.beginCountdown().runningActivities)
        assertEquals(setOf(ContinuousActivity.RECORDING), ready.beginRecording().runningActivities)
        val previewing = ready.openPanel(PracticePanel.Jangdan).let { it.copy(draft = it.draft.copy(previewing = true)) }
        assertEquals(setOf(ContinuousActivity.METRONOME), previewing.runningActivities)
        assertEquals(emptySet(), previewing.closePanel().runningActivities)
    }

    @Test
    fun recordingIsNotRunningWhilePausedOrDone() {
        val recording = ready.beginRecording()
        assertEquals(emptySet(), recording.askAbort(fraction = 0.4f).runningActivities)
        assertEquals(emptySet(), recording.endRecording().runningActivities)
        assertEquals(emptySet(), recording.interrupt().runningActivities)
        assertEquals(setOf(ContinuousActivity.RECORDING), recording.askAbort(0f).beginCountdown(resume = true).runningActivities)
    }
}

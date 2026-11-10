package com.ssafy.eolssu.feature.guide.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnPracticeUiStateTest {

    private val loud = 0.6f
    private val quiet = 0.05f

    /** [ms] 동안 [level] 크기를 100ms마다 넣는다. */
    private fun LearnPracticeUiState.feed(level: Float, ms: Int): LearnPracticeUiState =
        (0 until ms / 100).fold(this) { s, _ -> s.recordLevel(level, 100.0) }

    private fun firstSound() = LearnPracticeUiState(stage = 3).startListening()

    /** 5단계 연습 음은 올라가기 한 줄, 내려오기 한 줄로 나눠 보이고(S15P31E102-688), 다른 단계는 한 묶음이다. */
    @Test
    fun stageFivePitchNotesSplitIntoUpAndDownRows() {
        assertEquals(listOf(0 until 5, 5 until 10), pitchNoteRows(stage = 5, count = 10))
        assertEquals(listOf(0 until 6), pitchNoteRows(stage = 4, count = 6))
        assertEquals(listOf(0 until 1), pitchNoteRows(stage = 6, count = 1))
    }

    /** 5단계 통과 기준 문구는 서버 예시(8음 중 7음) 대신 실제 판정 음 수와 필요한 맞은 음 수를 쓴다(S15P31E102-688). */
    @Test
    fun stageFivePassCountFollowsPitchPlan() {
        val pitch = LearnPitchProgress(notes = List(10) { "仲" }, required = 9, holdTargetMs = 300)
        assertEquals(10 to 9, stageFivePassCount(5, pitch))
        assertNull(stageFivePassCount(5, null))
        assertNull(stageFivePassCount(4, pitch))
    }

    @Test
    fun countsASoundHeldForTwoSecondsOnce() {
        val held = firstSound().feed(loud, 2_000)
        assertEquals(1, held.successes)
        assertEquals(LearnPracticeCue.Counted, held.cue)
        assertEquals(1f, held.holdProgress)

        // 한 소리를 길게 이어도 한 번만 센다
        assertEquals(1, held.feed(loud, 3_000).successes)
    }

    @Test
    fun passesAfterThreeSeparateSounds() {
        val passed = (1..FIRST_SOUND_TARGET).fold(firstSound()) { s, _ -> s.feed(loud, 2_000).feed(quiet, 500) }

        assertEquals(LearnPracticePhase.Passed, passed.phase)
        assertEquals(FIRST_SOUND_TARGET, passed.successes)
        assertFalse(passed.listening)
        // 통과한 뒤 들어온 크기는 반영하지 않는다
        assertEquals(passed, passed.recordLevel(loud, 100.0))
    }

    @Test
    fun shortBreaksKeepTheSameSound() {
        val state = firstSound().feed(loud, 1_000).feed(quiet, 200).feed(loud, 1_000)
        assertEquals(1, state.successes)
    }

    @Test
    fun soundEndingBeforeTwoSecondsIsTooShortAndResetsHold() {
        val state = firstSound().feed(loud, 1_500).feed(quiet, 300)

        assertEquals(0, state.successes)
        assertEquals(0.0, state.holdMs)
        assertEquals(LearnPracticeCue.TooShort, state.cue)
    }

    @Test
    fun silenceWithoutSoundIsNotTooShort() {
        val state = firstSound().feed(quiet, 1_000)
        assertNull(state.cue)
        assertFalse(state.sounding)
    }

    @Test
    fun stagesWithoutSoundJudgingNeverCount() {
        val state = LearnPracticeUiState(stage = 4).startListening().feed(loud, 5_000).feed(quiet, 500)

        assertFalse(state.judgesBySound)
        assertEquals(0, state.successes)
        assertNull(state.cue)
        assertTrue(state.listening)
    }

    @Test
    fun ignoresLevelsWhenNotListening() {
        val ready = LearnPracticeUiState(stage = 3)
        assertEquals(ready, ready.recordLevel(loud, 100.0))
    }

    @Test
    fun stoppingKeepsCountAndRestartingAfterPassCountsAgain() {
        val stopped = firstSound().feed(loud, 2_000).feed(quiet, 500).stopListening()
        assertEquals(1, stopped.successes)
        assertEquals(LearnPracticePhase.Ready, stopped.phase)
        assertEquals(1, stopped.startListening().successes)

        val passed = (1..FIRST_SOUND_TARGET).fold(firstSound()) { s, _ -> s.feed(loud, 2_000).feed(quiet, 500) }
        assertEquals(0, passed.startListening().successes)
        assertEquals(0, passed.reset().successes)
    }

    @Test
    fun micStatusDecidesWhetherListeningCanStart() {
        assertTrue(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Denied).canListen)
        assertTrue(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Failed).canListen)
        assertFalse(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Blocked).canListen)
        assertFalse(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Unsupported).canListen)

        val denied = firstSound().feed(loud, 500).micUnavailable(LearnMicStatus.Denied)
        assertFalse(denied.listening)
        assertEquals(LearnMicStatus.Available, denied.startListening().mic)
    }
}

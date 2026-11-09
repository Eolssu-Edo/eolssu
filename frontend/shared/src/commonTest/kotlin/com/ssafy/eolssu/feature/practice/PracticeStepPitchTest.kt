package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 한 음씩 따라 불기의 기기 안 판정(S15P31E102-341). 관측은 1초에 1,000샘플, 20샘플(20ms)씩 이어지는 고정 입력이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStepPitchTest {
    private val hwang = 311.13
    private val criteria = NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, octaveMustMatch = true,
        firstNoteTuning = true, maxTuningOffsetCent = 50.0, skipSigimsae = false, hintAfterMs = 8000)
    private val hwangNote = StepTarget(0, ScoreNote.SourcePitch("황", 0))
    private val taeNote = StepTarget(1, ScoreNote.SourcePitch("태", 0))

    private fun cents(hz: Double, cents: Double) = hz * 2.0.pow(cents / 1200)

    /** 관측을 차례로 만든다. [skip]이면 순번 하나를 건너뛰어 관측 누락을 만든다. */
    private class Feed(val matcher: StepPitchMatcher) {
        var sequence = 0L
        fun frame(hz: Double?, difference: Double? = 0.05, skip: Boolean = false): Int? {
            if (skip) sequence++
            val end = sequence * 20 + 46
            return matcher.accept(PitchReading(1, 1, sequence++, end - 46, end, 1000, hz, 0, difference))
        }

        /** [count]번 보내며 처음 맞은 음과 그때까지 보낸 수. */
        fun hold(hz: Double?, count: Int, difference: Double? = 0.05): Pair<Int?, Int> {
            repeat(count) { i -> frame(hz, difference)?.let { return it to i + 1 } }
            return null to count
        }
    }

    private fun feed(criteria: NotePracticeCriteria = this.criteria, target: StepTarget = hwangNote) =
        Feed(StepPitchMatcher(criteria, hwang).apply { target(target) })

    @Test fun matchesOnceTheNoteIsHeldForHoldMs() {
        // 처음 맞은 관측의 끝부터 300ms: 20ms씩 15번 더 이어져야 한다.
        assertEquals(0 to 16, feed().hold(hwang, 40))
    }

    @Test fun pitchOutsideToleranceNeverMatches() {
        assertEquals(null to 40, feed().hold(cents(hwang, 60.0), 40))
        assertEquals(0 to 16, feed().hold(cents(hwang, -45.0), 40))
    }

    @Test fun silenceUnconfirmedPitchAndMissingFramesRestartTheHold() {
        val silence = feed()
        assertEquals(null to 10, silence.hold(hwang, 10))
        assertNull(silence.frame(null))
        assertEquals(0 to 16, silence.hold(hwang, 40))

        val missing = feed()
        assertEquals(null to 10, missing.hold(hwang, 10))
        assertNull(missing.frame(hwang, skip = true))
        assertEquals(0 to 15, missing.hold(hwang, 40))
    }

    @Test fun lowConfidenceFramesAreLeftOutWithoutBreakingTheHold() {
        val f = feed()
        assertEquals(null to 8, f.hold(hwang, 8))
        // 신뢰도 1 − 0.5 = 0.5 < 0.8: 판정에서 빼지만 이어짐은 끊지 않는다.
        assertEquals(null to 5, f.hold(hwang, 5, difference = 0.5))
        assertEquals(0 to 3, f.hold(hwang, 10))
    }

    @Test fun octaveCountsOnlyWhenCriteriaAllowIt() {
        assertEquals(null to 40, feed().hold(hwang * 2, 40))
        assertEquals(0 to 16, feed(criteria.copy(octaveMustMatch = false)).hold(hwang * 2, 40))
    }

    @Test fun firstNoteTuningShiftsLaterTargets() {
        val tae = hwang * 2.0.pow(2 / 12.0)
        val tuned = feed()
        assertEquals(0 to 16, tuned.hold(cents(hwang, 40.0), 40))
        tuned.matcher.target(taeNote)
        // 첫 음이 40 cent 높았으므로 태를 20 cent 낮게 불면 보정 뒤 60 cent 낮다.
        assertEquals(null to 40, tuned.hold(cents(tae, -20.0), 40))
        assertEquals(1 to 16, tuned.hold(cents(tae, 40.0), 40))

        val none = feed(criteria.copy(firstNoteTuning = false))
        assertEquals(0 to 16, none.hold(cents(hwang, 40.0), 40))
        none.matcher.target(taeNote)
        assertEquals(1 to 16, none.hold(cents(tae, -20.0), 40))

        // 첫 음 편차가 보정 한도를 넘으면 보정하지 않는다.
        val capped = feed(criteria.copy(maxTuningOffsetCent = 30.0))
        assertEquals(0 to 16, capped.hold(cents(hwang, 40.0), 40))
        capped.matcher.target(taeNote)
        assertEquals(1 to 16, capped.hold(cents(tae, -20.0), 40))
    }

    @Test fun repeatedNoteNeedsABreakBeforeItCounts() {
        val f = feed()
        assertEquals(0 to 16, f.hold(hwang, 40))
        f.matcher.target(StepTarget(1, ScoreNote.SourcePitch("황", 0)))
        assertEquals(null to 40, f.hold(hwang, 40))
        assertNull(f.frame(null))
        assertEquals(1 to 16, f.hold(hwang, 40))
    }

    @Test fun movingToTheNextNoteNeedsNoBreak() {
        val f = feed()
        assertEquals(0 to 16, f.hold(hwang, 40))
        f.matcher.target(taeNote)
        assertEquals(null to 5, f.hold(hwang, 5))
        assertEquals(1 to 16, f.hold(hwang * 2.0.pow(2 / 12.0), 40))
    }

    @Test fun judgeStartsTheMicrophoneOnceReportsMatchesAndDiscardsTheRecording() = runTest {
        val recorder = StepRecorder()
        var failed = false
        val judge = PitchStepNoteJudge(recorder, recorder.readings, StepPitchMatcher(criteria, hwang), false, backgroundScope) { failed = true }
        val matched = mutableListOf<Int>()
        judge.listen(hwangNote) { matched += it }
        judge.listen(hwangNote) { matched += it }
        assertEquals(1, recorder.starts)
        repeat(16) { recorder.send(hwang) }
        runCurrent()
        assertEquals(listOf(0), matched)

        judge.stop()
        assertEquals(1, recorder.cancels)
        recorder.send(hwang)
        runCurrent()
        assertEquals(listOf(0), matched)
        judge.stop()
        assertEquals(1, recorder.cancels)
        assertFalse(failed)
    }

    @Test fun judgeReportsAMicrophoneThatCannotStart() = runTest {
        val recorder = StepRecorder(failStart = true)
        var failed = false
        val judge = PitchStepNoteJudge(recorder, recorder.readings, StepPitchMatcher(criteria, hwang), false, backgroundScope) { failed = true }
        judge.listen(hwangNote) {}
        assertTrue(failed)
        assertEquals(1, recorder.cancels)
    }

    @Test fun judgePassesSigimsaeNotesWhenCriteriaSkipThem() = runTest {
        val recorder = StepRecorder()
        val judge = PitchStepNoteJudge(recorder, recorder.readings, StepPitchMatcher(criteria, hwang), true, backgroundScope) {}
        val matched = mutableListOf<Int>()
        judge.listen(taeNote.copy(sigimsae = true)) { matched += it }
        assertEquals(listOf(1), matched)
        assertEquals(0, recorder.starts)
    }

    @Test fun judgeIsOnlyMadeForSongsWithCountablePitches() = runTest {
        val song = com.ssafy.eolssu.data.song.smallScoreSong()
        val recorder = StepRecorder()
        assertTrue(pitchStepJudgeFor(song, criteria, recorder, backgroundScope) {} != null)
        assertNull(pitchStepJudgeFor(song, criteria, null, backgroundScope) {})
        assertNull(pitchStepJudgeFor(song, criteria, StepRecorder(observes = false), backgroundScope) {})
        assertNull(pitchStepJudgeFor(song.copy(score = song.score.copy(timing = null)), criteria, recorder, backgroundScope) {})
    }
}

/** 음높이 관측을 직접 보내는 녹음기. */
internal class StepRecorder(private val failStart: Boolean = false, observes: Boolean = true) : AudioRecorder {
    private val flow = MutableSharedFlow<PitchReading>(extraBufferCapacity = 64)
    override val pitchReadings: Flow<PitchReading>? = flow.takeIf { observes }
    val readings: Flow<PitchReading> get() = flow
    override val measuresLevel = false
    var starts = 0
    var cancels = 0
    private var recording = 0L
    private var sequence = 0L

    override fun start() {
        starts++
        if (failStart) error("마이크를 쓸 수 없음")
        recording++
        sequence = 0
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop(): RecordedAudio? = null
    override fun cancel() { cancels++ }
    override fun level() = 0f

    fun send(hz: Double?) {
        val end = sequence * 20 + 46
        flow.tryEmit(PitchReading(recording, 1, sequence++, end - 46, end, 1000, hz, 0, 0.05))
    }
}

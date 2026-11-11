package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.YinPitchEstimator
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import com.ssafy.eolssu.feature.practice.component.GaugeNeedle
import com.ssafy.eolssu.feature.practice.component.gaugeFraction
import com.ssafy.eolssu.feature.practice.component.gaugeRangeCents
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** 한 음씩 디버그 음높이 막대 값(S15P31E102-666). 관측은 1초에 1,000샘플, 20샘플(20ms)씩 이어지는 고정 입력이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStepGaugeTest {
    private val hwang = 311.13
    private val criteria = NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, octaveMustMatch = true,
        firstNoteTuning = true, maxTuningOffsetCent = 50.0, skipSigimsae = false, hintAfterMs = 8000)
    private val hwangNote = StepTarget(0, ScoreNote.SourcePitch("황", 0))
    private val taeNote = StepTarget(1, ScoreNote.SourcePitch("태", 0))
    private val taeHz = hwang * 2.0.pow(2 / 12.0)

    private fun cents(hz: Double, cents: Double) = hz * 2.0.pow(cents / 1200)

    private class Feed(val matcher: StepPitchMatcher) {
        var sequence = 0L
        fun frame(hz: Double?, difference: Double? = 0.05, raw: Double? = hz, available: Boolean = true, dbfs: Double? = null): StepPitchGauge? {
            val end = sequence * 20 + 46
            matcher.accept(PitchReading(1, 1, sequence++, end - 46, end, 1000, hz, 0, difference, available, raw, dbfs))
            return matcher.gauge
        }

        fun repeat(hz: Double?, count: Int): StepPitchGauge? {
            var last: StepPitchGauge? = null
            repeat(count) { last = frame(hz) }
            return last
        }
    }

    private fun feed() = Feed(StepPitchMatcher(criteria, hwang).apply { target(hwangNote) })

    private fun near(expected: Double, actual: Double?, within: Double = 0.5) =
        assertTrue(actual != null && abs(expected - actual) <= within, "expected $expected but was $actual")

    @Test fun holdingFillsTheHoldBarUntilTheNoteMatches() {
        val f = feed()
        val first = assertNotNull(f.frame(hwang))
        assertEquals(StepPitchState.Holding, first.state)
        assertEquals(0.0, first.hold)
        near(hwang, first.targetHz, 0.01)
        near(0.0, first.cents)
        assertEquals(0.95, first.confidence!!, 1e-9)
        assertEquals(0.8, first.minConfidence)
        assertEquals(50.0, first.toleranceCent)
        // 8번째 관측: 첫 관측 끝부터 140ms가 지나 300ms의 140/300을 채웠다.
        assertEquals(140.0 / 300, assertNotNull(f.repeat(hwang, 7)).hold, 1e-9)
        val matched = assertNotNull(f.repeat(hwang, 8))
        assertEquals(StepPitchState.Matched, matched.state)
        assertEquals(1.0, matched.hold)
        near(0.0, matched.tuningCent)
    }

    @Test fun outOfRangeShowsHowFarTheSoundIs() {
        val gauge = assertNotNull(feed().frame(cents(hwang, 60.0)))
        assertEquals(StepPitchState.OutOfRange, gauge.state)
        near(60.0, gauge.cents)
        assertEquals(0, gauge.octaves)
        assertEquals(0.0, gauge.hold)
    }

    @Test fun sameNoteInAnotherOctaveIsShownApartFromTheCents() {
        val up = assertNotNull(feed().frame(cents(hwang * 2, 10.0)))
        assertEquals(StepPitchState.WrongOctave, up.state)
        assertEquals(1, up.octaves)
        near(10.0, up.cents)
        // 옥타브를 떼어도 허용 범위 밖이면 그냥 범위 밖이다.
        val far = assertNotNull(feed().frame(cents(hwang / 2, 80.0)))
        assertEquals(StepPitchState.OutOfRange, far.state)
        assertEquals(-1, far.octaves)
        near(80.0, far.cents)
    }

    @Test fun eachReasonThatBreaksOrSkipsTheHoldHasItsOwnState() {
        val f = feed()
        assertEquals(StepPitchState.Silent, f.frame(null, raw = null)?.state)
        val unconfirmed = assertNotNull(f.frame(null, raw = cents(hwang, 30.0)))
        assertEquals(StepPitchState.Unconfirmed, unconfirmed.state)
        near(30.0, unconfirmed.cents)
        assertEquals(StepPitchState.Dropped, f.frame(null, difference = null, raw = null, available = false)?.state)
        f.repeat(hwang, 5)
        val low = assertNotNull(f.frame(hwang, difference = 0.5))
        assertEquals(StepPitchState.LowConfidence, low.state)
        assertEquals(0.5, low.confidence!!, 1e-9)
        // 판정에서 뺀 창도 이어짐은 끊지 않아 유지 막대가 그대로 찬다.
        assertEquals(100.0 / 300, low.hold, 1e-9)
    }

    @Test fun missingPitchIsSplitIntoQuietAndUnclearByTheWindowLevel() {
        val f = feed()
        val quiet = assertNotNull(f.frame(null, difference = null, raw = null, dbfs = -52.3))
        assertEquals(StepPitchState.Quiet, quiet.state)
        assertEquals(-52.3, quiet.levelDbfs)
        assertEquals(StepPitchState.Quiet, f.frame(null, difference = null, raw = null, dbfs = Double.NEGATIVE_INFINITY)?.state)
        val unclear = assertNotNull(f.frame(null, difference = null, raw = null, dbfs = -20.0))
        assertEquals(StepPitchState.Aperiodic, unclear.state)
        assertEquals(-20.0, unclear.levelDbfs)
        // 높이를 잡은 창도 크기를 함께 보인다.
        assertEquals(-18.5, f.frame(hwang, dbfs = -18.5)?.levelDbfs)
    }

    @Test fun windowLevelMatchesTheEstimatorGate() {
        val noise = Random(666)
        val loudNoise = FloatArray(2048) { (noise.nextDouble() * 0.6 - 0.3).toFloat() }
        val minimum = PitchGate.Default.minLevelDbfs
        assertNull(YinPitchEstimator.estimate(loudNoise, 44_100))
        assertTrue(YinPitchEstimator.rmsDbfs(loudNoise) > minimum)
        val quietTone = FloatArray(2048) { i -> (0.005 * sin(2 * PI * 466.0 * i / 44_100)).toFloat() }
        assertTrue(YinPitchEstimator.rmsDbfs(quietTone) < minimum)
        assertNull(YinPitchEstimator.estimate(quietTone, 44_100))
        val tone = FloatArray(2048) { i -> (0.01 * sin(2 * PI * 466.0 * i / 44_100)).toFloat() }
        assertTrue(YinPitchEstimator.rmsDbfs(tone) > minimum)
        assertNotNull(YinPitchEstimator.estimate(tone, 44_100))
        assertEquals(Double.NEGATIVE_INFINITY, YinPitchEstimator.rmsDbfs(FloatArray(2048)))
    }

    @Test fun theMatchedNoteStillSoundingWaitsForABreak() {
        val f = feed()
        assertEquals(StepPitchState.Matched, f.repeat(hwang, 16)?.state)
        f.matcher.target(taeNote)
        val releasing = assertNotNull(f.frame(hwang))
        assertEquals(StepPitchState.Releasing, releasing.state)
        near(taeHz, releasing.targetHz, 0.01)
        near(-200.0, releasing.cents)
    }

    @Test fun centsAreMeasuredAfterTheFirstNoteTuning() {
        val f = feed()
        assertEquals(StepPitchState.Matched, f.repeat(cents(hwang, 30.0), 16)?.state)
        f.matcher.target(taeNote)
        f.frame(null, raw = null)
        val next = assertNotNull(f.frame(cents(taeHz, 30.0)))
        near(30.0, next.tuningCent)
        near(0.0, next.cents)
    }

    @Test fun judgeForwardsEveryGaugeAndClearsItOnStop() = runTest {
        val recorder = StepRecorder()
        val seen = mutableListOf<StepPitchGauge?>()
        val judge = PitchStepNoteJudge(recorder, recorder.readings, StepPitchMatcher(criteria, hwang), false, backgroundScope, { seen += it }) {}
        judge.listen(hwangNote) {}
        repeat(3) { recorder.send(hwang) }
        runCurrent()
        assertEquals(3, seen.size)
        assertEquals(StepPitchState.Holding, seen.last()?.state)
        judge.stop()
        assertNull(seen.last())
    }

    @Test fun barPosition() {
        assertEquals(100.0, gaugeRangeCents(50.0))
        assertEquals(140.0, gaugeRangeCents(70.0))
        assertEquals(0.5f, gaugeFraction(0.0, 100.0))
        assertEquals(0.75f, gaugeFraction(50.0, 100.0))
        assertEquals(1f, gaugeFraction(300.0, 100.0))
        assertEquals(0f, gaugeFraction(-300.0, 100.0))
    }

    private fun shown(state: StepPitchState, cents: Double?, octaves: Int = 0) =
        StepPitchGauge(state, 311.0, 311.0, cents, octaves, 0.9, 0.0, null, 50.0, 0.8)

    @Test fun needleIgnoresASingleWindowSpike() {
        val needle = GaugeNeedle()
        assertEquals(10.0, needle.update(shown(StepPitchState.Holding, 10.0)))
        needle.update(shown(StepPitchState.Holding, 10.0))
        // 한 창만 튄 300센트는 최근 3창 중앙값(10)에 묻혀 바늘이 움직이지 않는다.
        near(10.0, needle.update(shown(StepPitchState.OutOfRange, 300.0)), 0.01)
    }

    @Test fun needleFollowsALastingChangeGradually() {
        val needle = GaugeNeedle()
        needle.update(shown(StepPitchState.Holding, 0.0))
        val path = (1..8).map { needle.update(shown(StepPitchState.OutOfRange, 100.0))!! }
        // 두 창째부터 따라가되 한 번에 건너뛰지 않고 100센트로 다가간다.
        assertEquals(0.0, path[0])
        assertTrue(path.zipWithNext().all { (a, b) -> b >= a }, "$path")
        assertTrue(path[1] < 50.0, "$path")
        assertTrue(path.last() > 85.0, "$path")
    }

    @Test fun unreliableWindowsDoNotMoveTheNeedle() {
        val needle = GaugeNeedle()
        needle.update(shown(StepPitchState.Holding, 20.0))
        assertNull(needle.update(shown(StepPitchState.Unconfirmed, 400.0)))
        assertNull(needle.update(shown(StepPitchState.LowConfidence, -400.0)))
        assertNull(needle.update(shown(StepPitchState.Quiet, null)))
        near(20.0, needle.update(shown(StepPitchState.Holding, 20.0)), 0.01)
    }

    @Test fun needleMarksWrongOctaveOnlyWhenItLasts() {
        val needle = GaugeNeedle()
        needle.update(shown(StepPitchState.Holding, 0.0))
        needle.update(shown(StepPitchState.Holding, 0.0))
        needle.update(shown(StepPitchState.WrongOctave, 0.0, octaves = 1))
        assertEquals(false, needle.wrongOctave)
        needle.update(shown(StepPitchState.WrongOctave, 0.0, octaves = 1))
        assertEquals(true, needle.wrongOctave)
    }

    @Test fun stoppingStartsTheNeedleOver() {
        val needle = GaugeNeedle()
        repeat(3) { needle.update(shown(StepPitchState.Holding, -40.0)) }
        assertNull(needle.update(null))
        assertEquals(30.0, needle.update(shown(StepPitchState.Holding, 30.0)))
    }
}

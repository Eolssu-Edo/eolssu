package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.YinPitchEstimator
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

/** 한 음씩 단소 판정 완화(S15P31E102-666). 관측은 1초에 1,000샘플, 20샘플(20ms)씩 이어지는 고정 입력이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStepLeniencyTest {
    private val hwang = 311.13
    private val criteria = NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, octaveMustMatch = true,
        firstNoteTuning = true, maxTuningOffsetCent = 100.0, skipSigimsae = false, hintAfterMs = 8000)
    private val hwangNote = StepTarget(0, ScoreNote.SourcePitch("황", 0))
    private val taeNote = StepTarget(1, ScoreNote.SourcePitch("태", 0))
    private val muNote = StepTarget(0, ScoreNote.SourcePitch("무", 0))

    private fun semitones(n: Int) = hwang * 2.0.pow(n / 12.0)
    private fun cents(hz: Double, cents: Double) = hz * 2.0.pow(cents / 1200)

    private class Feed(val matcher: StepPitchMatcher) {
        var sequence = 0L

        /** 관측 하나를 보내고 맞은 음의 순서를 돌려준다. */
        fun frame(hz: Double?): Int? {
            val end = sequence * 20 + 46
            return matcher.accept(PitchReading(1, 1, sequence++, end - 46, end, 1000, hz, 0, 0.05, true, hz, -20.0))
        }

        /** [count]번 보내며 처음 맞은 음과 그때까지 보낸 수. */
        fun hold(hz: Double?, count: Int): Pair<Int?, Int> {
            repeat(count) { i -> frame(hz)?.let { return it to i + 1 } }
            return null to count
        }
    }

    private fun feed(leniency: StepLeniency, target: StepTarget = hwangNote, songHz: List<Double> = emptyList()) =
        Feed(StepPitchMatcher(criteria, hwang, leniency, songHz).apply { target(target) })

    @Test fun onlyDansoGetsTheLenientRules() {
        assertEquals(StepLeniency.Danso, StepLeniency.forInstrument("DANSO"))
        assertEquals(StepLeniency.Strict, StepLeniency.forInstrument("SOGEUM"))
        assertEquals(StepLeniency.Strict, StepLeniency.forInstrument("JEONGAK_DAEGEUM"))
        assertEquals(StepLeniency.Strict, StepLeniency.forInstrument(null))
        assertEquals(PitchGate.Default, StepLeniency.Strict.gate)
        assertEquals(0, StepLeniency.Strict.gapMs)
    }

    @Test fun dansoKeepsTheHoldThroughAShortGap() {
        // 10창(첫 창 끝부터 180ms) 맞고 3창(60ms) 높이가 빈 뒤 다시 분다.
        val danso = feed(StepLeniency.Danso)
        assertEquals(null to 10, danso.hold(hwang, 10))
        assertEquals(null to 3, danso.hold(null, 3))
        // 빈 시간도 유지에 들어가 첫 창 끝부터 300ms가 되는 3번째 창에서 맞는다.
        assertEquals(0 to 3, danso.hold(hwang, 20))

        val strict = feed(StepLeniency.Strict)
        assertEquals(null to 10, strict.hold(hwang, 10))
        assertEquals(null to 3, strict.hold(null, 3))
        assertEquals(0 to 16, strict.hold(hwang, 20))
    }

    @Test fun dansoStillRestartsAfterAGapLongerThan100Ms() {
        val f = feed(StepLeniency.Danso)
        assertEquals(null to 10, f.hold(hwang, 10))
        // 마지막으로 맞은 창부터 120ms(6창)가 지나면 지운다.
        assertEquals(null to 6, f.hold(null, 6))
        assertEquals(0 to 16, f.hold(hwang, 20))
    }

    @Test fun dansoToleratesAShortWobbleOutOfRange() {
        val danso = feed(StepLeniency.Danso)
        assertEquals(null to 10, danso.hold(hwang, 10))
        assertEquals(null to 2, danso.hold(cents(hwang, 150.0), 2))
        assertEquals(0 to 4, danso.hold(hwang, 20))
        // 범위 밖 음을 계속 불면 넘어가지 않는다.
        assertEquals(null to 40, feed(StepLeniency.Danso).hold(cents(hwang, 150.0), 40))
    }

    @Test fun dansoAcceptsAWideFirstNoteAndTunesTheRestToIt() {
        // 곡 음: 황·태(+200)·중(+500). 첫 음 황은 위 이웃까지 절반(100센트)과 조율 상한(100센트) 중 작은 값까지 받는다.
        val song = listOf(hwang, semitones(2), semitones(5))
        val danso = feed(StepLeniency.Danso, songHz = song)
        assertEquals(0 to 16, danso.hold(cents(hwang, 80.0), 40))
        danso.matcher.target(taeNote)
        danso.frame(null)
        // 첫 음에서 +80센트로 조율했으니 뒤 음도 +80센트로 불면 맞는다.
        assertEquals(1 to 16, danso.hold(cents(semitones(2), 80.0), 40))

        assertEquals(null to 40, feed(StepLeniency.Strict, songHz = song).hold(cents(hwang, 80.0), 40))
    }

    @Test fun wideFirstNoteNeverReachesHalfwayToANeighbor() {
        // 곡 음: 남(+9)·무(+10)·청황(+12). 무는 아래 이웃 남이 100센트라 아래로는 50센트까지만 받는다.
        val song = listOf(semitones(9), semitones(10), semitones(12))
        assertEquals(null to 40, feed(StepLeniency.Danso, muNote, song).hold(cents(semitones(10), -70.0), 40))
        assertEquals(0 to 16, feed(StepLeniency.Danso, muNote, song).hold(cents(semitones(10), 90.0), 40))
    }

    @Test fun lenientGateFindsQuieterAndBreathierTones() {
        val quiet = FloatArray(2048) { i -> (0.004 * sin(2 * PI * 466.0 * i / 44_100)).toFloat() }
        assertNull(YinPitchEstimator.estimate(quiet, 44_100))
        val found = assertNotNull(YinPitchEstimator.estimate(quiet, 44_100, PitchGate.Lenient))
        assertTrue(found.frequencyHz in 460.0..472.0, "$found")
    }

    @Test fun judgeUsesTheLenientGateOnlyWhileListeningToADansoSong() = runTest {
        val danso = StepRecorder()
        val judge = assertNotNull(pitchStepJudgeFor(smallScoreSong(), criteria, danso, backgroundScope) {})
        judge.listen(hwangNote) {}
        assertEquals(PitchGate.Lenient, danso.pitchGate)
        judge.stop()
        assertEquals(PitchGate.Default, danso.pitchGate)

        val sogeum = StepRecorder()
        val data = smallScoreData().let { it.copy(score = it.score.copy(instrument = "SOGEUM")) }
        val sogeumJudge = assertNotNull(pitchStepJudgeFor(smallScoreSong(data), criteria, sogeum, backgroundScope) {})
        sogeumJudge.listen(hwangNote) {}
        assertEquals(PitchGate.Default, sogeum.pitchGate)
        sogeumJudge.stop()
    }
}

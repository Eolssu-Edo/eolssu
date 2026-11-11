package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.PitchConfirmation
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.YinPitchEstimator
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.PI
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * 한 음씩 따라 불기 판정의 재생 검증(S15P31E102-653).
 * 실제 녹음에서 잰 음별 F0(`data/annotations/audio/{danso,sogeum}`의 `labelF0Hz`)로 소리를 만들고, Android 녹음기와 같은 창·간격으로
 * YIN → 연속 두 창 확인 → [StepPitchMatcher]를 차례로 거쳐 음마다 몇 초 만에 통과하는지 잰다.
 * 원본 녹음(data/raw)은 저장소에 없으므로 녹음 자체가 아니라 그 녹음의 높이를 재현한다. 김 소리·떨림·음 사이 이음은 단순화했다.
 * 기준값은 서버 `application.yml`의 note-step-1(네 악기 같은 값)이다.
 */
class PracticeStepReplayTest {
    private val hwang = 311.13
    private val criteria = NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, octaveMustMatch = true,
        firstNoteTuning = true, maxTuningOffsetCent = 50.0, skipSigimsae = false, hintAfterMs = 8000)

    /** 불 음 하나: 악보 율명·옥타브와 실제로 낸 높이. */
    private data class Blown(val yul: String, val octave: Int, val hz: Double)

    /** 단소 기본음계 평취 중(ngc-danso-2339). 황 기준 −3~+21센트. */
    private val dansoMedium = listOf(Blown("중", 0, 414.588), Blown("임", 0, 465.528), Blown("무", 0, 554.36),
        Blown("황", 1, 622.493), Blown("태", 1, 700.56))

    /** 소금 기본음 중 평취(ngc-sogeum-2655). 같은 황 기준에서 +53~+83센트 높다. */
    private val sogeumMedium = listOf(Blown("임", 0, 484.59), Blown("남", 0, 539.459), Blown("무", 0, 581.484),
        Blown("황", 1, 644.32), Blown("태", 1, 726.044))

    private fun Blown.shifted(cents: Double) = copy(hz = hz * 2.0.pow(cents / 1200))
    private fun Blown.target() = targetHz(ScoreNote.SourcePitch(yul, octave), hwang)!!
    private fun Blown.cents() = 1200 * log2(hz / target())

    /**
     * [notes]를 [noteSec]초씩, 사이에 [gapSec]초 숨을 두고 분다. 음마다 그 음 시작부터 판정이 통과하기까지 걸린 초, 끝까지
     * 통과하지 못하면 null이다. 앞 음이 통과하지 못하면 판정기는 그 음을 계속 기다리므로 뒤 음도 null이 된다.
     * [dropEvery]가 있으면 그 수의 창마다 하나를 분석 불가로 바꾼다(녹음기 분석 대기열이 넘칠 때와 같은 관측).
     */
    private fun replay(notes: List<Blown>, noteSec: Double = 0.8, gapSec: Double = 0.15, dropEvery: Int? = null): List<Double?> {
        val noise = Random(653)
        val starts = notes.indices.map { (it * (noteSec + gapSec) * RATE).roundToInt() }
        val length = (notes.size * (noteSec + gapSec) * RATE).roundToInt()
        val audio = FloatArray(length)
        notes.forEachIndexed { k, note ->
            for (i in starts[k] until starts[k] + (noteSec * RATE).roundToInt()) {
                val t = (i - starts[k]).toDouble() / RATE
                // 단소 단음의 배음 비(YinPitchEstimatorTest의 단소 모양)와 작은 김 소리.
                audio[i] = (0.3 * sin(2 * PI * note.hz * t) + 0.25 * sin(4 * PI * note.hz * t) +
                    0.1 * sin(6 * PI * note.hz * t) + 0.02 * (noise.nextDouble() * 2 - 1)).toFloat()
            }
        }
        val matcher = StepPitchMatcher(criteria, hwang)
        val confirmation = PitchConfirmation()
        val passed = MutableList<Double?>(notes.size) { null }
        var waiting = 0
        fun aim() = matcher.target(StepTarget(waiting, ScoreNote.SourcePitch(notes[waiting].yul, notes[waiting].octave)))
        aim()
        var sequence = 0L
        var start = 0
        while (start + WINDOW <= length && waiting < notes.size) {
            val available = dropEvery == null || (sequence + 1) % dropEvery != 0L
            val estimate = if (available) YinPitchEstimator.estimate(audio.copyOfRange(start, start + WINDOW), RATE) else null
            val reading = confirmation.apply(PitchReading(1, 1, sequence++, start.toLong(), (start + WINDOW).toLong(), RATE,
                estimate?.frequencyHz, 0, estimate?.normalizedDifference, available, estimate?.frequencyHz))
            matcher.accept(reading)?.let { index ->
                passed[index] = (start + WINDOW - starts[index]).toDouble() / RATE
                waiting++
                if (waiting < notes.size) aim()
            }
            start += HOP
        }
        println(notes.indices.joinToString("\n", "replay dropEvery=$dropEvery\n") { k ->
            val n = notes[k]
            "  ${n.yul}${n.octave} target=${n.target().fmt()}Hz blown=${n.hz.fmt()}Hz ${n.cents().fmt()}c passed=${passed[k]?.let { "${it.fmt()}s" } ?: "-"}"
        })
        return passed
    }

    private fun Double.fmt() = ((this * 10).roundToInt() / 10.0).toString()

    @Test fun dansoBasicScalePassesEveryNoteSoonAfterItStarts() {
        val passed = replay(dansoMedium)
        // 소리가 난 뒤 첫 창(46ms)·두 창 확인(20ms)·유지 300ms면 약 0.37초다.
        passed.forEach { assertNotNull(it); assertTrue(it in 0.3..0.5, "$passed") }
    }

    @Test fun sogeumTunedOver50CentsHighNeverPassesTheFirstNote() {
        assertTrue(sogeumMedium.all { it.cents() > 50 })
        // 첫 음 조율 보정은 첫 음이 ±50센트 안에서 맞은 뒤에야 재므로, 악기 전체가 50센트 넘게 어긋나면 첫 음에서 멈춘다.
        assertEquals(List(sogeumMedium.size) { null }, replay(sogeumMedium))
    }

    // 재생 한 번이 브라우저(Wasm·JS) 테스트 제한 2초의 상당 부분을 쓰므로 테스트 하나에 재생을 한 번만 둔다(S15P31E102-735).

    // 같은 단소를 통째로 올려 분다. +45센트까지는 첫 음 조율이 흡수해 모두 통과하고, ±60센트면 하나도 통과하지 못한다.
    @Test fun wholeInstrumentOffsetOf45CentsStillPassesEveryNote() {
        assertTrue(replay(dansoMedium.map { it.shifted(45.0) }).all { it != null })
    }

    @Test fun wholeInstrumentOffsetOf60CentsHighPassesNoNote() {
        assertTrue(replay(dansoMedium.map { it.shifted(60.0) }).all { it == null })
    }

    @Test fun wholeInstrumentOffsetOf60CentsLowPassesNoNote() {
        assertTrue(replay(dansoMedium.map { it.shifted(-60.0) }).all { it == null })
    }

    // 300ms 유지에는 끊김 없는 확인 창 약 17개(약 0.34초)가 필요하다. 분석 불가 창이 그보다 자주 끼면 맞게 불어도 넘어가지 않는다.
    @Test fun analysisGapEvery25WindowsStillPassesEveryNote() {
        assertTrue(replay(dansoMedium, dropEvery = 25).all { it != null })
    }

    @Test fun analysisGapEvery15WindowsBlocksEveryNote() {
        assertTrue(replay(dansoMedium, dropEvery = 15).all { it == null })
    }

    private companion object {
        // AndroidAudioRecorder와 같은 값.
        const val RATE = 44_100
        const val WINDOW = 2048
        const val HOP = 882
    }
}

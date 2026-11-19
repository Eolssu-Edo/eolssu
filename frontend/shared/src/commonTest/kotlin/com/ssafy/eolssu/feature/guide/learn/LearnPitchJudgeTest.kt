package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.StepLeniency
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 기초부터 배우기 4~6단계 음높이 판정(S15P31E102-611)과 연습 화면 한 음씩과 같은 기준(S15P31E102-857)·7단계 같은 음 혀치기(S15P31E102-845). 관측은 1초에 1,000샘플, 20샘플(20ms)씩 이어지는 고정 입력이다.
 * 목표 높이는 단소 황종 E♭4(MIDI 63)에서 센다.
 */
class LearnPitchJudgeTest {
    private val criteria = NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, octaveMustMatch = true,
        firstNoteTuning = true, maxTuningOffsetCent = 50.0, skipSigimsae = false, hintAfterMs = 8000)

    private val dansoDetail = LearnStageDetail(
        goal = "", why = "", source = "",
        pitchNames = listOf("汰", "潢", "無", "林", "仲"),
        ranges = listOf(LearnRange(Blow.High, "역취", "", "㳞")),
    )
    private val scale = listOf("仲", "林", "無", "潢", "汰")

    private fun midi(n: Int) = 440 * 2.0.pow((n - 69) / 12.0)
    private fun cents(hz: Double, cents: Double) = hz * 2.0.pow(cents / 1200)
    private fun hz(hanja: String) = assertNotNull(learnTargetHz(hanja, GuideInstrument.Danso))

    private fun plan(
        stage: Int,
        detail: LearnStageDetail = dansoDetail,
        criteria: NotePracticeCriteria = this.criteria,
        instrument: GuideInstrument = GuideInstrument.Danso,
    ) = assertNotNull(learnPitchPlan(stage, detail.copy(pitchNames = if (stage == 5) scale else detail.pitchNames), instrument, criteria))

    /** 관측을 차례로 넣는다. 결과(맞음·틀림)를 모두 모은다. */
    private class Feed(val tracker: LearnPitchTracker) {
        var sequence = 0L
        val results = mutableListOf<LearnNoteResult>()

        fun frame(hz: Double?, difference: Double = 0.05) {
            val end = sequence * 20 + 46
            tracker.accept(PitchReading(1, 1, sequence++, end - 46, end, 1000, hz, 0, difference))?.let { results += it }
        }

        /** [ms] 동안 [hz]를 20ms마다 넣는다. */
        fun hold(hz: Double?, ms: Int, difference: Double = 0.05): Feed {
            repeat(ms / 20) { frame(hz, difference) }
            return this
        }

        /** 숨·혀로 끊음. */
        fun breath() = hold(null, 200)
    }

    private fun feed(stage: Int, criteria: NotePracticeCriteria = this.criteria, instrument: GuideInstrument = GuideInstrument.Danso) =
        Feed(LearnPitchTracker(plan(stage, criteria = criteria, instrument = instrument)))

    /** 판정기 자체(끊김·신뢰도·조율)를 보는 검사용. 4단계 음 순서에 한 음씩 유지 시간([criteria], 0.3초)을 쓴다. 4단계 화면은 1초다. */
    private fun stepFeed(criteria: NotePracticeCriteria = this.criteria, instrument: GuideInstrument = GuideInstrument.Danso) =
        plan(4, criteria = criteria, instrument = instrument).let { Feed(LearnPitchTracker(it.copy(rule = it.rule.copy(holdMs = criteria.holdMs)))) }

    @Test fun targetHeightFollowsTheGuideHwangAndSounding() {
        // 단소 林 = 황종 E♭4 + 7반음 = B♭4, 汰(청태) = 한 옥타브 위 태 = F5
        assertTrue(abs(hz("林") - midi(70)) < 1e-9)
        assertTrue(abs(hz("汰") - midi(77)) < 1e-9)
        // 산조대금 황종은 F4, 소금은 운지표 율명보다 한 옥타브 높게 난다
        assertTrue(abs(learnTargetHz("黃", GuideInstrument.DaegeumSanjo)!! - midi(65)) < 1e-9)
        assertTrue(abs(learnTargetHz("林", GuideInstrument.Sogeum)!! - midi(82)) < 1e-9)
        assertNull(learnTargetHz("없음", GuideInstrument.Danso))
    }

    /** 4~6단계는 연습 화면 한 음씩과 같은 서버 기준·악기별 완화를 쓰고, 6단계만 옥타브를 맞춘다(S15P31E102-857). 4단계만 유지 시간이 1초다(S15P31E102-860). */
    @Test fun plansUseTheStepPracticeCriteriaAndLeniency() {
        val four = plan(4)
        assertEquals(dansoDetail.pitchNames, four.notes)
        assertEquals(5, four.required)
        assertEquals(LearnPitchRule(50.0, 1_000, 0.8, octaveMustMatch = false, firstNoteTuning = true, maxTuningOffsetCent = 50.0,
            leniency = StepLeniency.Danso), four.rule)
        assertEquals(PitchGate.Lenient, four.rule.leniency.gate)

        val five = plan(5)
        assertEquals(scale + scale.reversed(), five.notes)
        assertEquals(10, five.required)
        assertEquals(four.rule.copy(holdMs = 300), five.rule)

        val six = plan(6)
        assertEquals(listOf("㳞"), six.notes)
        assertEquals(five.rule.copy(octaveMustMatch = true), six.rule)

        // 두 대금은 한 음씩처럼 대금 완화를 쓰고(S15P31E102-866), 소금은 완화 없이 서버 기준 그대로다
        listOf(GuideInstrument.DaegeumJeongak, GuideInstrument.DaegeumSanjo).forEach {
            assertEquals(StepLeniency.Daegeum, plan(4, instrument = it).rule.leniency)
        }
        assertEquals(StepLeniency.Strict, plan(4, instrument = GuideInstrument.Sogeum).rule.leniency)
    }

    /** 허용 오차·유지 시간·조율 상한은 서버 기준을 넓히거나 좁히지 않고 그대로 쓴다. 4단계 유지 시간만 서버 기준과 상관없이 1초다. 3단계 첫 소리도 4단계와 같은 1초다. */
    @Test fun criteriaValuesFlowStraightIntoTheRule() {
        listOf(4, 5, 6).forEach { stage ->
            val danso = plan(stage, criteria = criteria.copy(pitchToleranceCent = 70.0, holdMs = 200, maxTuningOffsetCent = 100.0)).rule
            assertEquals(70.0, danso.toleranceCent)
            assertEquals(if (stage == 4) 1_000 else 200, danso.holdMs)
            assertEquals(100.0, danso.maxTuningOffsetCent)
            assertEquals(25.0, plan(stage, criteria = criteria.copy(pitchToleranceCent = 25.0)).rule.toleranceCent)
        }
        assertEquals(1_000, FIRST_SOUND_HOLD_MS)
    }

    /**
     * 3단계 첫 소리는 정해진 음 없이 1초 동안 같은 높이를 이어 내야 한다(S15P31E102-1216). 허용 오차·신뢰도는 공통 값과 서버 기준 가운데 넓은 쪽,
     * 끊김 허용·높이 문턱은 한 음씩과 같은 악기별 완화다.
     */
    @Test fun firstSoundPlanUsesTheInstrumentLeniencyAndTheWiderTolerance() {
        val danso = learnFirstSoundPlan(GuideInstrument.Danso, null)
        assertEquals(1, danso.required)
        assertTrue(danso.targetsHz.isEmpty())
        assertEquals(LearnPitchRule(LEARN_PITCH_TOLERANCE_CENT, FIRST_SOUND_HOLD_MS, LEARN_PITCH_MIN_CONFIDENCE, octaveMustMatch = true,
            firstNoteTuning = false, maxTuningOffsetCent = 0.0, leniency = StepLeniency.Danso), danso.rule)
        assertEquals(70.0, learnFirstSoundPlan(GuideInstrument.Danso, criteria.copy(pitchToleranceCent = 70.0)).rule.toleranceCent)
        assertEquals(LEARN_PITCH_TOLERANCE_CENT, learnFirstSoundPlan(GuideInstrument.Danso, criteria.copy(pitchToleranceCent = 25.0)).rule.toleranceCent)
        assertEquals(0.7, learnFirstSoundPlan(GuideInstrument.Danso, criteria.copy(minConfidence = 0.7)).rule.minConfidence)
        assertEquals(StepLeniency.Daegeum, learnFirstSoundPlan(GuideInstrument.DaegeumSanjo, null).rule.leniency)
        assertEquals(StepLeniency.Strict, learnFirstSoundPlan(GuideInstrument.Sogeum, null).rule.leniency)
    }

    private fun firstSound(instrument: GuideInstrument = GuideInstrument.Danso) =
        Feed(LearnPitchTracker(learnFirstSoundPlan(instrument, null)))

    @Test fun firstSoundPassesWhenAnyPitchIsHeldSteadyForOneSecond() {
        // 정해진 음이 없어 어느 높이든 1초 이어 내면 통과한다. 떨림(5Hz, ±30센트, 꼭대기에서 시작)은 같은 높이다. 높이 문턱이 기본인 소금도 같다.
        listOf(GuideInstrument.Danso to midi(70), GuideInstrument.Danso to midi(65), GuideInstrument.Sogeum to midi(82)).forEach { (instrument, note) ->
            val short = firstSound(instrument).hold(note, 900)
            assertTrue(short.results.isEmpty())
            assertFalse(short.tracker.passed)

            val held = firstSound(instrument)
            repeat(55) { held.frame(cents(note, 30 * cos(2 * PI * it / 10))) }
            assertEquals(listOf(LearnNoteResult.Hit), held.results)
            assertTrue(held.tracker.passed)
            assertTrue(held.tracker.finished)
        }
    }

    /** 말소리처럼 높이가 계속 움직이거나, 소리는 크지만 높이가 잡히지 않으면(박수·잡음) 아무리 길어도 통과하지 못한다. */
    @Test fun firstSoundDoesNotPassWhenThePitchWandersOrIsMissing() {
        // 20ms마다 8센트씩 오르내리는 미끄러짐(초당 400센트): 기준에서 ±50센트를 벗어날 때마다 새로 잰다
        val glide = firstSound()
        repeat(250) { i -> glide.frame(cents(midi(57), 8.0 * (i % 60) * if ((i / 60) % 2 == 0) 1 else -1)) }
        assertTrue(glide.results.isEmpty())
        // 음절마다 높이가 바뀌는 말(0.3초씩 2~3반음 차이)
        val speech = firstSound()
        repeat(6) { listOf(0.0, 250.0, -200.0).forEach { c -> speech.hold(cents(midi(57), c), 300) } }
        assertTrue(speech.results.isEmpty())
        // 높이 없음·낮은 신뢰도만 이어짐
        assertTrue(firstSound().hold(null, 5_000).hold(midi(70), 5_000, difference = 0.5).results.isEmpty())
        // 거의 1초 내다가 다른 높이로 바뀌면 처음부터 다시 잰다
        val switched = firstSound().hold(midi(70), 900).hold(midi(72), 900)
        assertTrue(switched.results.isEmpty())
        assertEquals(listOf(LearnNoteResult.Hit), switched.hold(midi(72), 300).results)
    }

    /** 높이가 잠깐 비는 끊김은 한 음씩처럼 단소·대금만 봐주고, 소금은 그 자리에서 다시 잰다. */
    @Test fun firstSoundForgivesShortDropoutsOnlyForLenientInstruments() {
        val danso = firstSound().hold(midi(70), 600).hold(null, 60).hold(midi(70), 500)
        assertEquals(listOf(LearnNoteResult.Hit), danso.results)

        val sogeum = firstSound(GuideInstrument.Sogeum).hold(midi(82), 600).hold(null, 60).hold(midi(82), 500)
        assertTrue(sogeum.results.isEmpty())
        assertEquals(listOf(LearnNoteResult.Hit), sogeum.hold(midi(82), 600).results)
    }

    @Test fun noPlanForOtherStagesOrUnknownNotes() {
        listOf(3, 7, 8).forEach { assertNull(learnPitchPlan(it, dansoDetail, GuideInstrument.Danso, criteria)) }
        assertNull(learnPitchPlan(4, dansoDetail.copy(pitchNames = emptyList()), GuideInstrument.Danso, criteria))
        assertNull(learnPitchPlan(4, dansoDetail.copy(pitchNames = listOf("林", "없음")), GuideInstrument.Danso, criteria))
        assertNull(learnPitchPlan(6, dansoDetail.copy(ranges = emptyList()), GuideInstrument.Danso, criteria))
    }

    @Test fun stageFourPassesWhenEveryNoteIsHeldForOneSecond() {
        val feed = feed(4)
        dansoDetail.pitchNames.forEach { feed.hold(hz(it), 1_100).breath() }
        assertEquals(List(5) { LearnNoteResult.Hit }, feed.results)
        assertTrue(feed.tracker.passed)
        assertTrue(feed.tracker.finished)
    }

    @Test fun stageFourIgnoresShortWrongAndSilentInput() {
        val feed = feed(4)
        // 1초 전에 끊기면 다시 잰다(서버 한 음씩 0.3초보다 길게 불어도). 다른 음(潢)·50센트 밖·무음은 오래 불어도 맞음이 아니다.
        feed.hold(hz("汰"), 900).breath().hold(hz("汰"), 900).breath()
            .hold(hz("潢"), 5_000).breath()
            .hold(cents(hz("汰"), 60.0), 5_000).breath()
            .hold(null, 5_000)
        assertEquals(emptyList(), feed.results)
        assertEquals(0, feed.tracker.index)
        assertFalse(feed.tracker.passed)

        // 한 옥타브 낮게 불어도 4단계는 율명이 맞으면 된다
        feed.hold(hz("汰") / 2, 1_100)
        assertEquals(listOf(LearnNoteResult.Hit), feed.results)
        assertEquals(1, feed.tracker.index)
    }

    @Test fun lowConfidenceFramesNeitherCountNorBreakTheHold() {
        val feed = stepFeed()
        feed.hold(hz("汰"), 100).hold(hz("汰"), 200, difference = 0.5)
        assertEquals(emptyList(), feed.results)
        feed.hold(hz("汰"), 40)
        assertEquals(listOf(LearnNoteResult.Hit), feed.results)
    }

    /** 단소는 한 음씩처럼 0.1초 이하의 끊김(분석 누락·잠깐 튄 높이)을 봐주고, 그보다 길면 처음부터 다시 잰다(S15P31E102-857). */
    @Test fun dansoKeepsTheHoldThroughShortGapsLikeStepPractice() {
        val dropout = stepFeed()
        dropout.hold(hz("汰"), 100).hold(null, 100).hold(hz("汰"), 120)
        assertEquals(listOf(LearnNoteResult.Hit), dropout.results)

        val glitch = stepFeed()
        glitch.hold(hz("汰"), 100).hold(hz("汰") * 1.5, 100).hold(hz("汰"), 120)
        assertEquals(listOf(LearnNoteResult.Hit), glitch.results)

        val skipped = stepFeed()
        skipped.hold(hz("汰"), 100)
        skipped.sequence += 4 // 관측 네 개가 빠져 0.1초 만에 다음 관측이 옴
        skipped.hold(hz("汰"), 200)
        assertEquals(listOf(LearnNoteResult.Hit), skipped.results)

        val broken = stepFeed()
        broken.hold(hz("汰"), 200).hold(null, 200).hold(hz("汰"), 280)
        assertEquals(emptyList(), broken.results)
        broken.hold(hz("汰"), 40)
        assertEquals(listOf(LearnNoteResult.Hit), broken.results)
    }

    /** 완화가 없는 악기(소금)는 한 음씩처럼 끊김을 봐주지 않는다. 한 관측만 끊겨도 유지 시간을 처음부터 다시 잰다. */
    @Test fun otherInstrumentsRestartTheHoldOnAnyGap() {
        val tae = assertNotNull(learnTargetHz("汰", GuideInstrument.Sogeum))
        val feed = stepFeed(instrument = GuideInstrument.Sogeum)
        feed.hold(tae, 200).hold(null, 20).hold(tae, 300)
        assertEquals(emptyList(), feed.results)
        feed.hold(tae, 20)
        assertEquals(listOf(LearnNoteResult.Hit), feed.results)
    }

    /** 단소는 첫 음 조율 전에 이웃 음과 헷갈리지 않는 범위에서 조율 상한까지 넓게 받는다(한 음씩과 같음). 다른 악기는 ±허용 오차뿐이다. */
    @Test fun dansoTakesTheFirstNoteWideLikeStepPractice() {
        val dansoCriteria = criteria.copy(pitchToleranceCent = 70.0, holdMs = 200, maxTuningOffsetCent = 100.0)
        val danso = stepFeed(dansoCriteria)
        danso.hold(cents(hz("汰"), 90.0), 300)
        assertEquals(listOf(LearnNoteResult.Hit), danso.results)

        val tae = assertNotNull(learnTargetHz("汰", GuideInstrument.Sogeum))
        val sogeum = stepFeed(dansoCriteria, GuideInstrument.Sogeum)
        sogeum.hold(cents(tae, 90.0), 3_000)
        assertEquals(emptyList(), sogeum.results)
    }

    @Test fun firstNoteTuningShiftsLaterTargets() {
        // 첫 음을 +30센트로 맞히면 뒤 음은 +70센트도 ±50센트 안이다. 조율을 쓰지 않으면 아니다.
        val tuned = feed(4)
        tuned.hold(cents(hz("汰"), 30.0), 1_100).breath().hold(cents(hz("潢"), 70.0), 1_100)
        assertEquals(2, tuned.results.size)

        val plain = feed(4, criteria.copy(firstNoteTuning = false))
        plain.hold(cents(hz("汰"), 30.0), 1_100).breath().hold(cents(hz("潢"), 70.0), 1_100)
        assertEquals(1, plain.results.size)
    }

    /** 5단계는 다른 음을 오래 불어도 틀림으로 넘기지 않고, 맞는 음이 날 때까지 기다린다. 열 음을 다 내면 통과다(S15P31E102-749). */
    @Test fun stageFiveWaitsForEachNoteAndPassesWhenAllAreHit() {
        val feed = feed(5)
        val notes = scale + scale.reversed()
        feed.hold(hz("汰"), 2_000).breath()
        assertEquals(emptyList(), feed.results)
        assertEquals(0, feed.tracker.index)
        notes.forEach { feed.hold(hz(it), 1_100).breath() }
        assertEquals(List(10) { LearnNoteResult.Hit }, feed.results)
        assertEquals(0, feed.tracker.misses)
        assertTrue(feed.tracker.passed)
    }

    /** 5단계 내려오기 박스의 체크를 풀면 판정이 그 박스의 첫 음(6번째 음)으로 돌아간다(S15P31E102-749). */
    @Test fun rewindingGoesBackToTheGivenNote() {
        val feed = feed(5)
        (scale + scale.reversed()).forEach { feed.hold(hz(it), 1_100).breath() }
        assertTrue(feed.tracker.finished)

        feed.tracker.rewind(5)
        assertEquals(5, feed.tracker.index)
        assertEquals(5, feed.tracker.hits)
        assertFalse(feed.tracker.finished)
        feed.breath().hold(hz(scale.last()), 1_100)
        assertEquals(6, feed.tracker.index)
    }

    @Test fun stageFiveSilenceIsNotAWrongNote() {
        val feed = feed(5)
        feed.hold(null, 5_000)
        assertEquals(emptyList(), feed.results)
        assertEquals(0, feed.tracker.index)
    }

    @Test fun repeatedNoteNeedsABreakBeforeItCountsAgain() {
        // 올라가기 끝 음과 내려오기 첫 음은 같은 汰다. 이어 불면 한 번만 센다.
        val feed = feed(5)
        scale.dropLast(1).forEach { feed.hold(hz(it), 1_100).breath() }
        feed.hold(hz("汰"), 3_000)
        assertEquals(5, feed.results.size)
        feed.breath().hold(hz("汰"), 1_100)
        assertEquals(6, feed.results.size)
    }

    @Test fun stageSixNeedsTheNewOctave() {
        val wrongOctave = feed(6)
        wrongOctave.hold(hz("㳞") / 2, 3_000)
        assertFalse(wrongOctave.tracker.passed)

        val right = feed(6)
        right.hold(hz("㳞"), 1_100)
        assertTrue(right.tracker.passed)
    }

    /** 대금도 6단계에서는 한 옥타브 낮은 소리를 받지 않는다. 한 음씩의 낮은 옥타브 받기는 기초 배우기에 쓰지 않는다(S15P31E102-866). */
    @Test fun daegeumStageSixStillNeedsTheNewOctave() {
        val high = assertNotNull(learnTargetHz("㳞", GuideInstrument.DaegeumJeongak))
        val low = feed(6, instrument = GuideInstrument.DaegeumJeongak)
        low.hold(high / 2, 3_000)
        assertFalse(low.tracker.passed)

        val right = feed(6, instrument = GuideInstrument.DaegeumJeongak)
        right.hold(high, 1_100)
        assertTrue(right.tracker.passed)
    }

    @Test fun resetStartsOver() {
        val feed = feed(4)
        feed.hold(hz("汰"), 1_100)
        feed.tracker.reset()
        assertEquals(0, feed.tracker.index)
        assertEquals(0, feed.tracker.hits)
    }

    /** 7단계 혀치기: '투'마다 높이를 재어 첫 '투'와 같은 음이면 하나 더 센다. */
    private class Tongue(val tracker: LearnTongueTracker = LearnTongueTracker()) {
        var sequence = 0L

        fun frame(hz: Double?, difference: Double = 0.05) {
            val end = sequence * 20 + 46
            tracker.accept(PitchReading(1, 1, sequence++, end - 46, end, 1000, hz, 0, difference))
        }

        /** 숨을 끊고 [hz]로 '투'를 한 번 낸다(소리 크기로 잡은 시작 뒤 [ms]). */
        fun tu(hz: Double, ms: Int = 200): Tongue {
            repeat(3) { frame(null) }
            tracker.onset()
            repeat(ms / 20) { frame(hz) }
            return this
        }
    }

    @Test fun tonguesOnTheFirstNoteCountUntilAnotherNote() {
        val tongue = Tongue()
        tongue.tu(hz("汰")).tu(cents(hz("汰"), 40.0)).tu(cents(hz("汰"), -40.0))
        assertEquals(3, tongue.tracker.count)
        // 다른 음이면 그 '투'가 새 첫 음이 되어 1부터 다시 센다. 옥타브가 달라도 다른 음이다.
        tongue.tu(hz("潢"))
        assertEquals(1, tongue.tracker.count)
        tongue.tu(hz("潢") * 2)
        assertEquals(1, tongue.tracker.count)
        tongue.tu(hz("潢") * 2).tu(hz("潢") * 2).tu(hz("潢") * 2)
        assertEquals(TONGUE_TARGET, tongue.tracker.count)
        tongue.tracker.reset()
        assertEquals(0, tongue.tracker.count)
    }

    @Test fun aTongueCountsOnceAndNeedsASteadyPitch() {
        val tongue = Tongue()
        // 시작 없이 이어 부는 소리는 세지 않고, 한 '투'를 길게 불면 한 번만 센다
        repeat(20) { tongue.frame(hz("汰")) }
        assertEquals(0, tongue.tracker.count)
        tongue.tu(hz("汰"), ms = 2_000)
        assertEquals(1, tongue.tracker.count)
        // 높이를 정하기 전(0.04초)에 끝난 '투'와 신뢰도가 낮은 '투'는 세지 않는다
        tongue.tu(hz("汰"), ms = 40)
        repeat(3) { tongue.frame(null) }
        tongue.tracker.onset()
        repeat(10) { tongue.frame(hz("汰"), difference = 0.5) }
        assertEquals(1, tongue.tracker.count)
    }

    @Test fun tongueToleranceFollowsTheWiderServerRule() {
        val narrow = Tongue()
        narrow.tu(hz("汰")).tu(cents(hz("汰"), 60.0))
        assertEquals(1, narrow.tracker.count)

        val wide = Tongue(LearnTongueTracker(criteria.copy(pitchToleranceCent = 70.0)))
        wide.tu(hz("汰")).tu(cents(hz("汰"), 60.0))
        assertEquals(2, wide.tracker.count)
    }
}

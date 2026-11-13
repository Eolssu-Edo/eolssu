package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 기초부터 배우기 4~6단계 음높이 판정(S15P31E102-611)과 공통 완화 기준·7단계 같은 음 혀치기(S15P31E102-845). 관측은 1초에 1,000샘플, 20샘플(20ms)씩 이어지는 고정 입력이다.
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

    private fun plan(stage: Int, detail: LearnStageDetail = dansoDetail, criteria: NotePracticeCriteria = this.criteria) =
        assertNotNull(learnPitchPlan(stage, detail.copy(pitchNames = if (stage == 5) scale else detail.pitchNames), GuideInstrument.Danso, criteria))

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

    private fun feed(stage: Int, criteria: NotePracticeCriteria = this.criteria) = Feed(LearnPitchTracker(plan(stage, criteria = criteria)))

    @Test fun targetHeightFollowsTheGuideHwangAndSounding() {
        // 단소 林 = 황종 E♭4 + 7반음 = B♭4, 汰(청태) = 한 옥타브 위 태 = F5
        assertTrue(abs(hz("林") - midi(70)) < 1e-9)
        assertTrue(abs(hz("汰") - midi(77)) < 1e-9)
        // 산조대금 황종은 F4, 소금은 운지표 율명보다 한 옥타브 높게 난다
        assertTrue(abs(learnTargetHz("黃", GuideInstrument.DaegeumSanjo)!! - midi(65)) < 1e-9)
        assertTrue(abs(learnTargetHz("林", GuideInstrument.Sogeum)!! - midi(82)) < 1e-9)
        assertNull(learnTargetHz("없음", GuideInstrument.Danso))
    }

    /** 4~6단계는 같은 완화 기준(±50센트·1초·끊김 0.15초)을 쓰고, 6단계만 옥타브를 맞춘다(S15P31E102-845). */
    @Test fun plansShareTheRelaxedLearnRule() {
        val four = plan(4)
        assertEquals(dansoDetail.pitchNames, four.notes)
        assertEquals(5, four.required)
        assertEquals(LearnPitchRule(50.0, 1_000, 150, 0.8, octaveMustMatch = false, firstNoteTuning = true, maxTuningOffsetCent = 50.0,
            countsMisses = false), four.rule)

        val five = plan(5)
        assertEquals(scale + scale.reversed(), five.notes)
        assertEquals(10, five.required)
        assertEquals(four.rule, five.rule)

        val six = plan(6)
        assertEquals(listOf("㳞"), six.notes)
        assertEquals(four.rule.copy(octaveMustMatch = true), six.rule)
    }

    /** 서버 기준이 공통 허용 오차보다 넓으면(단소 ±70) 그 값을, 좁으면 공통 ±50을 쓴다. 유지 시간은 서버 값과 상관없이 1초다. */
    @Test fun toleranceTakesTheWiderOfServerAndLearnRule() {
        listOf(4, 5, 6).forEach { stage ->
            val wide = plan(stage, criteria = criteria.copy(pitchToleranceCent = 70.0, holdMs = 200)).rule
            assertEquals(70.0, wide.toleranceCent)
            assertEquals(LEARN_PITCH_HOLD_MS, wide.holdMs)
            assertEquals(50.0, plan(stage, criteria = criteria.copy(pitchToleranceCent = 25.0)).rule.toleranceCent)
        }
        assertEquals(LEARN_PITCH_HOLD_MS.toDouble(), FIRST_SOUND_HOLD_MS)
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
        // 0.9초에서 끊기면 다시 잰다. 다른 음(潢)·50센트 밖·무음은 오래 불어도 맞음이 아니다.
        feed.hold(hz("汰"), 900).breath()
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
        val feed = feed(4)
        feed.hold(hz("汰"), 500).hold(hz("汰"), 200, difference = 0.5).hold(hz("汰"), 400)
        assertEquals(listOf(LearnNoteResult.Hit), feed.results)
    }

    /** 0.15초 이하의 끊김(분석 누락)이나 잠깐 튄 높이는 유지 시간을 이어 가고, 그보다 길면 처음부터 다시 잰다(S15P31E102-845). */
    @Test fun shortGapsKeepTheHoldButLongerOnesRestartIt() {
        val dropout = feed(4)
        dropout.hold(hz("汰"), 600).hold(null, 100).hold(hz("汰"), 400)
        assertEquals(listOf(LearnNoteResult.Hit), dropout.results)

        val glitch = feed(4)
        glitch.hold(hz("汰"), 600).hold(hz("汰") * 1.5, 100).hold(hz("汰"), 400)
        assertEquals(listOf(LearnNoteResult.Hit), glitch.results)

        val skipped = feed(4)
        skipped.hold(hz("汰"), 600)
        skipped.sequence += 5 // 관측 다섯 개(0.1초)가 빠짐
        skipped.hold(hz("汰"), 400)
        assertEquals(listOf(LearnNoteResult.Hit), skipped.results)

        val broken = feed(4)
        broken.hold(hz("汰"), 600).hold(null, 200).hold(hz("汰"), 900)
        assertEquals(emptyList(), broken.results)
        broken.hold(hz("汰"), 200)
        assertEquals(listOf(LearnNoteResult.Hit), broken.results)
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

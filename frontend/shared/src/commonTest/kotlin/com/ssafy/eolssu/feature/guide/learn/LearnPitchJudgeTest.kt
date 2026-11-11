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
 * 기초부터 배우기 4~6단계 음높이 판정(S15P31E102-611). 관측은 1초에 1,000샘플, 20샘플(20ms)씩 이어지는 고정 입력이다.
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

    @Test fun plansFollowTheStagePassRules() {
        val four = plan(4)
        assertEquals(dansoDetail.pitchNames, four.notes)
        assertEquals(5, four.required)
        assertEquals(LearnPitchRule(25.0, 2_000, 0.8, octaveMustMatch = false, firstNoteTuning = true, maxTuningOffsetCent = 50.0,
            countsMisses = false), four.rule)

        val five = plan(5)
        assertEquals(scale + scale.reversed(), five.notes)
        assertEquals(10, five.required)
        assertEquals(50.0, five.rule.toleranceCent)
        assertEquals(300, five.rule.holdMs)
        assertFalse(five.rule.countsMisses)

        val six = plan(6)
        assertEquals(listOf("㳞"), six.notes)
        assertEquals(2_000, six.rule.holdMs)
        assertTrue(six.rule.octaveMustMatch)
    }

    @Test fun noPlanForOtherStagesOrUnknownNotes() {
        listOf(3, 7, 8).forEach { assertNull(learnPitchPlan(it, dansoDetail, GuideInstrument.Danso, criteria)) }
        assertNull(learnPitchPlan(4, dansoDetail.copy(pitchNames = emptyList()), GuideInstrument.Danso, criteria))
        assertNull(learnPitchPlan(4, dansoDetail.copy(pitchNames = listOf("林", "없음")), GuideInstrument.Danso, criteria))
        assertNull(learnPitchPlan(6, dansoDetail.copy(ranges = emptyList()), GuideInstrument.Danso, criteria))
    }

    @Test fun stageFourPassesWhenEveryNoteIsHeldForTwoSeconds() {
        val feed = feed(4)
        dansoDetail.pitchNames.forEach { feed.hold(hz(it), 2_100).breath() }
        assertEquals(List(5) { LearnNoteResult.Hit }, feed.results)
        assertTrue(feed.tracker.passed)
        assertTrue(feed.tracker.finished)
    }

    @Test fun stageFourIgnoresShortWrongAndSilentInput() {
        val feed = feed(4)
        // 1.9초에서 끊기면 다시 잰다. 다른 음(潢)·25센트 밖·무음은 오래 불어도 맞음이 아니다.
        feed.hold(hz("汰"), 1_900).breath()
            .hold(hz("潢"), 5_000).breath()
            .hold(cents(hz("汰"), 30.0), 5_000).breath()
            .hold(null, 5_000)
        assertEquals(emptyList(), feed.results)
        assertEquals(0, feed.tracker.index)
        assertFalse(feed.tracker.passed)

        // 한 옥타브 낮게 불어도 4단계는 율명이 맞으면 된다
        feed.hold(hz("汰") / 2, 2_100)
        assertEquals(listOf(LearnNoteResult.Hit), feed.results)
        assertEquals(1, feed.tracker.index)
    }

    @Test fun lowConfidenceFramesNeitherCountNorBreakTheHold() {
        val feed = feed(4)
        feed.hold(hz("汰"), 1_000).hold(hz("汰"), 200, difference = 0.5).hold(hz("汰"), 900)
        assertEquals(listOf(LearnNoteResult.Hit), feed.results)
    }

    @Test fun firstNoteTuningShiftsLaterTargets() {
        // 첫 음을 +20센트로 맞히면 뒤 음은 +40센트도 ±25센트 안이다. 조율을 쓰지 않으면 아니다.
        val tuned = feed(4)
        tuned.hold(cents(hz("汰"), 20.0), 2_100).breath().hold(cents(hz("潢"), 40.0), 2_100)
        assertEquals(2, tuned.results.size)

        val plain = feed(4, criteria.copy(firstNoteTuning = false))
        plain.hold(cents(hz("汰"), 20.0), 2_100).breath().hold(cents(hz("潢"), 40.0), 2_100)
        assertEquals(1, plain.results.size)
    }

    /** 5단계는 다른 음을 오래 불어도 틀림으로 넘기지 않고, 맞는 음이 날 때까지 기다린다. 열 음을 다 내면 통과다(S15P31E102-749). */
    @Test fun stageFiveWaitsForEachNoteAndPassesWhenAllAreHit() {
        val feed = feed(5)
        val notes = scale + scale.reversed()
        feed.hold(hz("汰"), 2_000).breath()
        assertEquals(emptyList(), feed.results)
        assertEquals(0, feed.tracker.index)
        notes.forEach { feed.hold(hz(it), 400).breath() }
        assertEquals(List(10) { LearnNoteResult.Hit }, feed.results)
        assertEquals(0, feed.tracker.misses)
        assertTrue(feed.tracker.passed)
    }

    /** 5단계 내려오기 박스의 체크를 풀면 판정이 그 박스의 첫 음(6번째 음)으로 돌아간다(S15P31E102-749). */
    @Test fun rewindingGoesBackToTheGivenNote() {
        val feed = feed(5)
        (scale + scale.reversed()).forEach { feed.hold(hz(it), 400).breath() }
        assertTrue(feed.tracker.finished)

        feed.tracker.rewind(5)
        assertEquals(5, feed.tracker.index)
        assertEquals(5, feed.tracker.hits)
        assertFalse(feed.tracker.finished)
        feed.breath().hold(hz(scale.last()), 400)
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
        scale.dropLast(1).forEach { feed.hold(hz(it), 400).breath() }
        feed.hold(hz("汰"), 1_400)
        assertEquals(5, feed.results.size)
        feed.breath().hold(hz("汰"), 400)
        assertEquals(6, feed.results.size)
    }

    @Test fun stageSixNeedsTheNewOctave() {
        val wrongOctave = feed(6)
        wrongOctave.hold(hz("㳞") / 2, 3_000)
        assertFalse(wrongOctave.tracker.passed)

        val right = feed(6)
        right.hold(hz("㳞"), 2_100)
        assertTrue(right.tracker.passed)
    }

    @Test fun resetStartsOver() {
        val feed = feed(4)
        feed.hold(hz("汰"), 2_100)
        feed.tracker.reset()
        assertEquals(0, feed.tracker.index)
        assertEquals(0, feed.tracker.hits)
    }
}

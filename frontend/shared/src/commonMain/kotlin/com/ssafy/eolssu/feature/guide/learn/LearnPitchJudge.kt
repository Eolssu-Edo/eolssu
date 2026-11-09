package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.UNKNOWN_PITCH
import com.ssafy.eolssu.core.model.fingering.pitchIndexOf
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

/*
 * 기초부터 배우기 4~6단계의 기기 안 음높이 판정(S15P31E102-611, 결정은 docs/plans/S15P31E102-611.md).
 * 녹음기의 음높이 관측([com.ssafy.eolssu.core.audio.AudioRecorder.pitchReadings], 341의 YIN + 연속 두 창 확인)을 읽고
 * 신뢰도·첫 음 조율은 한 음씩 연습 기준(PRA-06)을 쓴다. 단계별 유지 시간·허용 오차는 서버 단계 상세의 통과 기준 예시다.
 */

/** 4단계 통과 기준 예시: 목표 음과 ±25센트 이내. */
const val LEARN_PITCH_STEADY_CENT = 25.0

/** 4·6단계 통과 기준 예시: 음마다 2초 이상 유지. */
const val LEARN_PITCH_HOLD_MS = 2_000

/** 5단계 통과 기준 예시: 8음 중 7음. 앱 패턴 길이에 맞춰 이 비율을 올림한다. */
const val LEARN_PATTERN_PASS_RATIO = 7.0 / 8

/** 기초부터 배우기 악기의 황종 MIDI 번호. [GuideInstrument.hwang]은 C에서 센 반음이고 E♭4·F4 옥타브에 둔다(악보 공통 형식과 같음). */
private fun GuideInstrument.hwangMidi(): Int = 60 + hwang

/** 운지표 율명(명목 옥타브)보다 실제로 몇 옥타브 높게 나는지. 소금은 대금과 율명이 같지만 한 옥타브 높게 난다. */
private fun GuideInstrument.soundingOctave(): Int = if (this == GuideInstrument.Sogeum) 1 else 0

/** [instrument]로 [hanja]를 냈을 때의 목표 높이(Hz). 운지표에 없는 율명이면 null. */
internal fun learnTargetHz(hanja: String, instrument: GuideInstrument): Double? {
    val index = pitchIndexOf(hanja).takeIf { it != UNKNOWN_PITCH } ?: return null
    return 440 * 2.0.pow((instrument.hwangMidi() + index + 12 * instrument.soundingOctave() - 69) / 12.0)
}

/**
 * 한 음을 맞게 냈는지 보는 규칙.
 *
 * @property countsMisses true면 다른 음을 [holdMs] 이어 낸 것을 틀림으로 세고 다음 음으로 간다(5단계). false면 맞을 때까지 기다린다.
 */
data class LearnPitchRule(
    val toleranceCent: Double,
    val holdMs: Int,
    val minConfidence: Double,
    val octaveMustMatch: Boolean,
    val firstNoteTuning: Boolean,
    val maxTuningOffsetCent: Double,
    val countsMisses: Boolean,
)

/** 단계 연습에서 차례로 낼 음([notes] 율명, [targetsHz] 높이)과 통과에 필요한 맞은 음 수. */
data class LearnPitchPlan(val notes: List<String>, val targetsHz: List<Double>, val required: Int, val rule: LearnPitchRule)

/**
 * [stage]를 음높이로 판정하는 계획. 4~6단계가 아니거나, 낼 음이 없거나, 높이를 셀 수 없는 율명이 있으면 null(스스로 확인).
 * 4단계 익힐 순서 각 음, 5단계 올라가기·내려오기 이어 불기, 6단계 새 음역마다 첫 음.
 */
internal fun learnPitchPlan(stage: Int, detail: LearnStageDetail, instrument: GuideInstrument, criteria: NotePracticeCriteria): LearnPitchPlan? {
    fun rule(tolerance: Double, hold: Int, octave: Boolean, misses: Boolean) = LearnPitchRule(tolerance, hold, criteria.minConfidence,
        octave, criteria.firstNoteTuning, criteria.maxTuningOffsetCent, misses)
    val (notes, rule) = when (stage) {
        4 -> detail.pitchNames to rule(LEARN_PITCH_STEADY_CENT, LEARN_PITCH_HOLD_MS, octave = false, misses = false)
        5 -> (LearnPattern.Ascending.notes(detail.pitchNames) + LearnPattern.Descending.notes(detail.pitchNames)) to
            rule(criteria.pitchToleranceCent, criteria.holdMs, octave = false, misses = true)
        6 -> detail.ranges.map { it.first } to rule(criteria.pitchToleranceCent, LEARN_PITCH_HOLD_MS, octave = true, misses = false)
        else -> return null
    }
    if (notes.isEmpty()) return null
    val targets = notes.map { learnTargetHz(it, instrument) ?: return null }
    val required = if (stage == 5) ceil(notes.size * LEARN_PATTERN_PASS_RATIO - 1e-9).toInt() else notes.size
    return LearnPitchPlan(notes, targets, required, rule)
}

/** 한 음의 판정. */
enum class LearnNoteResult { Hit, Miss }

/**
 * [plan]의 음을 차례로 기다린다. 화면·마이크와 떨어진 계산이라 고정 입력으로 검사한다.
 *
 * - 맞음: 목표와의 차이(첫 음 조율을 뺀 뒤)가 ±[LearnPitchRule.toleranceCent] 안인 관측이 [LearnPitchRule.holdMs] 이상 끊기지 않음.
 *   시간은 녹음 샘플 위치로 잰다.
 * - 틀림([LearnPitchRule.countsMisses]일 때만): 목표 밖의 한 높이(첫 관측 ±허용 오차)가 같은 시간 이어짐.
 * - 끊김: 소리 없음·높이 미확정·분석 불가·관측 누락. 신뢰도(1 − YIN 정규화 차이)가 낮은 관측은 빼고 이어짐도 끊지 않는다.
 * - 판정 직후에는 방금 낸 높이가 계속 들리는 동안 다음 음을 재지 않는다. 같은 음이 이어지면 한 번 끊은 뒤에 센다.
 */
internal class LearnPitchTracker(private val plan: LearnPitchPlan) {
    private val rule = plan.rule
    var index = 0
        private set
    var hits = 0
        private set
    var misses = 0
        private set

    /** 지금 목표 음을 맞게 이어 낸 시간(ms). 화면의 유지 막대에 쓴다. */
    var holdMs = 0.0
        private set

    val passed: Boolean get() = hits >= plan.required

    /** 모든 음을 냈거나, 남은 음을 다 맞혀도 통과할 수 없음. */
    val finished: Boolean get() = passed || index >= plan.notes.size || misses > plan.notes.size - plan.required

    private var last: PitchReading? = null
    private var tuningCent: Double? = null
    private var heldHz: Double? = null
    private var released = true
    private var onStart = 0L
    private val onRun = mutableListOf<Double>()
    private var offStart = 0L
    private var offHz: Double? = null

    fun reset() {
        index = 0
        hits = 0
        misses = 0
        tuningCent = null
        heldHz = null
        released = true
        last = null
        clearRuns()
    }

    /** [reading]까지 들었을 때 지금 음의 판정이 났으면 그 결과, 아니면 null. */
    fun accept(reading: PitchReading): LearnNoteResult? {
        if (finished) return null
        val previous = last
        last = reading
        if (previous == null || previous.recordingId != reading.recordingId || previous.segmentId != reading.segmentId ||
            previous.sequence + 1 != reading.sequence
        ) clearRuns()
        val heard = reading.frequencyHz?.takeIf { reading.analysisAvailable && it.isFinite() && it > 0 }
        if (heard == null) {
            released = true
            clearRuns()
            return null
        }
        if (1 - (reading.normalizedDifference ?: 0.0) < rule.minConfidence) return null
        if (!released) {
            if (heldHz?.let { abs(offset(heard, it)) <= rule.toleranceCent } == true) return null
            released = true
        }
        val goal = plan.targetsHz[index]
        val off = offset(heard, goal)
        if (abs(off - (tuningCent ?: 0.0)) <= rule.toleranceCent) {
            offHz = null
            if (onRun.isEmpty()) onStart = reading.endSample
            onRun += off
            holdMs = (reading.endSample - onStart) * 1000.0 / reading.sampleRate
            if (holdMs < rule.holdMs) return null
            if (rule.firstNoteTuning && tuningCent == null) {
                val middle = onRun.sorted()[onRun.size / 2]
                tuningCent = if (abs(middle) <= rule.maxTuningOffsetCent) middle else 0.0
            }
            hits++
            return finish(goal, LearnNoteResult.Hit)
        }
        onRun.clear()
        holdMs = 0.0
        if (!rule.countsMisses) return null
        val wrong = offHz
        if (wrong == null || abs(1200 * log2(heard / wrong)) > rule.toleranceCent) {
            offHz = heard
            offStart = reading.endSample
            return null
        }
        if ((reading.endSample - offStart) * 1000.0 / reading.sampleRate < rule.holdMs) return null
        misses++
        return finish(wrong, LearnNoteResult.Miss)
    }

    private fun finish(held: Double, result: LearnNoteResult): LearnNoteResult {
        heldHz = held
        released = false
        index++
        clearRuns()
        return result
    }

    private fun clearRuns() {
        onRun.clear()
        offHz = null
        holdMs = 0.0
    }

    /** 목표에서 몇 센트 떨어졌는지. 옥타브를 따지지 않으면 −600 이상 600 미만으로 접는다. */
    private fun offset(heard: Double, goal: Double): Double {
        val cents = 1200 * log2(heard / goal)
        if (rule.octaveMustMatch) return cents
        return ((cents + 600) % 1200 + 1200) % 1200 - 600
    }
}

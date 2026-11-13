package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.StepLeniency
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.UNKNOWN_PITCH
import com.ssafy.eolssu.core.model.fingering.pitchIndexOf
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

/*
 * 기초부터 배우기 4~6단계의 기기 안 음높이 판정(S15P31E102-611, 결정은 docs/plans/S15P31E102-611.md).
 * 녹음기의 음높이 관측([com.ssafy.eolssu.core.audio.AudioRecorder.pitchReadings], 341의 YIN + 연속 두 창 확인)을 읽는다.
 * 허용 오차·유지 시간·신뢰도·첫 음 조율은 연습 화면 한 음씩과 같은 서버 기준(PRA-06)을, 끊김 허용·높이 문턱·첫 음 넓게 받기는
 * 한 음씩과 같은 악기별 완화([StepLeniency])를 그대로 쓴다(S15P31E102-857). 기초 배우기만의 차이는 4·5단계가 옥타브를 따지지 않는 것과
 * 4단계 직접 불기가 음마다 [LEARN_STAGE_FOUR_HOLD_MS] 동안 이어 불어야 하는 것이다(S15P31E102-860).
 * 7단계 혀치기의 같은 음 판정([LearnTongueTracker])은 아래 공통 허용 오차를 따로 쓴다(S15P31E102-845).
 */

/** 4단계 직접 불기에서 한 음을 맞음으로 볼 유지 시간: 1초. 처음 익히는 음을 충분히 이어 불게 하려고 서버 한 음씩 유지 시간 대신 쓴다(S15P31E102-860). */
const val LEARN_STAGE_FOUR_HOLD_MS = 1_000

/** 7단계 혀치기 같은 음의 허용 오차: ±50센트. 서버 한 음씩 연습 기준이 더 넓으면(단소 ±70) 그 값을 쓴다. */
const val LEARN_PITCH_TOLERANCE_CENT = 50.0

/** 판정 기준을 받지 못했을 때 7단계 혀치기가 쓰는 신뢰도 문턱. 서버 한 음씩 연습 기준 초안과 같다. */
const val LEARN_PITCH_MIN_CONFIDENCE = 0.8

/** 7단계 혀치기에서 쓸 허용 오차. 공통 값과 서버 기준([criteria]) 가운데 넓은 쪽이다. */
internal fun learnToleranceCent(criteria: NotePracticeCriteria?): Double =
    max(LEARN_PITCH_TOLERANCE_CENT, criteria?.pitchToleranceCent ?: 0.0)

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
 * 한 음을 맞게 냈는지 보는 규칙. [octaveMustMatch] 말고는 연습 화면 한 음씩의 서버 기준과 악기별 완화를 그대로 옮긴 값이다.
 *
 * @property leniency 악기별 완화. 끊김 허용([StepLeniency.gapMs])·높이 문턱([StepLeniency.gate])·첫 음 넓게 받기.
 *   한 옥타브 낮은 소리 받기([StepLeniency.lowerOctave])는 쓰지 않는다. 옥타브를 따지는 6단계는 새 음역을 실제로 내는지 보는 단계라서다(S15P31E102-866).
 * @property countsMisses true면 다른 음을 [holdMs] 이어 낸 것을 틀림으로 세고 다음 음으로 간다. false면 맞을 때까지 기다린다(지금 단계는 모두 false).
 */
data class LearnPitchRule(
    val toleranceCent: Double,
    val holdMs: Int,
    val minConfidence: Double,
    val octaveMustMatch: Boolean,
    val firstNoteTuning: Boolean,
    val maxTuningOffsetCent: Double,
    val leniency: StepLeniency = StepLeniency.Strict,
    val countsMisses: Boolean = false,
)

/** 단계 연습에서 차례로 낼 음([notes] 율명, [targetsHz] 높이)과 통과에 필요한 맞은 음 수. */
data class LearnPitchPlan(val notes: List<String>, val targetsHz: List<Double>, val required: Int, val rule: LearnPitchRule)

/**
 * [stage]를 음높이로 판정하는 계획. 4~6단계가 아니거나, 낼 음이 없거나, 높이를 셀 수 없는 율명이 있으면 null(스스로 확인).
 * 4단계 익힐 순서 각 음, 5단계 올라가기·내려오기 이어 불기, 6단계 새 음역마다 첫 음.
 */
internal fun learnPitchPlan(stage: Int, detail: LearnStageDetail, instrument: GuideInstrument, criteria: NotePracticeCriteria): LearnPitchPlan? {
    // 기준은 한 음씩 연습과 같고(S15P31E102-857), 6단계만 새 음역을 내야 하므로 옥타브까지 맞춘다.
    // 4단계만 유지 시간을 1초로 둔다(S15P31E102-860).
    val leniency = StepLeniency.forInstrument(instrument.criteriaCode)
    fun rule(octave: Boolean, holdMs: Int = criteria.holdMs) = LearnPitchRule(criteria.pitchToleranceCent, holdMs, criteria.minConfidence, octave,
        criteria.firstNoteTuning, criteria.maxTuningOffsetCent, leniency)
    val (notes, rule) = when (stage) {
        4 -> detail.pitchNames to rule(octave = false, holdMs = LEARN_STAGE_FOUR_HOLD_MS)
        // 5단계는 올라가기·내려오기 박스에서 한 음씩 맞을 때까지 기다리고 다섯 음을 다 내면 박스를 마친다(S15P31E102-749).
        5 -> (LearnPattern.Ascending.notes(detail.pitchNames) + LearnPattern.Descending.notes(detail.pitchNames)) to rule(octave = false)
        6 -> detail.ranges.map { it.first } to rule(octave = true)
        else -> return null
    }
    if (notes.isEmpty()) return null
    val targets = notes.map { learnTargetHz(it, instrument) ?: return null }
    return LearnPitchPlan(notes, targets, notes.size, rule)
}

/** 한 음의 판정. */
enum class LearnNoteResult { Hit, Miss }

/**
 * [plan]의 음을 차례로 기다린다. 연습 화면 한 음씩 판정기(StepPitchMatcher)와 같은 규칙이며, 화면·마이크와 떨어진 계산이라 고정 입력으로 검사한다.
 *
 * - 맞음: 목표와의 차이(첫 음 조율을 뺀 뒤)가 맞음 범위 안인 관측이 [LearnPitchRule.holdMs] 이상 끊기지 않고 이어짐. 시간은 녹음 샘플 위치로 잰다.
 *   맞음 범위는 ±[LearnPitchRule.toleranceCent]이고, 첫 음 조율 전에는 악기별 완화([StepLeniency.window])로 이웃 음 쪽까지 넓을 수 있다.
 * - 끊김: 소리 없음·높이 미확정·분석 불가·관측 누락·범위 밖 높이. 마지막으로 맞은 관측부터 [StepLeniency.gapMs] 안의 끊김은 봐준다.
 *   신뢰도(1 − YIN 정규화 차이)가 낮은 관측은 판정에서 빼고 이어짐도 끊지 않는다.
 * - 틀림([LearnPitchRule.countsMisses]일 때만): 목표 밖의 한 높이(첫 관측 ±허용 오차)가 같은 시간 이어짐.
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
    private var onEnd = 0L
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

    /** [to]번째 음부터 다시 판정한다(박스 체크 해제, S15P31E102-749). 앞 음의 맞힘은 남기고 틀림 수는 지운다. */
    fun rewind(to: Int) {
        val keep = hits.coerceAtMost(to)
        reset()
        index = to.coerceIn(0, plan.notes.size)
        hits = keep
    }

    /** [reading]까지 들었을 때 지금 음의 판정이 났으면 그 결과, 아니면 null. */
    fun accept(reading: PitchReading): LearnNoteResult? {
        if (finished) return null
        val previous = last
        last = reading
        if (previous == null || previous.recordingId != reading.recordingId || previous.segmentId != reading.segmentId) {
            clearRuns()
        } else if (previous.sequence + 1 != reading.sequence) {
            breakRun(reading)
        }
        val heard = reading.frequencyHz?.takeIf { reading.analysisAvailable && it.isFinite() && it > 0 }
        if (heard == null) {
            released = true
            breakRun(reading)
            return null
        }
        // 신뢰도가 낮은 관측은 세지 않지만 맞게 내던 음을 끊지도 않는다.
        if (1 - (reading.normalizedDifference ?: 0.0) < rule.minConfidence) return null
        if (!released) {
            if (heldHz?.let { abs(tuned(heard, it)) <= rule.toleranceCent } == true) return null
            released = true
        }
        val goal = plan.targetsHz[index]
        if (tuned(heard, goal) in window(goal)) {
            offHz = null
            if (onRun.isEmpty()) onStart = reading.endSample
            onEnd = reading.endSample
            onRun += offset(heard, goal)
            holdMs = (reading.endSample - onStart) * 1000.0 / reading.sampleRate
            if (holdMs < rule.holdMs) return null
            if (rule.firstNoteTuning && tuningCent == null) {
                val middle = onRun.sorted()[onRun.size / 2]
                tuningCent = if (abs(middle) <= rule.maxTuningOffsetCent) middle else 0.0
            }
            hits++
            return finish(goal, LearnNoteResult.Hit)
        }
        breakRun(reading)
        // 끊김 허용 안이면 맞게 내던 음을 이어 가는 중이다.
        if (onRun.isNotEmpty()) return null
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

    /** 맞지 않은 관측이 이어짐을 끊는다. 단, 마지막으로 맞은 관측부터 [StepLeniency.gapMs] 안이면 봐주고 그 시간도 유지 시간에 넣는다. */
    private fun breakRun(reading: PitchReading) {
        if (onRun.isEmpty()) return
        val gap = (reading.endSample - onEnd) * 1000.0 / reading.sampleRate
        if (rule.leniency.gapMs > 0 && gap <= rule.leniency.gapMs) return
        clearRuns()
    }

    private fun clearRuns() {
        onRun.clear()
        offHz = null
        holdMs = 0.0
    }

    /** 지금 맞음으로 보는 센트 범위. 첫 음 조율 전에는 이 계획의 다른 음을 이웃 음으로 보고 넓힐 수 있다. */
    private fun window(goal: Double): ClosedFloatingPointRange<Double> =
        rule.leniency.window(rule.toleranceCent, rule.firstNoteTuning && tuningCent == null, rule.maxTuningOffsetCent,
            plan.targetsHz.map { 1200 * log2(it / goal) })

    /** 첫 음 조율을 뺀 목표 대비 센트. */
    private fun tuned(heard: Double, goal: Double) = offset(heard, goal) - (tuningCent ?: 0.0)

    /** 목표에서 몇 센트 떨어졌는지. 옥타브를 따지지 않으면 −600 이상 600 미만으로 접는다. */
    private fun offset(heard: Double, goal: Double): Double {
        val cents = 1200 * log2(heard / goal)
        if (rule.octaveMustMatch) return cents
        return ((cents + 600) % 1200 + 1200) % 1200 - 600
    }
}

/**
 * 7단계 혀치기의 같은 음 판정(S15P31E102-845). '투'의 시작은 소리 크기로 잡고([onset], [recordLevel]),
 * 그 '투'의 높이는 음높이 관측으로 정한다. 첫 '투'의 높이를 기준으로 삼아, 이어지는 '투'가 기준과 ±허용 오차 안이면 하나 더 센다.
 * 다른 음이면 거기서부터 새로 센다(그 '투'가 새 첫 음이 되어 1). 옥타브가 다르면 다른 음이다.
 *
 * - '투'의 높이: 소리 시작 뒤 서로 허용 오차 안에서 이어진 관측이 [STEADY_READINGS]개 모이면 그 중앙값.
 *   끊김(높이 없음·관측 흐름 바뀜)이나 허용 오차 밖으로 뛴 관측에서 다시 모은다.
 * - 높이를 정하기 전에 다음 '투'가 시작되면 앞 '투'는 세지 않는다. 신뢰도가 낮은 관측은 빼고 모음도 끊지 않는다.
 */
internal class LearnTongueTracker(criteria: NotePracticeCriteria? = null) {
    private var toleranceCent = learnToleranceCent(criteria)
    private var minConfidence = criteria?.minConfidence ?: LEARN_PITCH_MIN_CONFIDENCE

    /** 첫 음부터 이어서 같은 음으로 센 '투' 횟수. */
    var count = 0
        private set

    private var firstHz: Double? = null
    private var pending = false
    private var last: PitchReading? = null
    private val run = mutableListOf<Double>()

    /** 서버 기준을 받으면 허용 오차·신뢰도를 바꾼다. 센 횟수는 그대로다. */
    fun use(criteria: NotePracticeCriteria) {
        toleranceCent = learnToleranceCent(criteria)
        minConfidence = criteria.minConfidence
    }

    fun reset() {
        count = 0
        firstHz = null
        pending = false
        last = null
        run.clear()
    }

    /** 소리가 기준 아래로 내려갔다가 다시 나 새 '투'가 시작됐다. */
    fun onset() {
        pending = true
    }

    /** [reading]까지 들었을 때 이번 '투'의 높이가 정해져 [count]가 바뀌었으면 true. */
    fun accept(reading: PitchReading): Boolean {
        val previous = last
        last = reading
        if (previous == null || previous.recordingId != reading.recordingId || previous.segmentId != reading.segmentId ||
            previous.sequence + 1 != reading.sequence
        ) run.clear()
        val heard = reading.frequencyHz?.takeIf { reading.analysisAvailable && it.isFinite() && it > 0 }
        if (heard == null) {
            run.clear()
            return false
        }
        if (1 - (reading.normalizedDifference ?: 0.0) < minConfidence) return false
        if (run.isNotEmpty() && abs(cents(heard, run.last())) > toleranceCent) run.clear()
        run += heard
        if (!pending || run.size < STEADY_READINGS) return false
        pending = false
        val hz = run.sorted()[run.size / 2]
        val first = firstHz
        if (first == null || abs(cents(hz, first)) > toleranceCent) {
            firstHz = hz
            count = 1
        } else {
            count++
        }
        return true
    }

    private fun cents(hz: Double, base: Double) = 1200 * log2(hz / base)

    private companion object {
        /** '투' 하나의 높이를 정하는 데 필요한 이어진 관측 수(20ms 간격이면 약 60ms). */
        const val STEADY_READINGS = 3
    }
}

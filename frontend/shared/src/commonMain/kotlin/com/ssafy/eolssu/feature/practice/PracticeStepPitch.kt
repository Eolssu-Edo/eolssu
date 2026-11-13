package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.StepLeniency
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/*
 * 한 음씩 따라 불기의 기기 안 판정(S15P31E102-341). 기준값은 서버의 한 음씩 연습 기준(PRA-06)을 그대로 쓴다.
 * 녹음기의 음높이 관측([AudioRecorder.pitchReadings], 연속 두 창이 반음 안일 때만 값이 있음)을 읽고, 녹음 파일은 버린다.
 */

/** 한 음씩 판정기가 마지막 관측을 어떻게 봤는지(S15P31E102-666). 디버그 빌드의 음높이 막대만 쓰고 판정에는 쓰지 않는다. */
enum class StepPitchState {
    /** 높이를 셀 수 없고 크기 정보도 없음(크기를 재지 않는 플랫폼). 이어짐이 끊긴다. */
    Silent,

    /** 소리가 작아(−45dBFS 미만) 높이를 구하지 않음. 이어짐이 끊긴다. */
    Quiet,

    /** 소리는 충분히 크지만 주기가 불명확해(김 소리·잡음) 높이를 구하지 못함. 이어짐이 끊긴다. */
    Aperiodic,

    /** 분석하지 못한 창(분석 대기열 넘침 등). 이어짐이 끊긴다. */
    Dropped,

    /** 원시 높이는 있지만 앞 창과 반음 넘게 달라 확정하지 못함. 이어짐이 끊긴다. */
    Unconfirmed,

    /** 신뢰도가 기준보다 낮아 판정에서 뺌. 이어짐은 끊지 않는다. */
    LowConfidence,

    /** 방금 맞은 음이 계속 들려 다음 음을 아직 재지 않음. 숨·혀로 한 번 끊어야 한다. */
    Releasing,

    /** 허용 범위 밖. */
    OutOfRange,

    /** 같은 율명의 다른 옥타브(옥타브 일치 기준일 때). */
    WrongOctave,

    /** 허용 범위 안에서 유지 시간을 채우는 중. */
    Holding,

    /** 유지 시간을 채워 맞음. */
    Matched,
}

/**
 * 디버그 음높이 막대에 그릴 값. [cents]는 첫 음 조율([tuningCent])을 뺀 목표 대비 센트로, 판정이 허용 범위와 비교하는 값이다.
 * 옥타브 차이는 [octaves]로 떼어 [cents]를 −600~600에 둔다. [heardHz]는 확정 높이이고, 미확정이면 원시 분석값이다.
 * [hold]는 유지 시간을 채운 비율(0~1), [confidence]는 1 − YIN 정규화 차이이고 [minConfidence]보다 낮으면 판정에서 뺀다.
 * [levelDbfs]는 분석한 창의 RMS 크기로, [minLevelDbfs]보다 작으면 높이를 구하지 않는다.
 * [lowCent]~[highCent]는 지금 맞음으로 보는 범위(첫 음을 넓게 받으면 ±허용 센트보다 넓다)이고, [kept]는 끊긴 창이지만 끊김 허용으로
 * 유지를 이어 갔는지, [lenient]는 악기별 완화([StepLeniency])가 켜졌는지다.
 */
data class StepPitchGauge(
    val state: StepPitchState,
    val heardHz: Double?,
    val targetHz: Double?,
    val cents: Double?,
    val octaves: Int,
    val confidence: Double?,
    val hold: Double,
    val tuningCent: Double?,
    val toleranceCent: Double,
    val minConfidence: Double,
    val levelDbfs: Double? = null,
    val lowCent: Double = -toleranceCent,
    val highCent: Double = toleranceCent,
    val kept: Boolean = false,
    val minLevelDbfs: Double = PitchGate.Default.minLevelDbfs,
    val lenient: Boolean = false,
)

/** 유지를 끊는(끊김 허용이 없으면 300ms를 처음부터 다시 세는) 상태. */
private val BreakingStates = setOf(StepPitchState.Silent, StepPitchState.Quiet, StepPitchState.Aperiodic, StepPitchState.Dropped,
    StepPitchState.Unconfirmed, StepPitchState.OutOfRange, StepPitchState.WrongOctave)

/**
 * 기다리는 음 하나를 맞게 불었는지 본다. 화면·마이크와 떨어진 계산이라 고정 입력으로 검사한다.
 *
 * - 맞음: (들은 높이 − 목표 높이) cent가 첫 음 조율 편차를 뺀 뒤 ±[NotePracticeCriteria.pitchToleranceCent] 안이고,
 *   그 상태가 [NotePracticeCriteria.holdMs] 이상 끊기지 않고 이어짐. 시간은 녹음 샘플 위치로 잰다.
 * - 끊김: 소리 없음·높이 미확정·분석 불가·관측 누락·범위 밖 높이. 신뢰도가 [NotePracticeCriteria.minConfidence]보다 낮은 창은
 *   판정에서 빼고 이어짐도 끊지 않는다. 신뢰도는 1 − YIN 정규화 차이로 본다.
 * - 옥타브: [NotePracticeCriteria.octaveMustMatch]가 아니면 옥타브 차이를 접어 같은 율명으로 본다.
 * - 조율: [NotePracticeCriteria.firstNoteTuning]이면 듣기를 시작한 뒤 처음 맞은 음에서 잰 편차(중앙값)를 뒤 음 목표에 더한다.
 *   편차가 [NotePracticeCriteria.maxTuningOffsetCent]를 넘으면 보정하지 않는다.
 * - 이어 부는 음: 맞은 직후에는 방금 맞은 음 높이가 계속 들리는 동안 다음 음을 재지 않는다. 같은 음이 이어지면 숨·혀로
 *   한 번 끊은(높이가 비거나 바뀐) 뒤에 새로 잰다.
 * - 악기별 완화([leniency]): 마지막으로 맞은 창부터 [StepLeniency.gapMs] 안의 끊김은 이어짐을 끊지 않는다. 첫 음 조율 전에는
 *   [StepLeniency.wideFirstNote]이면 첫 음을 곡의 이웃 음([songHz])과 헷갈리지 않는 범위에서 조율 상한까지 넓게 받는다.
 *   [StepLeniency.lowerOctave]이면 옥타브 일치 기준에서도 한 옥타브 낮게 낸 소리를 목표 옥타브로 옮겨 잰다(대금, S15P31E102-866).
 */
internal class StepPitchMatcher(
    private val criteria: NotePracticeCriteria,
    private val hwangHz: Double,
    val leniency: StepLeniency = StepLeniency.Strict,
    private val songHz: List<Double> = emptyList(),
) {
    private var target: StepTarget? = null
    private var goalHz: Double? = null
    private var heldHz: Double? = null
    private var released = true
    private var tuningCent: Double? = null
    private var last: PitchReading? = null
    private var runStart = 0L
    private var lastInRangeEnd = 0L
    private val run = mutableListOf<Double>()

    /** 마지막 관측을 어떻게 봤는지. 듣기 전·멈춘 뒤는 null이다. 판정 결과에는 영향이 없다. */
    var gauge: StepPitchGauge? = null
        private set

    /** 기다릴 음을 정한다. 이미 기다리던 음이면 재던 것을 이어 간다. */
    fun target(next: StepTarget) {
        if (target?.index == next.index && goalHz != null) return
        target = next
        goalHz = targetHz(next.note, hwangHz)
        run.clear()
    }

    /** 듣기를 멈춤. 다시 들으면 첫 음 조율부터 새로 잰다. */
    fun reset() {
        target = null
        goalHz = null
        heldHz = null
        released = true
        tuningCent = null
        last = null
        run.clear()
        gauge = null
    }

    /** [reading]까지 들었을 때 기다리던 음을 맞게 불었으면 그 음의 연주 순서, 아니면 null. 맞으면 다음 음을 정할 때까지 듣지 않는다. */
    fun accept(reading: PitchReading): Int? {
        val previous = last
        last = reading
        if (previous == null || previous.recordingId != reading.recordingId || previous.segmentId != reading.segmentId) {
            run.clear()
        } else if (previous.sequence + 1 != reading.sequence) {
            breakRun(reading)
        }
        val heard = reading.frequencyHz?.takeIf { reading.analysisAvailable && it.isFinite() && it > 0 }
        val confidence = reading.normalizedDifference?.let { 1 - it }
        if (heard == null) {
            released = true
            breakRun(reading)
            val raw = reading.rawFrequencyHz?.takeIf { reading.analysisAvailable && it.isFinite() && it > 0 }
            val level = reading.inputDbfs
            val state = when {
                !reading.analysisAvailable -> StepPitchState.Dropped
                raw != null -> StepPitchState.Unconfirmed
                level == null -> StepPitchState.Silent
                level < leniency.gate.minLevelDbfs -> StepPitchState.Quiet
                else -> StepPitchState.Aperiodic
            }
            report(reading, state, raw, confidence, holdOf(reading))
            return null
        }
        if ((confidence ?: 1.0) < criteria.minConfidence) {
            report(reading, StepPitchState.LowConfidence, heard, confidence, holdOf(reading))
            return null
        }
        if (!released) {
            if (heldHz?.let { abs(tuned(heard, it)) <= criteria.pitchToleranceCent } == true) {
                report(reading, StepPitchState.Releasing, heard, confidence)
                return null
            }
            released = true
        }
        val waiting = target ?: return null
        val goal = goalHz ?: return null
        if (tuned(heard, goal) !in window(goal)) {
            breakRun(reading)
            report(reading, StepPitchState.OutOfRange, heard, confidence, holdOf(reading))
            return null
        }
        if (run.isEmpty()) runStart = reading.endSample
        lastInRangeEnd = reading.endSample
        run += offset(heard, goal)
        if ((reading.endSample - runStart) * 1000.0 / reading.sampleRate < criteria.holdMs) {
            report(reading, StepPitchState.Holding, heard, confidence, holdOf(reading))
            return null
        }
        if (criteria.firstNoteTuning && tuningCent == null) {
            val middle = run.sorted()[run.size / 2]
            tuningCent = if (abs(middle) <= criteria.maxTuningOffsetCent) middle else 0.0
        }
        report(reading, StepPitchState.Matched, heard, confidence, 1.0)
        heldHz = goal
        released = false
        target = null
        goalHz = null
        run.clear()
        return waiting.index
    }

    /**
     * 맞지 않은 창이 이어짐을 끊는다. 단, [StepLeniency.gapMs] 동안은 마지막으로 맞은 창부터의 끊김을 봐준다.
     * 봐준 동안의 시간도 유지 시간에 들어간다. 끝내 맞은 창이 다시 오지 않으면 그다음 끊김에서 지운다.
     */
    private fun breakRun(reading: PitchReading) {
        if (run.isEmpty()) return
        val gap = (reading.endSample - lastInRangeEnd) * 1000.0 / reading.sampleRate
        if (leniency.gapMs > 0 && gap <= leniency.gapMs) return
        run.clear()
    }

    /** 첫 음 조율을 뺀 목표 대비 센트. */
    private fun tuned(heard: Double, goal: Double) = offset(heard, goal) - (tuningCent ?: 0.0)

    /** 지금 맞음으로 보는 센트 범위([StepLeniency.window]). 첫 음 조율 전에는 곡의 이웃 음([songHz])을 보고 넓힐 수 있다. */
    private fun window(goal: Double): ClosedFloatingPointRange<Double> =
        leniency.window(criteria.pitchToleranceCent, criteria.firstNoteTuning && tuningCent == null, criteria.maxTuningOffsetCent,
            songHz.map { 1200 * log2(it / goal) })

    /** 이어 온 맞은 창이 유지 시간을 얼마나 채웠는지(0~1). 이어 온 창이 없으면 0이다. */
    private fun holdOf(reading: PitchReading): Double {
        if (run.isEmpty() || criteria.holdMs <= 0) return 0.0
        return ((reading.endSample - runStart) * 1000.0 / reading.sampleRate / criteria.holdMs).coerceIn(0.0, 1.0)
    }

    /**
     * 막대 값을 남긴다. 같은 율명의 다른 옥타브는 옥타브를 떼고 남은 차이가 허용 범위 안이면 [StepPitchState.WrongOctave]로 바꾼다.
     * 기다리는 음이 없으면 높이 차이는 비워 둔다.
     */
    private fun report(reading: PitchReading, state: StepPitchState, heard: Double?, confidence: Double?, hold: Double = 0.0) {
        val goal = goalHz
        val tuned = if (heard != null && goal != null) tuned(heard, goal) else null
        val octaves = tuned?.let { (it / 1200).roundToInt() } ?: 0
        val cents = tuned?.minus(1200.0 * octaves)
        val range = goal?.let(::window) ?: -criteria.pitchToleranceCent..criteria.pitchToleranceCent
        val shown = if (state == StepPitchState.OutOfRange && octaves != 0 && cents != null && cents in range) {
            StepPitchState.WrongOctave
        } else {
            state
        }
        val kept = shown in BreakingStates && run.isNotEmpty()
        gauge = StepPitchGauge(shown, heard, goal, cents, octaves, confidence, hold, tuningCent, criteria.pitchToleranceCent,
            criteria.minConfidence, reading.inputDbfs?.takeIf { !it.isNaN() }, range.start, range.endInclusive, kept,
            leniency.gate.minLevelDbfs, leniency != StepLeniency.Strict)
    }

    /**
     * 목표에서 몇 cent 떨어졌는지. 옥타브를 따지지 않으면 −600 이상 600 미만으로 접는다.
     * 옥타브를 따질 때도 [StepLeniency.lowerOctave]이면 한 옥타브 낮은 소리는 목표 옥타브로 옮긴다.
     */
    private fun offset(heard: Double, goal: Double): Double {
        val cents = 1200 * log2(heard / goal)
        if (criteria.octaveMustMatch) return leniency.foldLowerOctave(cents)
        return ((cents + 600) % 1200 + 1200) % 1200 - 600
    }
}

/**
 * 마이크로 듣는 실제 판정기. 듣기 시작할 때 녹음기를 켜고, 멈추면 녹음을 버린다(파일을 남기거나 보내지 않는다).
 * 녹음기를 켜지 못하면 [onFailed]를 부른다. [listen]·[stop]과 판정 알림은 [scope]의 스레드에서 실행한다.
 * [onGauge]가 있으면 관측마다 막대 값([StepPitchMatcher.gauge])을, 멈추면 null을 넘긴다(디버그 빌드 전용, S15P31E102-666).
 */
class PitchStepNoteJudge internal constructor(
    private val recorder: AudioRecorder,
    private val readings: Flow<PitchReading>,
    private val matcher: StepPitchMatcher,
    private val skipSigimsae: Boolean,
    private val scope: CoroutineScope,
    private val onGauge: ((StepPitchGauge?) -> Unit)? = null,
    private val onFailed: () -> Unit,
) : StepNoteJudge {
    override val manualConfirm: Boolean = false
    private var collector: Job? = null
    private var onMatched: ((Int) -> Unit)? = null

    override fun listen(target: StepTarget, onMatched: (index: Int) -> Unit) {
        this.onMatched = onMatched
        if (skipSigimsae && target.sigimsae) {
            onMatched(target.index)
            return
        }
        matcher.target(target)
        if (collector != null) return
        recorder.pitchGate = matcher.leniency.gate
        // 녹음보다 먼저 구독해야 첫 관측을 놓치지 않는다(관측 흐름은 지난 값을 다시 주지 않는다).
        collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readings.collect { reading ->
                val matched = matcher.accept(reading)
                onGauge?.invoke(matcher.gauge)
                matched?.let { index -> this@PitchStepNoteJudge.onMatched?.invoke(index) }
            }
        }
        if (runCatching { recorder.start() }.isFailure) {
            stop()
            onFailed()
        }
    }

    override fun stop() {
        val job = collector ?: return
        collector = null
        onMatched = null
        job.cancel()
        recorder.cancel()
        recorder.pitchGate = PitchGate.Default
        matcher.reset()
        onGauge?.invoke(null)
    }
}

/**
 * [song]을 기기에서 판정할 수 있으면 그 판정기를 만든다. 음높이 관측이 없는 플랫폼(현재 iOS·Web), 황종 높이가 없는 악보,
 * 율명·옥타브를 모르는 음이 있는 악보는 null이다.
 */
internal fun pitchStepJudgeFor(
    song: Song,
    criteria: NotePracticeCriteria,
    recorder: AudioRecorder?,
    scope: CoroutineScope,
    onGauge: ((StepPitchGauge?) -> Unit)? = null,
    onFailed: () -> Unit,
): PitchStepNoteJudge? {
    val readings = recorder?.pitchReadings ?: return null
    val hwangHz = song.score.timing?.hwangHz?.takeIf { it.isFinite() && it > 0 } ?: return null
    if (!song.stepJudgeable(hwangHz)) return null
    val leniency = StepLeniency.forInstrument(song.score.source?.instrument)
    val matcher = StepPitchMatcher(criteria, hwangHz, leniency, songTargetHz(song.timeline, hwangHz))
    return PitchStepNoteJudge(recorder, readings, matcher, criteria.skipSigimsae, scope, onGauge, onFailed)
}

/** 한 음씩이 기다릴 음이 있고, 그 음들이 모두 높이를 셀 수 있는 원본 율명인지. */
internal fun Song.stepJudgeable(hwangHz: Double): Boolean {
    val targets = stepTargets(timeline)
    return targets.isNotEmpty() && targets.all { targetHz(timeline[it].note, hwangHz) != null }
}

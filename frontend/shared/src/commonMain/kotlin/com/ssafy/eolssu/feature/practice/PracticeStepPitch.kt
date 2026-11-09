package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import kotlin.math.abs
import kotlin.math.log2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/*
 * 한 음씩 따라 불기의 기기 안 판정(S15P31E102-341). 기준값은 서버의 한 음씩 연습 기준(PRA-06)을 그대로 쓴다.
 * 녹음기의 음높이 관측([AudioRecorder.pitchReadings], 연속 두 창이 반음 안일 때만 값이 있음)을 읽고, 녹음 파일은 버린다.
 */

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
 */
internal class StepPitchMatcher(private val criteria: NotePracticeCriteria, private val hwangHz: Double) {
    private var target: StepTarget? = null
    private var goalHz: Double? = null
    private var heldHz: Double? = null
    private var released = true
    private var tuningCent: Double? = null
    private var last: PitchReading? = null
    private var runStart = 0L
    private val run = mutableListOf<Double>()

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
    }

    /** [reading]까지 들었을 때 기다리던 음을 맞게 불었으면 그 음의 연주 순서, 아니면 null. 맞으면 다음 음을 정할 때까지 듣지 않는다. */
    fun accept(reading: PitchReading): Int? {
        val previous = last
        last = reading
        if (previous == null || previous.recordingId != reading.recordingId || previous.segmentId != reading.segmentId ||
            previous.sequence + 1 != reading.sequence
        ) run.clear()
        val heard = reading.frequencyHz?.takeIf { reading.analysisAvailable && it.isFinite() && it > 0 }
        if (heard == null) {
            released = true
            run.clear()
            return null
        }
        if (1 - (reading.normalizedDifference ?: 0.0) < criteria.minConfidence) return null
        if (!released) {
            if (heldHz?.let { matches(heard, it) } == true) return null
            released = true
        }
        val waiting = target ?: return null
        val goal = goalHz ?: return null
        if (!matches(heard, goal)) {
            run.clear()
            return null
        }
        if (run.isEmpty()) runStart = reading.endSample
        run += offset(heard, goal)
        if ((reading.endSample - runStart) * 1000.0 / reading.sampleRate < criteria.holdMs) return null
        if (criteria.firstNoteTuning && tuningCent == null) {
            val middle = run.sorted()[run.size / 2]
            tuningCent = if (abs(middle) <= criteria.maxTuningOffsetCent) middle else 0.0
        }
        heldHz = goal
        released = false
        target = null
        goalHz = null
        run.clear()
        return waiting.index
    }

    private fun matches(heard: Double, goal: Double) = abs(offset(heard, goal) - (tuningCent ?: 0.0)) <= criteria.pitchToleranceCent

    /** 목표에서 몇 cent 떨어졌는지. 옥타브를 따지지 않으면 −600 이상 600 미만으로 접는다. */
    private fun offset(heard: Double, goal: Double): Double {
        val cents = 1200 * log2(heard / goal)
        if (criteria.octaveMustMatch) return cents
        return ((cents + 600) % 1200 + 1200) % 1200 - 600
    }
}

/**
 * 마이크로 듣는 실제 판정기. 듣기 시작할 때 녹음기를 켜고, 멈추면 녹음을 버린다(파일을 남기거나 보내지 않는다).
 * 녹음기를 켜지 못하면 [onFailed]를 부른다. [listen]·[stop]과 판정 알림은 [scope]의 스레드에서 실행한다.
 */
class PitchStepNoteJudge internal constructor(
    private val recorder: AudioRecorder,
    private val readings: Flow<PitchReading>,
    private val matcher: StepPitchMatcher,
    private val skipSigimsae: Boolean,
    private val scope: CoroutineScope,
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
        // 녹음보다 먼저 구독해야 첫 관측을 놓치지 않는다(관측 흐름은 지난 값을 다시 주지 않는다).
        collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readings.collect { reading -> matcher.accept(reading)?.let { index -> this@PitchStepNoteJudge.onMatched?.invoke(index) } }
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
        matcher.reset()
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
    onFailed: () -> Unit,
): PitchStepNoteJudge? {
    val readings = recorder?.pitchReadings ?: return null
    val hwangHz = song.score.timing?.hwangHz?.takeIf { it.isFinite() && it > 0 } ?: return null
    if (!song.stepJudgeable(hwangHz)) return null
    return PitchStepNoteJudge(recorder, readings, StepPitchMatcher(criteria, hwangHz), criteria.skipSigimsae, scope, onFailed)
}

/** 한 음씩이 기다릴 음이 있고, 그 음들이 모두 높이를 셀 수 있는 원본 율명인지. */
internal fun Song.stepJudgeable(hwangHz: Double): Boolean {
    val targets = stepTargets(timeline)
    return targets.isNotEmpty() && targets.all { targetHz(timeline[it].note, hwangHz) != null }
}

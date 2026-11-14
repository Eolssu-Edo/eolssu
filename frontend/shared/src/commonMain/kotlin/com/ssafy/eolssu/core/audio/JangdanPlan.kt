package com.ssafy.eolssu.core.audio

import com.ssafy.eolssu.core.model.JangdanPlayback
import com.ssafy.eolssu.core.model.BasicAccompaniment
import com.ssafy.eolssu.core.model.ScoreTiming
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

data class JangdanStrike(val tick: Double, val clip: JangdanClip, val gain: Float = 1f)
data class JangdanPlan(
    val msPerTick: Double,
    val strikes: List<JangdanStrike>,
    val startTick: Double,
    val cycleTicks: Double,
    val cellTicks: Double,
) {
    constructor(mapping: JangdanPlayback, msPerTick: Double, strikes: List<JangdanStrike>) :
        this(msPerTick, strikes, mapping.startTick.toDouble(), mapping.cycleTicks.toDouble(), mapping.cellTicks)

    fun cellAt(tick: Double): Int = if (tick < startTick || !tick.isFinite()) -1 else
        (((tick - startTick) % cycleTicks) / cellTicks).toInt()
}

/** 첫 강박·묶음 강세·나머지 박을 기존 장구 샘플로 재생한다. */
fun planBasicAccompaniment(setting: BasicAccompaniment, timing: ScoreTiming, bpm: Int): JangdanPlan? {
    if (bpm <= 0) return null
    val pulse = setting.pulseTicks(timing) ?: return null
    val starts = setting.groups.runningFold(0, Int::plus).dropLast(1)
    val strikes = List(setting.groups.sum()) { index ->
        val sample = when { index == 0 -> JangdanSample.Deong; index in starts -> JangdanSample.Kung; else -> JangdanSample.Deok }
        JangdanStrike(index * pulse, JangdanClip(sample), if (index in starts) 1f else 0.65f)
    }
    return JangdanPlan(60_000.0 / bpm / timing.ticksPerBeat, strikes, setting.startTick.toDouble(), setting.groups.sum() * pulse, pulse)
}

/** 두 소박의 시간에 맞춰 실제 굴림 클립과 음높이 보존 재생 속도를 정한다. */
fun planJangdan(mapping: JangdanPlayback, bpm: Int, ticksPerBeat: Int): JangdanPlan? {
    if (bpm <= 0 || ticksPerBeat <= 0) return null
    val msPerTick = 60_000.0 / bpm / ticksPerBeat
    val cell = mapping.cellTicks
    val result = mutableListOf<JangdanStrike>()
    for ((index, syllable) in mapping.beats.withIndex()) {
        val at = index * cell
        fun add(offset: Double, sample: JangdanSample, gain: Float = 1f) {
            result += JangdanStrike((at + offset).coerceAtLeast(0.0), JangdanClip(sample), gain)
        }
        when (syllable) {
            "" -> Unit
            "떵", "덩" -> add(0.0, JangdanSample.Deong)
            "쿵", "궁" -> add(0.0, JangdanSample.Kung)
            "덕", "따" -> add(0.0, JangdanSample.Deok)
            "다" -> add(0.0, JangdanSample.Da)
            "더" -> add(0.0, JangdanSample.Deo)
            "기" -> add(0.0, JangdanSample.Gi)
            "기덕", "기닥" -> {
                add(-minOf(35.0 / msPerTick, cell / 4), JangdanSample.Gi, 0.7f)
                add(0.0, if (syllable == "기덕") JangdanSample.Deok else JangdanSample.Da)
            }
            "덕기", "덕덕" -> {
                add(0.0, JangdanSample.Deok)
                add(cell * if (syllable == "덕기") 0.75 else 0.5,
                    if (syllable == "덕기") JangdanSample.Gi else JangdanSample.Deok, 0.8f)
            }
            "더러러러" -> {
                if (mapping.jangdanId != "2") return null
                val durationMs = 2 * cell * msPerTick
                val (sourceBpm, sample) = gutgeoriRolls.minBy { abs(ln(60_000.0 / it.first / durationMs)) }
                val sourceFrames = (60.0 * JANGDAN_SAMPLE_RATE / sourceBpm).roundToInt()
                val targetFrames = (durationMs * JANGDAN_SAMPLE_RATE / 1000).roundToInt()
                if (targetFrames !in 1..4 * JANGDAN_SAMPLE_RATE) return null
                result += JangdanStrike(at, JangdanClip(sample, targetFrames.takeUnless { it == sourceFrames }))
            }
            else -> return null
        }
    }
    return result.takeIf { it.isNotEmpty() }?.let { JangdanPlan(mapping, msPerTick, it.sortedBy(JangdanStrike::tick)) }
}

private val gutgeoriRolls = listOf(59 to JangdanSample.Gutgeori59, 79 to JangdanSample.Gutgeori79,
    99 to JangdanSample.Gutgeori99, 109 to JangdanSample.Gutgeori109, 119 to JangdanSample.Gutgeori119)

/** 절대 악보 위치를 사용한다. 늦은 콜백은 과거 타격을 몰아치지 않으며 반복 경계도 원래 tick으로 계산한다. */
class JangdanCursor(
    private val plan: JangdanPlan,
    private val lateMs: Double = 30.0,
    resumeAfterTick: Double? = null,
    private val startAtTick: Double? = null,
) {
    private var previous: Double? = resumeAfterTick
    private var resumeFloor: Double? = resumeAfterTick
    var movedBackward: Boolean = false
        private set

    fun due(tick: Double): List<JangdanStrike> {
        if (!tick.isFinite()) return emptyList()
        // A resumed decoder may initially report a few milliseconds before its paused position.
        resumeFloor?.let { floor ->
            if (tick <= floor) return emptyList()
            resumeFloor = null
        }
        val last = previous
        movedBackward = last != null && tick < last - 0.000001
        val lateFloor = tick - lateMs / plan.msPerTick
        val from = when {
            last == null -> maxOf(lateFloor, startAtTick ?: Double.NEGATIVE_INFINITY) - 0.000001
            movedBackward -> lateFloor - 0.000001
            else -> maxOf(last + 0.000001, lateFloor)
        }
        previous = tick
        if (tick < plan.startTick) return emptyList()
        val cycle = plan.cycleTicks
        val firstCycle = floor((from - plan.startTick) / cycle).toLong().coerceAtLeast(0)
        val lastCycle = floor((tick - plan.startTick) / cycle).toLong().coerceAtLeast(0)
        return (firstCycle..lastCycle).flatMap { number ->
            val start = plan.startTick + number * cycle
            plan.strikes.mapNotNull { strike ->
                val absolute = start + strike.tick
                strike.copy(tick = absolute).takeIf { absolute > from && absolute <= tick + 0.000001 }
            }
        }
    }
}

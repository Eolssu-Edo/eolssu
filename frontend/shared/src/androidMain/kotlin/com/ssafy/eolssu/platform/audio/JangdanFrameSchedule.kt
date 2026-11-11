package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import com.ssafy.eolssu.core.audio.JangdanPlan
import kotlin.math.roundToLong

/** Planned frames. Only JangdanOutputSnapshot.events proves that AudioTrack accepted a strike. */
internal data class ScheduledJangdanCountIn(
    val countInStartFrame: Long,
    val recordingStartFrame: Long,
    val frameZeroNanos: Long,
    val epoch: Long,
    val preparationEvents: List<RenderedJangdanEvent>,
    val cycleStartFrames: List<Long>,
)

/** An unbounded score timeline with a finite count-in; each event is rounded from the same origin. */
internal class JangdanFrameSchedule(
    plan: JangdanPlan,
    val countInStartFrame: Long,
    private val cycles: Int,
    private val epoch: Long,
    private val generation: Long,
) {
    private val plan = plan.copy(mapping = plan.mapping.copy(
        beats = plan.mapping.beats.toList(), groups = plan.mapping.groups.toList()), strikes = plan.strikes.toList())
    private val strikes = this.plan.strikes.sortedBy { it.tick }
    private val framesPerTick = plan.msPerTick * JANGDAN_SAMPLE_RATE / 1000.0
    private val preparationCount = Math.multiplyExact(cycles, strikes.size)
    private var ordinal = 0L
    private var cancelled = false
    private var adopted = false

    init {
        require(countInStartFrame >= 0 && cycles > 0 && strikes.isNotEmpty())
        require(framesPerTick.isFinite() && framesPerTick > 0)
        require(plan.mapping.cycleTicks * framesPerTick >= 1)
        require(strikes.all { it.tick.isFinite() && it.tick >= 0 && it.tick < plan.mapping.cycleTicks && it.gain.isFinite() })
    }

    val recordingStartFrame: Long get() = frameAt(cycles * plan.mapping.cycleTicks.toDouble())
    val nextFrame: Long? get() = if (cancelled) null else event(ordinal).frame

    fun describe(frameZeroNanos: Long) = ScheduledJangdanCountIn(
        countInStartFrame, recordingStartFrame, frameZeroNanos, epoch,
        List(preparationCount) { event(it.toLong()) },
        List(cycles) { frameAt(it * plan.mapping.cycleTicks.toDouble()) },
    )

    fun adopt(plan: JangdanPlan, currentGeneration: Long): Boolean {
        if (cancelled || adopted || plan != this.plan || currentGeneration != generation) return false
        adopted = true
        return true
    }

    fun cancel() { cancelled = true }

    /** A late render is a broken output timeline, never a request to bunch past strikes together. */
    fun takeAt(firstFrame: Long): List<RenderedJangdanEvent> {
        val next = nextFrame ?: return emptyList()
        check(next >= firstFrame) { "Missed scheduled jangdan frame" }
        if (next != firstFrame) return emptyList()
        val result = mutableListOf<RenderedJangdanEvent>()
        do {
            result += event(ordinal++)
        } while (nextFrame == firstFrame)
        return result
    }

    /** Split before a future attack so its event cannot be published by an earlier short write. */
    fun blockFrames(firstFrame: Long, maximum: Int): Int {
        require(firstFrame >= 0 && maximum > 0)
        val next = nextFrame ?: return maximum
        check(next > firstFrame) { "Consume scheduled attacks before rendering" }
        return minOf(maximum.toLong(), next - firstFrame).toInt()
    }

    private fun event(index: Long): RenderedJangdanEvent {
        val cycle = index / strikes.size
        val strike = strikes[(index % strikes.size).toInt()]
        val tick = if (index < preparationCount) {
            cycle * plan.mapping.cycleTicks.toDouble() + strike.tick
        } else {
            cycles * plan.mapping.cycleTicks.toDouble() + plan.mapping.startTick +
                (cycle - cycles) * plan.mapping.cycleTicks.toDouble() + strike.tick
        }
        return RenderedJangdanEvent(strike.clip, strike.gain.coerceIn(0f, 1f) * .65f, frameAt(tick))
    }

    private fun frameAt(tick: Double): Long {
        val frames = tick * framesPerTick
        check(frames.isFinite() && frames >= 0 && frames < Long.MAX_VALUE.toDouble())
        val rounded = frames.roundToLong()
        check(rounded <= Long.MAX_VALUE - countInStartFrame)
        return countInStartFrame + rounded
    }
}

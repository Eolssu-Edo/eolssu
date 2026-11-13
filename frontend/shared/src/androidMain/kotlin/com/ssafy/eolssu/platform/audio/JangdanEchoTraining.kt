package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.EchoQuietWindow
import com.ssafy.eolssu.core.audio.JangdanClip
import com.ssafy.eolssu.core.audio.JangdanPlan
import com.ssafy.eolssu.core.audio.CycleEchoCalibrationInput
import com.ssafy.eolssu.core.audio.CycleEchoCalibrator
import com.ssafy.eolssu.core.audio.CycleEchoPlaybackReference
import com.ssafy.eolssu.core.audio.CycleEchoStrike
import com.ssafy.eolssu.core.audio.EchoCalibrationResult
import com.ssafy.eolssu.core.audio.RecordingEchoCalibrator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A preparation chooses one learned reference; it never mixes independent and cycle templates. */
internal sealed interface JangdanEchoTraining {
    val trainingEnd: Long

    data class Isolated(val preparation: JangdanEchoPreparation) : JangdanEchoTraining {
        override val trainingEnd: Long get() = preparation.trainingEnd
    }

    data class Cycle(
        val cycleStartFrames: List<Long>,
        val periodFrames: Double,
        val phaseStartFrame: Int,
        val phaseEndFrame: Int,
        val quietWindows: List<EchoQuietWindow>,
        val playbackOriginFrame: Double,
        override val trainingEnd: Long,
    ) : JangdanEchoTraining
}

internal fun calibrateJangdanEchoTraining(
    capture: FloatArray,
    training: JangdanEchoTraining,
    plan: JangdanPlan,
    clipFrames: Map<JangdanClip, Int>,
): EchoCalibrationResult = when (training) {
    is JangdanEchoTraining.Isolated -> training.preparation.let {
        RecordingEchoCalibrator.calibrate(capture, it.strikes, clipFrames, it.quiet)
    }
    is JangdanEchoTraining.Cycle -> {
        val framesPerTick = plan.msPerTick * 44_100 / 1000.0
        CycleEchoCalibrator.calibrate(capture, CycleEchoCalibrationInput(
            training.cycleStartFrames, training.periodFrames, training.phaseStartFrame,
            training.phaseEndFrame, training.quietWindows, CycleEchoPlaybackReference(
                training.playbackOriginFrame, training.periodFrames,
                plan.strikes.sortedBy { it.tick }.map {
                    CycleEchoStrike(it.tick * framesPerTick, it.clip, it.gain * .65f,
                        clipFrames.getValue(it.clip) + (44_100 * .12).roundToInt())
                },
            ),
        ))
    }
}

internal fun selectJangdanEchoTraining(
    plan: JangdanPlan,
    scheduled: ScheduledJangdanCountIn,
    clipFrames: Map<JangdanClip, Int>,
    inputShift: Long,
    initialQuietEnd: Long,
): JangdanEchoTraining? {
    val recordingStart = try { Math.addExact(scheduled.recordingStartFrame, inputShift) }
        catch (_: ArithmeticException) { return null }
    jangdanEchoPreparation(scheduled.preparationEvents, clipFrames, inputShift, initialQuietEnd,
        recordingStart)?.let { return JangdanEchoTraining.Isolated(it) }

    val rate = 44_100
    val pre = (rate * .045).roundToInt()
    val pad = rate / 200
    val tail = (rate * .12).roundToInt()
    val fitReadyFrames = rate / 100
    val framesPerTick = plan.msPerTick * rate / 1000.0
    val period = plan.mapping.cycleTicks * framesPerTick
    if (!period.isFinite() || period <= 0 || recordingStart !in 1..rate * 55L ||
        scheduled.cycleStartFrames.size != 3 || plan.strikes.isEmpty() ||
        plan.strikes.map { it.clip }.toSet() != clipFrames.keys ||
        clipFrames.values.any { it !in 1..rate * 4 }) return null
    val starts = try { scheduled.cycleStartFrames.map { Math.addExact(it, inputShift) } }
        catch (_: ArithmeticException) { return null }
    if (starts.first() < 0 || starts.last() >= recordingStart) return null
    val origin = scheduled.countInStartFrame
    if (origin < 0 || origin > recordingStart || (0..3).any { cycle ->
            val expected = origin + (cycle * period).roundToLong()
            expected != if (cycle == 3) scheduled.recordingStartFrame else scheduled.cycleStartFrames[cycle]
        }) return null

    // Validate the reference actually scheduled for all three observations, not just its duration.
    val strikes = plan.strikes.sortedBy { it.tick }
    if (strikes.any { !it.tick.isFinite() || it.tick < 0 || it.tick >= plan.mapping.cycleTicks ||
            !it.gain.isFinite() || it.gain <= 0 || it.gain > 1 }) return null
    val expected = (0..2).flatMap { cycle -> strikes.map { strike ->
        RenderedJangdanEvent(strike.clip, strike.gain * .65f,
            origin + ((cycle * plan.mapping.cycleTicks + strike.tick) * framesPerTick).roundToLong())
    } }
    if (scheduled.preparationEvents != expected) return null

    // Cycle one has no preceding cycle. Exclude every phase its missing response could affect,
    // then retain the existing 45 ms preparation/alignment margin before learning a template.
    val previousTail = strikes.maxOf {
        it.tick * framesPerTick + clipFrames.getValue(it.clip) + tail - period
    }.coerceAtLeast(0.0)
    val phaseStart = ceil(previousTail).toInt() + pre + pad
    // The probe stops at recordingStart. Leave both alignment/filter margin and the recorder's
    // 10 ms fit-ready condition inside preparation; fitting never waits for performance PCM.
    val phaseEnd = floor(period).toInt() - pre - pad - fitReadyFrames
    if (phaseEnd - phaseStart < rate / 4) return null
    val trainingEnd = starts.last() + phaseEnd + pad
    if (trainingEnd + fitReadyFrames >= recordingStart) return null
    val quietStart = rate / 10L
    val quietEnd = minOf(initialQuietEnd, expected.first().frame + inputShift - pre - pad)
    if (quietEnd - quietStart < rate * .3) return null
    val playbackOrigin = origin.toDouble() + inputShift + 3 * period +
        (plan.mapping.startTick - plan.recordingStartTick) * framesPerTick
    if (!playbackOrigin.isFinite()) return null
    return JangdanEchoTraining.Cycle(starts, period, phaseStart, phaseEnd,
        listOf(EchoQuietWindow(quietStart, quietEnd)), playbackOrigin, trainingEnd)
}

package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.EchoCalibrationStrike
import com.ssafy.eolssu.core.audio.EchoQuietWindow
import com.ssafy.eolssu.core.audio.JangdanClip
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Only isolated responses may train the existing per-clip model. All positions are input frames. */
internal data class JangdanEchoPreparation(
    val strikes: List<EchoCalibrationStrike>,
    val quiet: List<EchoQuietWindow>,
    val trainingEnd: Long,
)

internal fun jangdanEchoPreparation(
    events: List<RenderedJangdanEvent>,
    clipFrames: Map<JangdanClip, Int>,
    shift: Long,
    initialQuietEnd: Long,
    recordingStartFrame: Long,
): JangdanEchoPreparation? {
    val rate = 44_100
    val pre = (rate * .045).roundToInt() + rate / 200
    val tail = (rate * .12).roundToInt() + rate / 200
    if (events.isEmpty() || clipFrames.isEmpty() || clipFrames.values.any { it !in 1..rate * 4 }) return null
    if (events.any { it.clip !in clipFrames || it.gain !in .01f..1f || !it.gain.isFinite() }) return null
    val strikes = events.sortedBy { it.frame }.map { EchoCalibrationStrike(it.clip, it.frame + shift, it.gain) }
    val bounds = strikes.map { (it.startFrame - pre)..<(it.startFrame + clipFrames.getValue(it.clip) + tail) }
    val isolated = strikes.filterIndexed { index, _ ->
        val window = bounds[index]
        window.first >= 0 && window.last < recordingStartFrame - pre &&
            bounds.indices.none { other -> other != index && bounds[other].first <= window.last && window.first <= bounds[other].last }
    }
    if (clipFrames.keys.any { clip -> isolated.count { it.clip == clip } < 3 }) return null
    val quiet = mutableListOf<EchoQuietWindow>()
    val firstEnd = minOf(initialQuietEnd, bounds.first().first)
    if (firstEnd > rate / 10) quiet += EchoQuietWindow(rate / 10L, firstEnd)
    // 3 overlapping 93 ms windows are needed by the existing tonal-input guard.
    val minimumQuiet = 8_192L
    for (index in 0 until strikes.lastIndex) {
        val hit = strikes[index]
        val start = hit.startFrame + clipFrames.getValue(hit.clip) + rate / 4
        val end = bounds[index + 1].first
        if (end - start >= minimumQuiet && bounds.none { it.first < end && it.last >= start }) {
            quiet += EchoQuietWindow(start, end)
        }
    }
    if (quiet.sumOf { it.endFrame - it.startFrame } < rate * .3) return null
    val trainingEnd = maxOf(
        isolated.maxOf { it.startFrame + clipFrames.getValue(it.clip) + tail },
        quiet.maxOf { it.endFrame },
    )
    return JangdanEchoPreparation(isolated, quiet, trainingEnd)
}

/** Prefix before the scheduled boundary is never passed to the encoder or pitch analysis. */
internal fun preparationPrefixFrames(blockStart: Long, blockCount: Int, recordingStart: Long): Int {
    require(blockStart >= 0 && blockCount >= 0 && recordingStart >= 0)
    return (recordingStart - blockStart).coerceIn(0, blockCount.toLong()).toInt()
}

internal fun inputFrameAtNanos(nanos: Long, inputZeroNanos: Long): Long =
    ((nanos - inputZeroNanos) * (44_100.0 / 1_000_000_000.0)).roundToLong()

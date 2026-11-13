package com.ssafy.eolssu.core.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class CycleEchoStrike(
    val offsetFrames: Double,
    val clip: JangdanClip,
    val gain: Float,
    val responseFrames: Int,
)

internal data class CycleEchoPlaybackReference(
    val cycleOriginFrame: Double,
    val periodFrames: Double,
    val strikes: List<CycleEchoStrike>,
)

internal data class CycleEchoCalibrationInput(
    val cycleStartFrames: List<Long>,
    val periodFrames: Double,
    val phaseStartFrame: Int,
    val phaseEndFrame: Int,
    val quietWindows: List<EchoQuietWindow>,
    val playback: CycleEchoPlaybackReference,
)

internal object CycleEchoCalibrator {
    /** Only the supplied preparation prefix is filtered. Performance samples never enter the model. */
    fun calibrate(capture: FloatArray, input: CycleEchoCalibrationInput, sampleRate: Int = 44100): EchoCalibrationResult {
        fun reject(gate: EchoCalibrationGate, value: Double? = null, limit: Double? = null) =
            EchoCalibrationResult.Rejected(RecordingEchoStatus.Unreliable,
                EchoCalibrationDiagnostic(gate, value = value, limit = limit))
        if (sampleRate != 44100) return reject(EchoCalibrationGate.UnsupportedSampleRate)
        if (input.cycleStartFrames.size != 3) return reject(EchoCalibrationGate.InsufficientRepeats,
            input.cycleStartFrames.size.toDouble(), 3.0)
        val period = input.periodFrames
        val pad = sampleRate / 200
        val begin = input.phaseStartFrame
        val end = input.phaseEndFrame
        val size = end - begin
        if (!period.isFinite() || period !in 1.0..sampleRate * 55.0 || begin < pad ||
            end + pad > floor(period) || size < sampleRate / 40 || input.cycleStartFrames.first() < 0 ||
            input.cycleStartFrames.withIndex().any { (index, frame) ->
                frame != (input.cycleStartFrames.first() + index * period).roundToLong()
            }) return reject(EchoCalibrationGate.InvalidStrikeWindow)
        val playback = input.playback
        if (!playback.cycleOriginFrame.isFinite() || abs(playback.cycleOriginFrame) > 1e12 ||
            playback.periodFrames != period || playback.strikes.isEmpty()) return reject(EchoCalibrationGate.MissingStrikes)
        if (playback.strikes.any { !it.offsetFrames.isFinite() || it.offsetFrames !in 0.0..<period ||
                !it.gain.isFinite() || it.gain !in .01f..1f || it.responseFrames !in 1..sampleRate * 4 }) {
            return reject(EchoCalibrationGate.InvalidClipFrames)
        }
        if (input.quietWindows.isEmpty()) return reject(EchoCalibrationGate.MissingQuietWindows)
        if (input.quietWindows.any { it.startFrame < 0 || it.endFrame <= it.startFrame ||
                it.endFrame > input.cycleStartFrames.first() - pad }) return reject(EchoCalibrationGate.InvalidQuietWindow)
        val quiet = input.quietWindows.sortedBy { it.startFrame }
        if (quiet.zipWithNext().any { (a, b) -> a.endFrame > b.startFrame }) return reject(EchoCalibrationGate.InvalidQuietWindow)
        if (quiet.sumOf { it.endFrame - it.startFrame } < sampleRate * .3) {
            return reject(EchoCalibrationGate.InsufficientQuietFrames)
        }
        val lastRequired = input.cycleStartFrames.last() + end + pad
        if (lastRequired > capture.size || lastRequired > Int.MAX_VALUE || lastRequired <= 0) {
            return reject(EchoCalibrationGate.AlignedWindowBounds)
        }
        val trainingEnd = lastRequired.toInt()
        var rails = 0
        for (index in 0 until trainingEnd) {
            if (!capture[index].isFinite() || abs(capture[index]) > 1) return reject(EchoCalibrationGate.InvalidSample)
            if (abs(capture[index]) >= .999f) rails++
        }
        if (rails > max(3, trainingEnd / 1000)) return reject(EchoCalibrationGate.Clipping)
        quiet.forEach { window ->
            RecordingEchoCalibrator.quietDiagnostic(capture, window)?.let {
                return EchoCalibrationResult.Rejected(RecordingEchoStatus.NotQuiet, it)
            }
        }
        val fit = bandLimited(capture, trainingEnd, 900.0, 5500.0, sampleRate)
        val wide = bandLimited(capture, trainingEnd, 90.0, 10000.0, sampleRate)
        val low = bandLimited(capture, trainingEnd, 90.0, 700.0, sampleRate)
        val fitNoise = RecordingEchoCalibrator.quietPower(fit, quiet)
        val wideNoise = RecordingEchoCalibrator.quietPower(wide, quiet)
        val lowNoise = RecordingEchoCalibrator.quietPower(low, quiet)
        val starts = input.cycleStartFrames.map { it.toInt() + begin }
        // A short, energetic anchor gives one clock shift per repeat, not independent shifts
        // that could manufacture a match for unrelated portions of the recording.
        val windowSize = min(size, (sampleRate * .26).roundToInt())
        val windows = mutableListOf<Int>()
        var offset = 0
        while (offset + windowSize < size) {
            windows += offset
            offset += max(1, windowSize / 2)
        }
        if (windows.lastOrNull() != size - windowSize) windows += size - windowSize
        val anchor = windows.maxBy { phase -> median(starts.map {
            (energy(fit, it + phase, it + phase + windowSize) / windowSize).toFloat()
        }) }
        val matches = starts.map { reference -> starts.map { target ->
            RecordingEchoCalibrator.bestMatch(fit, reference + anchor, target + anchor, windowSize, pad)
        } }
        val medoid = matches.indices.maxBy { row ->
            median(matches[row].filterIndexed { index, _ -> index != row }.map { it.score })
        }
        val row = matches[medoid]
        if (row.any { it.score < .5f }) return reject(EchoCalibrationGate.RepeatMatchCount,
            row.count { it.score >= .5f }.toDouble(), 3.0)
        var similarity = median(row.filterIndexed { index, _ -> index != medoid }.map { it.score })
        if (similarity < .55f) return reject(EchoCalibrationGate.RepeatSimilarity, similarity.toDouble(), .55)
        val centerLag = median(row.map { it.lag.toFloat() }).roundToInt()
        val aligned = starts.mapIndexed { index, start -> start + row[index].lag - centerLag }
        if (aligned.any { it < 0 || it + size > trainingEnd }) return reject(EchoCalibrationGate.AlignedWindowBounds)
        val template = FloatArray(size) { phase -> medianOfThree(
            wide[aligned[0] + phase], wide[aligned[1] + phase], wide[aligned[2] + phase],
        ) }
        val mask = BooleanArray(size) { true }
        var maximumResidual = 0f
        for (phase in windows) {
            val offsets = aligned.map { it + phase }
            val wideResidual = maximumLocalResidual(wide, offsets, windowSize, wideNoise)
            val lowResidual = maximumLocalResidual(low, offsets, windowSize, lowNoise)
            val residual = max(wideResidual, lowResidual)
            if (residual > .15f) return reject(
                if (lowResidual > wideResidual) EchoCalibrationGate.ResponseLowResidual else EchoCalibrationGate.ResponseWideResidual,
                residual.toDouble(), .15,
            )
            maximumResidual = max(maximumResidual, residual)
            val power = median(offsets.map { (energy(fit, it, it + windowSize) / windowSize).toFloat() })
            if (power < max(1e-6f, fitNoise * 4)) {
                // Noise-dominated phases cannot establish a repeated response. Require
                // 6 dB over the measured quiet power or leave this phase untouched.
                mask.fill(false, phase, phase + windowSize)
                continue
            }
            val scores = (0..2).flatMap { a -> (a + 1..2).map { b ->
                correlation(fit, offsets[a], fit, offsets[b], windowSize, 1)
            } }
            if (scores.any { it < .5f }) return reject(EchoCalibrationGate.RepeatMatchCount,
                scores.count { it >= .5f }.toDouble(), 3.0)
            val localSimilarity = median(scores)
            if (localSimilarity < .55f) return reject(EchoCalibrationGate.RepeatSimilarity, localSimilarity.toDouble(), .55)
            similarity = min(similarity, localSimilarity)
        }
        if (mask.count { it } < sampleRate / 100 + sampleRate / 250 || energy(template) / size < 1e-6) {
            return reject(EchoCalibrationGate.TemplatePower)
        }
        val fadeIn = min(size, sampleRate / 50)
        val fadeOut = min(size, sampleRate * 3 / 100)
        for (i in 0 until fadeIn) template[i] *= i.toFloat() / fadeIn
        for (i in 0 until fadeOut) template[size - 1 - i] *= i.toFloat() / fadeOut
        val response = CycleEchoResponse(begin, template, mask,
            playback.copy(strikes = playback.strikes.toList()), sampleRate)
        return EchoCalibrationResult.Accepted(RecordingEchoModel(sampleRate, response, similarity, maximumResidual))
    }

    /** Every repeat must be consistent in each short window; a long loud cycle cannot hide a short wind note. */
    private fun maximumLocalResidual(signal: FloatArray, offsets: List<Int>, frames: Int, noisePower: Float): Float {
        val residuals = DoubleArray(3)
        var modelEnergy = 0.0
        for (index in 0 until frames) {
            val center = medianOfThree(signal[offsets[0] + index], signal[offsets[1] + index], signal[offsets[2] + index])
            modelEnergy += center.toDouble() * center
            for (repeat in offsets.indices) {
                val difference = signal[offsets[repeat] + index] - center
                residuals[repeat] += difference.toDouble() * difference
            }
        }
        val allowance = 2.0 * noisePower * frames
        return residuals.maxOf { (max(0.0, it - allowance) / max(modelEnergy, 1e-12)).toFloat() }
    }

    private fun medianOfThree(a: Float, b: Float, c: Float): Float = max(min(a, b), min(max(a, b), c))
}

/** An immutable learned phase range and its exact output schedule. No live input changes this model. */
internal class CycleEchoResponse(
    private val phaseStart: Int,
    private val template: FloatArray,
    private val trained: BooleanArray,
    private val playback: CycleEchoPlaybackReference,
    private val sampleRate: Int,
) {
    fun predict(firstFrame: Long, count: Int, events: List<EchoPlaybackEvent>, into: FloatArray, clockTolerance: Int): Boolean {
        if (firstFrame < 0 || count <= 0 || firstFrame > 1_000_000_000_000L - count) return false
        val period = playback.periodFrames
        var cycle = floor((firstFrame - playback.cycleOriginFrame) / period).toLong()
        var anchor = (playback.cycleOriginFrame + cycle * period).roundToLong()
        if (firstFrame < anchor) { cycle--; anchor = (playback.cycleOriginFrame + cycle * period).roundToLong() }
        val phase = firstFrame - anchor
        val start = phase - phaseStart
        if (start < 0 || start + count > template.size) return false
        val from = start.toInt()
        if ((from until from + count).any { !trained[it] }) return false
        if (!outputMatches(firstFrame, firstFrame + count, events, clockTolerance)) return false
        template.copyInto(into, 0, from, from + count)
        return true
    }

    private fun outputMatches(first: Long, end: Long, actual: List<EchoPlaybackEvent>, tolerance: Int): Boolean {
        val pre = (sampleRate * .045).roundToInt()
        val maximumResponse = playback.strikes.maxOf { it.responseFrames }
        val firstCycle = floor((first - maximumResponse - tolerance - playback.cycleOriginFrame) / playback.periodFrames).toLong() - 1
        val lastCycle = floor((end + pre + tolerance - playback.cycleOriginFrame) / playback.periodFrames).toLong() + 1
        if (lastCycle - firstCycle > 10_000) return false
        val expected = mutableListOf<EchoPlaybackEvent>()
        for (cycle in firstCycle..lastCycle) for (strike in playback.strikes) {
            val frame = (playback.cycleOriginFrame + cycle * playback.periodFrames + strike.offsetFrames).roundToLong()
            if (frame + strike.responseFrames > first - tolerance && frame - pre < end + tolerance) {
                if (frame < 0) return false
                expected += EchoPlaybackEvent(strike.clip, frame, strike.gain)
            }
        }
        if (expected.isEmpty()) return false
        val used = BooleanArray(actual.size)
        val deltas = mutableListOf<Long>()
        for (wanted in expected) {
            val index = actual.indices.firstOrNull { i -> !used[i] && actual[i].clip == wanted.clip &&
                actual[i].gain == wanted.gain && abs(actual[i].startFrame - wanted.startFrame) <= tolerance } ?: return false
            used[index] = true
            deltas += actual[index].startFrame - wanted.startFrame
        }
        if (deltas.maxOrNull()!! - deltas.minOrNull()!! > 1) return false
        for (index in actual.indices) {
            val event = actual[index]
            if (!event.gain.isFinite() || event.gain !in .01f..1f || event.startFrame < 0 || event.startFrame > 1_000_000_000_000L) return false
            val response = playback.strikes.filter { it.clip == event.clip }.maxOfOrNull { it.responseFrames } ?: maximumResponse
            if (!used[index] && event.startFrame + response > first + tolerance && event.startFrame - pre < end - tolerance) return false
        }
        return true
    }
}

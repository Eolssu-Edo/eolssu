package com.ssafy.eolssu.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** All positions refer to the same physical AudioRecord stream, including preparation. */
data class EchoCalibrationStrike(val clip: JangdanClip, val startFrame: Long, val gain: Float)
data class EchoQuietWindow(val startFrame: Long, val endFrame: Long)
data class EchoPlaybackEvent(val clip: JangdanClip, val startFrame: Long, val gain: Float)

sealed interface EchoCalibrationResult {
    data class Accepted(val model: RecordingEchoModel) : EchoCalibrationResult
    data class Rejected(val reason: RecordingEchoStatus) : EchoCalibrationResult
}

class RecordingEchoModel internal constructor(
    val sampleRate: Int,
    internal val preFrames: Int,
    internal val templates: Map<JangdanClip, FloatArray>,
    /** Lowest accepted repeat match; useful for local replay diagnostics. */
    val minimumSimilarity: Float,
    internal val maximumResponseResidual: Float,
)

object RecordingEchoCalibrator {
    /**
     * Learn only the supplied calibration strikes. Quiet windows must not overlap their
     * response tails. No sample after the last required window enters the learned model.
     * Gains must include the player's fixed output gain, as must playback event gains.
     */
    fun calibrate(
        capture: FloatArray,
        strikes: List<EchoCalibrationStrike>,
        clipFrames: Map<JangdanClip, Int>,
        quietWindows: List<EchoQuietWindow>,
        sampleRate: Int = 44100,
    ): EchoCalibrationResult {
        val unreliable = EchoCalibrationResult.Rejected(RecordingEchoStatus.Unreliable)
        if (sampleRate != 44100 || strikes.isEmpty() || quietWindows.isEmpty()) return unreliable
        val pre = (sampleRate * .045).roundToInt()
        val tail = (sampleRate * .12).roundToInt()
        val pad = sampleRate / 200
        if (clipFrames.isEmpty() || clipFrames.values.any { it !in 1..sampleRate * 4 } ||
            strikes.any { it.clip !in clipFrames || !it.gain.isFinite() || it.gain !in .01f..1f }) {
            return unreliable
        }
        val groups = strikes.groupBy { it.clip }
        if (groups.keys != clipFrames.keys || groups.values.any { it.size < 3 }) return unreliable
        if (quietWindows.any { it.startFrame < 0 || it.endFrame <= it.startFrame || it.endFrame > capture.size } ||
            quietWindows.sumOf { it.endFrame - it.startFrame } < sampleRate * .3) return unreliable
        for (strike in strikes) {
            val end = strike.startFrame + clipFrames.getValue(strike.clip) + tail + pad
            if (strike.startFrame < pre + pad || end > capture.size || end < strike.startFrame) return unreliable
            if (quietWindows.any { it.startFrame < end && it.endFrame > strike.startFrame - pre - pad }) {
                return unreliable
            }
        }
        val trainingEnd = max(
            strikes.maxOf { it.startFrame + clipFrames.getValue(it.clip) + tail + pad },
            quietWindows.maxOf { it.endFrame },
        ).toInt()
        var rails = 0
        for (i in 0 until trainingEnd) {
            if (!capture[i].isFinite() || abs(capture[i]) > 1f) return unreliable
            if (abs(capture[i]) >= .999f) rails++
        }
        if (rails > max(3, trainingEnd / 1000)) return unreliable
        for (window in quietWindows) {
            if (!isQuiet(capture, window)) return EchoCalibrationResult.Rejected(RecordingEchoStatus.NotQuiet)
        }

        val fit = bandLimited(capture, trainingEnd, 900.0, 5500.0, sampleRate)
        val clean = bandLimited(capture, trainingEnd, 90.0, 10000.0, sampleRate)
        val bass = bandLimited(capture, trainingEnd, 90.0, 700.0, sampleRate)
        val cleanNoise = quietPower(clean, quietWindows)
        val bassNoise = quietPower(bass, quietWindows)
        val templates = mutableMapOf<JangdanClip, FloatArray>()
        var minimumSimilarity = 1f
        var maximumResponseResidual = 0f
        for ((clip, group) in groups) {
            val frames = clipFrames.getValue(clip)
            val begin = min(sampleRate / 50, frames / 8)
            val length = min(frames - begin, (sampleRate * .4).roundToInt())
            if (length < sampleRate / 40) return unreliable
            val matches = group.map { reference ->
                group.map { target ->
                    bestMatch(fit, (reference.startFrame + begin).toInt(),
                        (target.startFrame + begin).toInt(), length, pad)
                }
            }
            val medoid = matches.indices.maxBy { row ->
                median(matches[row].filterIndexed { index, _ -> index != row }.map { it.score })
            }
            val accepted = matches[medoid].indices.filter { matches[medoid][it].score >= .5f }
            if (accepted.size < 3) return unreliable
            val similarity = median(accepted.filter { it != medoid }.map { matches[medoid][it].score })
            if (similarity < .55f) return unreliable
            minimumSimilarity = min(minimumSimilarity, similarity)
            val centerLag = median(accepted.map { matches[medoid][it].lag.toFloat() }).roundToInt()
            val offsets = accepted.map { index ->
                group[index].startFrame.toInt() - pre + matches[medoid][index].lag - centerLag
            }
            val size = pre + frames + tail
            if (offsets.any { it < 0 || it + size > trainingEnd }) return unreliable
            // Matching an attack in 900–5500 Hz cannot validate the lower frequencies
            // that will also be subtracted. Check every clip's entire response in both
            // bands, including short strikes and the tail. Ambient noise measured during
            // silence is allowed; inconsistent musical energy above it is not learned.
            val responseOffsets = offsets.map { it + pre }
            val gains = accepted.map { group[it].gain }
            val responseResidual = max(
                responseResidual(clean, responseOffsets, gains, frames + tail, cleanNoise),
                responseResidual(bass, responseOffsets, gains, frames + tail, bassNoise),
            )
            if (responseResidual > .15f) return unreliable
            maximumResponseResidual = max(maximumResponseResidual, responseResidual)
            val values = FloatArray(accepted.size)
            val template = FloatArray(size) { i ->
                for (j in accepted.indices) values[j] = clean[offsets[j] + i] / group[accepted[j]].gain
                values.sort()
                val middle = values.size / 2
                if (values.size % 2 == 0) (values[middle - 1] + values[middle]) * .5f else values[middle]
            }
            if (energy(template, pre, pre + frames) / frames < 1e-6) return unreliable
            val fadeIn = sampleRate / 50
            val fadeOut = sampleRate * 3 / 100
            for (i in 0 until fadeIn) template[i] *= i.toFloat() / fadeIn
            for (i in 0 until fadeOut) template[size - 1 - i] *= i.toFloat() / fadeOut
            templates[clip] = template
        }
        return EchoCalibrationResult.Accepted(RecordingEchoModel(
            sampleRate, pre, templates, minimumSimilarity, maximumResponseResidual,
        ))
    }

    private fun quietPower(signal: FloatArray, windows: List<EchoQuietWindow>): Float = median(windows.map {
        (energy(signal, it.startFrame.toInt(), it.endFrame.toInt()) / (it.endFrame - it.startFrame)).toFloat()
    })

    private fun responseResidual(
        signal: FloatArray,
        offsets: List<Int>,
        gains: List<Float>,
        frames: Int,
        noisePower: Float,
    ): Float {
        val values = FloatArray(offsets.size)
        val residuals = DoubleArray(offsets.size)
        var modelEnergy = 0.0
        for (i in 0 until frames) {
            for (j in offsets.indices) values[j] = signal[offsets[j] + i] / gains[j]
            values.sort()
            val middle = values.size / 2
            val center = if (values.size % 2 == 0) (values[middle - 1] + values[middle]) * .5f else values[middle]
            modelEnergy += center.toDouble() * center
            for (j in offsets.indices) {
                val difference = signal[offsets[j] + i] / gains[j] - center
                residuals[j] += difference.toDouble() * difference
            }
        }
        // The 2x noise allowance accounts for noise in a repeat and in its median model.
        // This is a repeatability check, not proof against an external sound repeated in
        // exactly the same phase and gain as playback.
        return median(offsets.indices.map { j ->
            val noiseAllowance = 2.0 * noisePower * frames / (gains[j] * gains[j])
            (max(0.0, residuals[j] - noiseAllowance) / max(modelEnergy, 1e-12)).toFloat()
        })
    }

    private fun isQuiet(capture: FloatArray, window: EchoQuietWindow): Boolean {
        // A 93 ms decimated window still contains several periods of a low wind note.
        val size = 1024
        var tonalRun = 0
        var loudRun = 0
        val block = FloatArray(size)
        var first = window.startFrame.toInt()
        while (first + size * 4 <= window.endFrame) {
            var mean = 0f
            for (i in block.indices) {
                block[i] = capture[first + i * 4]
                mean += block[i]
            }
            mean /= size
            for (i in block.indices) block[i] -= mean
            val rms = sqrt(energy(block) / size)
            loudRun = if (rms > .018) loudRun + 1 else 0
            var tonal = false
            if (rms > .006) {
                for (lag in 4..183) {
                    if (correlation(block, 0, block, lag, size - lag, 1) > .85f) {
                        tonal = true
                        break
                    }
                }
            }
            tonalRun = if (tonal) tonalRun + 1 else 0
            if (tonalRun >= 3 || loudRun >= 3) return false
            first += size * 2
        }
        return true
    }

    private data class Match(val lag: Int, val score: Float)

    private fun bestMatch(signal: FloatArray, reference: Int, target: Int, size: Int, pad: Int): Match {
        var best = Match(0, -1f)
        for (lag in -pad..pad step 4) {
            val score = correlation(signal, reference, signal, target + lag, size, 4)
            if (score > best.score) best = Match(lag, score)
        }
        val coarse = best.lag
        for (lag in max(-pad, coarse - 3)..min(pad, coarse + 3)) {
            val score = correlation(signal, reference, signal, target + lag, size, 1)
            if (score > best.score) best = Match(lag, score)
        }
        return best
    }
}

/** Conservative, causal block processing. An uncertain block is byte-for-byte unchanged. */
class RecordingEchoFilter(private val model: RecordingEchoModel) {
    private val lagFrames = model.sampleRate / 500 // +/- 2 ms clock/response tolerance
    private var predicted = FloatArray(0)
    private var input = FloatArray(0)

    /** Returns true only when this block was changed. Never buffers or changes sample count. */
    fun process(pcm: ShortArray, count: Int, firstFrame: Long, events: List<EchoPlaybackEvent>): Boolean {
        require(count in 0..pcm.size)
        if (count < model.sampleRate / 100 || firstFrame < 0 || events.isEmpty()) return false
        if (input.size < count) input = FloatArray(count)
        val predictedSize = count + lagFrames * 2
        if (predicted.size < predictedSize) predicted = FloatArray(predictedSize)
        predicted.fill(0f, 0, predictedSize)
        for (i in 0 until count) input[i] = pcm[i] / 32768f
        val rawEnergy = energy(input, 0, count)
        if (rawEnergy / count < 1e-6) return false
        val predictedStart = firstFrame - lagFrames
        for (event in events) {
            if (!event.gain.isFinite() || event.gain !in .01f..1f || event.startFrame < 0 ||
                event.startFrame > Long.MAX_VALUE - model.sampleRate * 5) return false
            val template = model.templates[event.clip] ?: return false
            val start = event.startFrame - model.preFrames
            val begin = max(0L, start - predictedStart).coerceAtMost(predictedSize.toLong()).toInt()
            val end = min(predictedSize.toLong(), start + template.size - predictedStart).coerceAtLeast(0).toInt()
            for (i in begin until end) predicted[i] += template[(predictedStart + i - start).toInt()] * event.gain
        }
        var bestLag = lagFrames
        var bestScore = 0f
        for (offset in 0..lagFrames * 2 step 4) {
            val score = correlation(input, 0, predicted, offset, count, 2)
            if (score > bestScore) { bestScore = score; bestLag = offset }
        }
        val coarse = bestLag
        bestScore = 0f
        for (offset in max(0, coarse - 3)..min(lagFrames * 2, coarse + 3)) {
            val score = correlation(input, 0, predicted, offset, count, 1)
            if (score > bestScore) { bestScore = score; bestLag = offset }
        }
        // Near-end dominant or unrelated input is preserved; there is no adaptive training.
        if (bestScore < .65f) return false
        val predictedEnergy = energy(predicted, bestLag, bestLag + count)
        if (predictedEnergy / count < 1e-6) return false
        var dot = 0.0
        for (i in 0 until count) dot += input[i] * predicted[bestLag + i]
        val fitGain = dot / predictedEnergy
        if (fitGain !in .4..1.6) return false
        val gain = min(fitGain, 1.0) * .85
        var outputEnergy = 0.0
        for (i in 0 until count) {
            val sample = input[i] - gain * predicted[bestLag + i]
            if (sample !in -1.0..(32767.0 / 32768)) return false
            outputEnergy += sample * sample
        }
        if (outputEnergy >= rawEnergy * .95) return false
        for (i in 0 until count) pcm[i] = ((input[i] - gain * predicted[bestLag + i]) * 32768).roundToInt().toShort()
        return true
    }
}

private fun energy(signal: FloatArray, start: Int = 0, end: Int = signal.size): Double {
    var result = 0.0
    for (i in start until end) result += signal[i].toDouble() * signal[i]
    return result
}

private fun correlation(a: FloatArray, ai: Int, b: FloatArray, bi: Int, size: Int, step: Int): Float {
    var dot = 0.0
    var aa = 0.0
    var bb = 0.0
    for (i in 0 until size step step) {
        val x = a[ai + i].toDouble()
        val y = b[bi + i].toDouble()
        dot += x * y
        aa += x * x
        bb += y * y
    }
    return if (aa * bb > 1e-20) (dot / sqrt(aa * bb)).toFloat() else 0f
}

private fun median(values: List<Float>): Float {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) * .5f else sorted[middle]
}

/** Zero-phase Butterworth sections are used only while preparing a model, never on live PCM. */
private fun bandLimited(input: FloatArray, count: Int, low: Double, high: Double, sampleRate: Int): FloatArray {
    val output = input.copyOf(count)
    fun section(cutoff: Double, highPass: Boolean) {
        val angle = 2 * PI * cutoff / sampleRate
        val cosine = cos(angle)
        val alpha = sin(angle) / sqrt(2.0)
        val a0 = 1 + alpha
        val b0 = (if (highPass) 1 + cosine else 1 - cosine) / (2 * a0)
        val b1 = (if (highPass) -(1 + cosine) else 1 - cosine) / a0
        val a1 = -2 * cosine / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in output.indices) {
            val x = output[i].toDouble()
            val y = b0 * x + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            output[i] = y.toFloat()
        }
    }
    section(low, true)
    section(high, false)
    output.reverse()
    section(low, true)
    section(high, false)
    output.reverse()
    return output
}

package com.ssafy.eolssu.core.audio

import kotlin.math.sqrt

/** 짧은 단음의 주기를 찾는다. 입력이 작거나 주기가 불명확하면 null이다. */
data class PitchEstimate(val frequencyHz: Double, val normalizedDifference: Double)

object YinPitchEstimator {
    fun detect(samples: FloatArray, sampleRate: Int): Double? = estimate(samples, sampleRate)?.frequencyHz

    fun estimate(samples: FloatArray, sampleRate: Int): PitchEstimate? {
        if (sampleRate <= 0 || samples.size < 4) return null
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        if (rms < 0.005623413) return null
        val maxLag = minOf(samples.size / 2, sampleRate / 60)
        val minLag = maxOf(2, sampleRate / 3000)
        if (maxLag <= minLag) return null
        val difference = DoubleArray(maxLag + 1)
        val span = samples.size - maxLag
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until span) {
                val delta = samples[i] - samples[i + lag]
                sum += delta * delta
            }
            difference[lag] = sum
        }
        var total = 0.0
        for (lag in 1..maxLag) {
            total += difference[lag]
            difference[lag] = if (total > 0.0) difference[lag] * lag / total else 1.0
        }
        var lag = minLag
        while (lag < maxLag) {
            if (difference[lag] < 0.15) {
                while (lag + 1 <= maxLag && difference[lag + 1] < difference[lag]) lag++
                val left = difference[lag - 1]
                val center = difference[lag]
                val right = difference[minOf(lag + 1, maxLag)]
                val denominator = left - 2 * center + right
                val correction = if (denominator != 0.0) 0.5 * (left - right) / denominator else 0.0
                return PitchEstimate(sampleRate / (lag + correction), center)
            }
            lag++
        }
        return null
    }
}

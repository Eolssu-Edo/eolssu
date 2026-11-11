package com.ssafy.eolssu.core.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** 짧은 단음의 주기를 찾는다. 입력이 작거나 주기가 불명확하면 null이다. */
data class PitchEstimate(val frequencyHz: Double, val normalizedDifference: Double)

/**
 * 높이를 구할 최소 조건. 창 RMS가 [minLevelDbfs]보다 작거나, 정규화 차이가 [maxDifference]보다 작은 주기가 없으면(김 소리·잡음)
 * 높이를 구하지 않는다. [Default]는 모든 화면의 기본값이고, [Lenient]는 한 음씩 단소처럼 김 소리가 많은 악기용이다(S15P31E102-666).
 */
data class PitchGate(val minLevelDbfs: Double = -45.0, val maxDifference: Double = 0.15) {
    companion object {
        val Default = PitchGate()
        val Lenient = PitchGate(minLevelDbfs = -55.0, maxDifference = 0.2)
    }
}

object YinPitchEstimator {
    fun detect(samples: FloatArray, sampleRate: Int): Double? = estimate(samples, sampleRate)?.frequencyHz

    /** 창의 RMS 크기(dBFS, 1.0 = 0dBFS). 모두 0이면 음의 무한대다. 높이를 못 구한 이유가 크기인지 보는 데 쓴다. */
    fun rmsDbfs(samples: FloatArray): Double {
        if (samples.isEmpty()) return Double.NEGATIVE_INFINITY
        return 20 * log10(rms(samples))
    }

    private fun rms(samples: FloatArray) = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)

    fun estimate(samples: FloatArray, sampleRate: Int, gate: PitchGate = PitchGate.Default): PitchEstimate? {
        if (sampleRate <= 0 || samples.size < 4) return null
        if (rms(samples) < 10.0.pow(gate.minLevelDbfs / 20)) return null
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
            if (difference[lag] < gate.maxDifference) {
                while (lag + 1 <= maxLag && difference[lag + 1] < difference[lag]) lag++
                val left = difference[lag - 1]
                val center = difference[lag]
                val right = difference[minOf(lag + 1, maxLag)]
                val denominator = left - 2 * center + right
                val correction = if (denominator != 0.0) 0.5 * (left - right) / denominator else 0.0
                return PitchEstimate(correctHarmonic(samples, sampleRate, sampleRate / (lag + correction)), center)
            }
            lag++
        }
        return null
    }

    /**
     * YIN이 고른 높이를 같은 창의 주파수 세기로 다시 본다(S15P31E102-556 D-1·D-2). 목표 음은 쓰지 않는다.
     * 주기 T로 반복되는 소리는 2T·3T로도 반복되어, 음 경계에서 1/2·1/3·1/5 높이를 고를 수 있다.
     * 고른 높이 자리에 소리가 거의 없고 배수 자리에만 있으면, 소리가 있는 가장 낮은 배수로 올린다.
     * 두 배로 잘못 잡는 쪽은 고치지 않는다. YIN은 기본음이 2배음의 0.25배 이하일 때만 그렇게 틀리는데
     * 실제 단소는 0.82배 이상이고, 내려 고치면 진짜 역취를 숨길 수 있다(D-3 철회).
     */
    internal fun correctHarmonic(samples: FloatArray, sampleRate: Int, hz: Double): Double {
        val windowed = hannWindowed(samples)
        val strength = { f: Double -> amplitudeNear(windowed, sampleRate, f) }
        val multiples = (2..5).filter { hz * it < sampleRate / 2.0 }.associateWith { strength(hz * it) }
        val loudest = multiples.values.maxOrNull() ?: return hz
        // 서버 S15P31E102-251과 같은 0.1배. 실제 단소 단음의 A(f)/A(2f) 하위 1%는 0.82였다.
        if (strength(hz) >= 0.1 * loudest) return hz
        // 가장 센 배수가 아니라 소리가 있는 가장 낮은 배수다. NaN 같은 예외 입력은 고치지 않는다.
        return hz * (multiples.entries.firstOrNull { it.value >= 0.1 * loudest }?.key ?: return hz)
    }

    private var hann = DoubleArray(0)

    private fun hannWindowed(samples: FloatArray): DoubleArray {
        val n = samples.size
        val window = hann.takeIf { it.size == n }
            ?: DoubleArray(n) { 0.5 - 0.5 * kotlin.math.cos(2 * kotlin.math.PI * it / (n - 1)) }.also { hann = it }
        return DoubleArray(n) { samples[it] * window[it] }
    }

    /**
     * Hann 창을 씌운 주파수 세기. 서버 `near()`처럼 가장 가까운 칸과 양옆 한 칸 중 최댓값이다.
     * 칸 하나의 크기를 Goertzel 점화식으로 구해 샘플마다 삼각함수를 부르지 않는다(창당 최대 15칸 × 2,048번 곱셈·덧셈).
     */
    private fun amplitudeNear(windowed: DoubleArray, sampleRate: Int, hz: Double): Double {
        val n = windowed.size
        val center = kotlin.math.round(hz * n / sampleRate).toInt()
        return (center - 1..center + 1).filter { it in 1 until n / 2 }.maxOfOrNull { bin ->
            val coefficient = 2 * kotlin.math.cos(2 * kotlin.math.PI * bin / n)
            var previous = 0.0
            var beforePrevious = 0.0
            for (x in windowed) {
                val current = x + coefficient * previous - beforePrevious
                beforePrevious = previous
                previous = current
            }
            sqrt(maxOf(0.0, previous * previous + beforePrevious * beforePrevious - coefficient * previous * beforePrevious))
        } ?: 0.0
    }
}

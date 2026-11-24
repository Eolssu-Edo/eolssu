package com.ssafy.eolssu.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 마이크 입력([inputRate], 보통 48kHz)을 [outputRate] 모노 PCM16으로 줄여 쌓고 WAV로 만든다. 웹 녹음기가 쓴다(S15P31E102-1262).
 * 서버가 이 WAV를 AAC m4a로 바꿔 저장하고, AI는 어차피 16kHz 모노로 디코딩하므로 16kHz로 줄여도 채점 입력은 같다.
 * 줄이기 전에 출력 나이퀴스트의 84%(16kHz 출력이면 약 6.7kHz)에서 자르는 저역 통과 필터(블랙먼 창 sinc)로 접힘을 막고,
 * 출력 샘플 위치는 필터를 거친 이웃 두 샘플 사이를 선형 보간한다. 입력 묶음을 어떻게 나눠 넣어도 결과가 같다.
 */
internal class PcmWavWriter(private val inputRate: Int, val outputRate: Int = WEB_RECORDING_RATE, taps: Int = 95) {
    init {
        require(inputRate > 0 && outputRate in 1..inputRate) { "Unsupported rates $inputRate -> $outputRate" }
        require(taps % 2 == 1) { "Taps must be odd" }
    }

    private val half = taps / 2
    private val kernel = lowPassKernel(half, CUTOFF_RATIO * outputRate / 2 / inputRate)
    private val step = inputRate.toDouble() / outputRate

    /** 아직 필요한 입력. [input]의 0번이 전체 입력의 [inputStart]번째 샘플이다. */
    private var input = FloatArray(INITIAL_INPUT)
    private var inputStart = 0L
    private var inputEnd = 0L
    private var nextOutput = 0L
    private var output = ShortArray(outputRate * INITIAL_SECONDS)
    private var finished = false

    /** 지금까지 만든 출력 샘플 수. */
    var outputSamples = 0
        private set

    /** 입력 [count]개를 더한다. 필터가 앞뒤 샘플을 보므로 마지막 몇 샘플은 다음 입력이나 [finish]에서 나온다. */
    fun push(samples: FloatArray, count: Int = samples.size) {
        check(!finished) { "Already finished" }
        append(samples, count)
        drain(limit = null)
    }

    /** 남은 입력을 마저 줄이고 44바이트 헤더를 붙인 WAV를 돌려준다. 그 뒤로는 [push]할 수 없다. */
    fun finish(): ByteArray {
        check(!finished) { "Already finished" }
        finished = true
        val realEnd = inputEnd
        append(FloatArray(half + 2), half + 2)
        drain(limit = realEnd)
        return pcm16MonoWav(output, outputSamples, outputRate)
    }

    private fun append(samples: FloatArray, count: Int) {
        val used = (inputEnd - inputStart).toInt()
        if (used + count > input.size) input = input.copyOf(maxOf(input.size * 2, used + count))
        samples.copyInto(input, used, 0, count)
        inputEnd += count
    }

    /** 앞뒤 입력이 모두 있는 출력 샘플을 만든다. [limit]이 있으면 그 입력 위치 앞까지만 만든다(끝의 0 채움 구간 제외). */
    private fun drain(limit: Long?) {
        while (true) {
            val position = nextOutput * step
            if (limit != null && position >= limit) break
            val base = floor(position).toLong()
            if (base + 1 + half >= inputEnd) break
            val fraction = (position - base).toFloat()
            val left = filtered(base)
            val right = filtered(base + 1)
            write(left + (right - left) * fraction)
            nextOutput++
        }
        val keepFrom = maxOf(inputStart, floor(nextOutput * step).toLong() - half)
        if (keepFrom > inputStart) {
            input.copyInto(input, 0, (keepFrom - inputStart).toInt(), (inputEnd - inputStart).toInt())
            inputStart = keepFrom
        }
    }

    /** 전체 입력의 [center]번째 샘플을 중심으로 거른 값. 녹음 시작 전은 무음으로 본다. */
    private fun filtered(center: Long): Float {
        var sum = 0f
        for (k in -half..half) {
            val index = center + k
            if (index < 0) continue
            sum += kernel[k + half] * input[(index - inputStart).toInt()]
        }
        return sum
    }

    private fun write(value: Float) {
        if (outputSamples == output.size) output = output.copyOf(output.size * 2)
        output[outputSamples++] = (value * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
    }

    private companion object {
        /** 출력 나이퀴스트 대비 통과 대역. 95탭 블랙먼 창이면 저지 대역이 출력 나이퀴스트 근처에서 시작한다. */
        const val CUTOFF_RATIO = 0.84
        const val INITIAL_INPUT = 8_192
        const val INITIAL_SECONDS = 60
    }
}

/** 웹 녹음 파일의 샘플레이트. 10분이 19,200,044바이트로 업로드 한도(20MiB) 안이다. */
internal const val WEB_RECORDING_RATE = 16_000

internal const val WAV_HEADER_BYTES = 44

/** PCM16 모노 [samples] 앞 [count]개로 WAV(RIFF, 리틀 엔디언)를 만든다. */
internal fun pcm16MonoWav(samples: ShortArray, count: Int, sampleRate: Int): ByteArray {
    val dataBytes = count * 2
    val wav = ByteArray(WAV_HEADER_BYTES + dataBytes)
    fun text(offset: Int, value: String) = value.forEachIndexed { i, c -> wav[offset + i] = c.code.toByte() }
    fun int32(offset: Int, value: Int) = repeat(4) { wav[offset + it] = (value ushr (8 * it)).toByte() }
    fun int16(offset: Int, value: Int) = repeat(2) { wav[offset + it] = (value ushr (8 * it)).toByte() }
    text(0, "RIFF")
    int32(4, 36 + dataBytes)
    text(8, "WAVE")
    text(12, "fmt ")
    int32(16, 16)
    int16(20, 1) // PCM
    int16(22, 1) // 모노
    int32(24, sampleRate)
    int32(28, sampleRate * 2)
    int16(32, 2)
    int16(34, 16)
    text(36, "data")
    int32(40, dataBytes)
    for (i in 0 until count) {
        val sample = samples[i].toInt()
        wav[WAV_HEADER_BYTES + 2 * i] = sample.toByte()
        wav[WAV_HEADER_BYTES + 2 * i + 1] = (sample shr 8).toByte()
    }
    return wav
}

/** 차단 주파수 [cutoff](입력 샘플당 주기, 0~0.5)의 블랙먼 창 sinc 저역 통과 계수. 합이 1이라 직류 크기를 바꾸지 않는다. */
private fun lowPassKernel(half: Int, cutoff: Double): FloatArray {
    val n = 2 * half + 1
    val values = DoubleArray(n) { i ->
        val k = i - half
        val sinc = if (k == 0) 2 * cutoff else sin(2 * PI * cutoff * k) / (PI * k)
        val window = 0.42 - 0.5 * cos(2 * PI * i / (n - 1)) + 0.08 * cos(4 * PI * i / (n - 1))
        sinc * window
    }
    val sum = values.sum()
    return FloatArray(n) { (values[it] / sum).toFloat() }
}

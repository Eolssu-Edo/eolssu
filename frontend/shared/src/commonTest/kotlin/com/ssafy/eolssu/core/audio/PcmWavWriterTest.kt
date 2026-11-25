package com.ssafy.eolssu.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmWavWriterTest {
    private fun sine(hz: Double, rate: Int, seconds: Double, amplitude: Float = 0.5f) =
        FloatArray((rate * seconds).toInt()) { (amplitude * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun samples(wav: ByteArray): IntArray = IntArray((wav.size - WAV_HEADER_BYTES) / 2) {
        val low = wav[WAV_HEADER_BYTES + 2 * it].toInt() and 0xFF
        val high = wav[WAV_HEADER_BYTES + 2 * it + 1].toInt()
        (high shl 8) or low
    }

    private fun int32(wav: ByteArray, offset: Int) =
        (0..3).sumOf { (wav[offset + it].toInt() and 0xFF) shl (8 * it) }

    /** 필터가 앞뒤 샘플을 보는 가장자리를 빼고 잰 RMS(정수 PCM16 기준). */
    private fun rms(values: IntArray): Double {
        val middle = values.copyOfRange(values.size / 10, values.size - values.size / 10)
        return sqrt(middle.sumOf { it.toDouble() * it } / middle.size)
    }

    @Test
    fun wavHeaderDescribes16BitMonoPcmAtTheOutputRate() {
        val wav = PcmWavWriter(48_000).apply { push(sine(440.0, 48_000, 0.5)) }.finish()

        assertContentEquals("RIFF".encodeToByteArray(), wav.copyOfRange(0, 4))
        assertContentEquals("WAVEfmt ".encodeToByteArray(), wav.copyOfRange(8, 16))
        assertContentEquals("data".encodeToByteArray(), wav.copyOfRange(36, 40))
        assertEquals(wav.size - 8, int32(wav, 4))
        assertEquals(16_000, int32(wav, 24))
        assertEquals(32_000, int32(wav, 28))
        assertEquals(wav.size - WAV_HEADER_BYTES, int32(wav, 40))
        assertEquals(1, wav[20].toInt()) // PCM
        assertEquals(1, wav[22].toInt()) // 모노
        assertEquals(16, wav[34].toInt())
    }

    @Test
    fun outputLengthMatchesTheInputDuration() {
        for (rate in listOf(48_000, 44_100, 16_000)) {
            val wav = PcmWavWriter(rate).apply { push(FloatArray(rate)) }.finish()
            assertEquals(16_000, samples(wav).size, "input $rate")
        }
        // 10분 웹 녹음은 업로드 한도(20MiB) 안이다.
        assertEquals(19_200_044, WAV_HEADER_BYTES + 600 * WEB_RECORDING_RATE * 2)
    }

    @Test
    fun inBandToneKeepsItsLevelAndPitch() {
        val output = samples(PcmWavWriter(48_000).apply { push(sine(1_000.0, 48_000, 1.0)) }.finish())

        // 진폭 0.5 사인파의 RMS는 0.5/√2(PCM16으로 약 11585)다.
        assertTrue(abs(rms(output) - 11_585) < 300, "rms ${rms(output)}")
        val middle = output.copyOfRange(1_600, 14_400)
        val crossings = (1 until middle.size).count { (middle[it - 1] < 0) != (middle[it] < 0) }
        // 0.8초 동안 1kHz는 부호가 약 1600번 바뀐다.
        assertTrue(crossings in 1_590..1_610, "crossings $crossings")
    }

    @Test
    fun toneAboveTheOutputNyquistIsFilteredInsteadOfFoldingBack() {
        val output = samples(PcmWavWriter(48_000).apply { push(sine(12_000.0, 48_000, 1.0)) }.finish())

        // 12kHz는 16kHz 파일에서 4kHz로 접혀 들릴 소리다. 원래 크기(약 11585)의 1% 아래로 줄어야 한다.
        assertTrue(rms(output) < 116, "rms ${rms(output)}")
    }

    @Test
    fun chunkSizesDoNotChangeTheResult() {
        val input = sine(523.25, 44_100, 0.7)
        val whole = PcmWavWriter(44_100).apply { push(input) }.finish()
        val chunked = PcmWavWriter(44_100).apply {
            var offset = 0
            for (size in generateSequence(1) { it * 3 % 1_031 + 1 }) {
                if (offset >= input.size) break
                val count = minOf(size, input.size - offset)
                push(input.copyOfRange(offset, offset + count))
                offset += count
            }
        }.finish()

        assertContentEquals(whole, chunked)
    }

    @Test
    fun loudInputIsClippedToThePcm16Range() {
        val output = samples(PcmWavWriter(16_000).apply { push(FloatArray(1_600) { 2f }) }.finish())

        assertEquals(32_767, output[800])
        assertTrue(output.all { it in -32_768..32_767 })
    }
}

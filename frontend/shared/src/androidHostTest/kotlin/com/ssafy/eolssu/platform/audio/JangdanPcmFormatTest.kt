package com.ssafy.eolssu.platform.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JangdanPcmFormatTest {
    @Test fun deviceRateConversionKeepsDurationAndPitch() {
        val source = ShortArray(44100) { (sin(2 * PI * 440 * it / 44100) * 16000).toInt().toShort() }
        val bytes = ByteBuffer.allocate(source.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            .apply { asShortBuffer().put(source) }.array()
        val output = jangdanOutputPcm(bytes, 48000)
        assertTrue(output.size in 47999..48001, "One second of audio must remain one second")
        val rising = output.indices.drop(1).count { output[it - 1] <= 0 && output[it] > 0 }
        assertEquals(440, rising, "Resampling must preserve the original pitch")
    }

    @Test fun nativeRateSamplesKeepEveryPcmValue() {
        val samples = shortArrayOf(0, -32768, 32767, -100, 100)
        val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            .apply { asShortBuffer().put(samples) }.array()
        assertContentEquals(samples, jangdanOutputPcm(bytes, 44100))
    }
}

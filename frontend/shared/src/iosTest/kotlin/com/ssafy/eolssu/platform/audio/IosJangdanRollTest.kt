package com.ssafy.eolssu.platform.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer

@OptIn(ExperimentalForeignApi::class)
class IosJangdanRollTest {
    private val format = checkNotNull(AVAudioFormat(standardFormatWithSampleRate = 44_100.0, channels = 1u))

    @Test
    fun stretchFillsExactlyTheTargetFramesAndKeepsSound() {
        val source = tone(frames = 44_100)
        for (target in listOf(22_050, 66_150)) {
            val output = assertNotNull(stretchJangdanRoll(source, target))
            assertEquals(target.toUInt(), output.frameLength)
            val samples = checkNotNull(output.floatChannelData)[0]!!
            // The first half of the output must carry the tone, not the time-pitch unit's silent lead-in.
            val peak = (0 until target / 2).maxOf { abs(samples[it]) }
            assertTrue(peak > 0.1f, "peak=$peak for $target frames")
        }
    }

    @Test
    fun stretchRejectsEmptyOrStereoInput() {
        assertNull(stretchJangdanRoll(tone(frames = 1_000), targetFrames = 0))
        val stereo = checkNotNull(AVAudioFormat(standardFormatWithSampleRate = 44_100.0, channels = 2u))
        val buffer = checkNotNull(AVAudioPCMBuffer(pCMFormat = stereo, frameCapacity = 100u)).apply { frameLength = 100u }
        assertNull(stretchJangdanRoll(buffer, targetFrames = 200))
    }

    private fun tone(frames: Int): AVAudioPCMBuffer =
        checkNotNull(AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt())).apply {
            val samples = checkNotNull(floatChannelData)[0]!!
            for (i in 0 until frames) samples[i] = (0.5 * sin(2 * PI * 440 * i / 44_100.0)).toFloat()
            frameLength = frames.toUInt()
        }
}

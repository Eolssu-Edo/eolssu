package com.ssafy.eolssu.platform.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ssafy.eolssu.core.audio.JANGDAN_SAMPLE_RATE
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 준비 중에만 변환한다. 재생 속도·음높이는 그대로 두고 기기의 native 출력률에 맞춘다. */
@OptIn(UnstableApi::class)
internal fun jangdanOutputPcm(data: ByteArray, outputRate: Int): ShortArray {
    val input = ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder()).apply { put(data); flip() }
    val output = if (outputRate == JANGDAN_SAMPLE_RATE) input else {
        val processor = SonicAudioProcessor().apply {
            setOutputSampleRateHz(outputRate)
            configure(AudioProcessor.AudioFormat(JANGDAN_SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT))
            flush(AudioProcessor.StreamMetadata.DEFAULT)
        }
        processor.queueInput(input)
        processor.queueEndOfStream()
        processor.output
    }
    return ShortArray(output.remaining() / 2).also { output.order(ByteOrder.nativeOrder()).asShortBuffer().get(it) }
}

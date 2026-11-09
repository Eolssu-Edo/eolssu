package com.ssafy.eolssu.platform.audio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureTimestampAlignmentTest {
    private val buffer = 3528

    @Test
    fun startupBacklogIsNotAlignedButDoesNotEndRecording() {
        // API 37 에뮬레이터 실측: 첫 읽기 882프레임일 때 장치 위치는 이미 14,790프레임이다.
        assertFalse(captureTimestampAligned(framePosition = 14_790, readFrames = 882, bufferFrames = buffer))
    }

    @Test
    fun backlogDrainedWithinBufferIsAlignedAgain() {
        // 같은 녹음에서 약 0.15초 뒤 차이가 버퍼 안으로 줄었다.
        assertTrue(captureTimestampAligned(framePosition = 20_637, readFrames = 20_286, bufferFrames = buffer))
        assertTrue(captureTimestampAligned(framePosition = 44_100L + buffer, readFrames = 44_100, bufferFrames = buffer))
        assertFalse(captureTimestampAligned(framePosition = 44_100L + buffer + 1, readFrames = 44_100, bufferFrames = buffer))
    }
}

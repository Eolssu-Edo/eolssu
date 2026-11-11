package com.ssafy.eolssu.platform.audio

/**
 * 녹음 입력을 음높이 분석 창으로 자른다. Android 녹음기와 같은 [size]·[hop]으로, 녹음 파일의 샘플 위치를 함께 센다.
 * 입력 묶음 크기와 관계없이 [hop]마다 창 하나를 낸다. 일시정지 뒤에는 [reset]으로 이전 구간의 소리를 버린다.
 */
internal class PitchWindows(private val size: Int, private val hop: Int) {
    private val rolling = FloatArray(size)
    private var fill = 0

    /** 녹음 파일에 쓴 전체 샘플 수. */
    var fileSamples = 0L
        private set

    /** [count]개 샘플을 더하고, 채워진 창마다 [onWindow]에 창 끝의 파일 위치와 창 복사본을 넘긴다. */
    fun push(count: Int, sample: (Int) -> Float, onWindow: (endSample: Long, samples: FloatArray) -> Unit) {
        for (i in 0 until count) {
            rolling[fill++] = sample(i)
            fileSamples++
            if (fill == size) {
                onWindow(fileSamples, rolling.copyOf())
                rolling.copyInto(rolling, 0, hop, size)
                fill -= hop
            }
        }
    }

    fun reset() {
        fill = 0
    }
}

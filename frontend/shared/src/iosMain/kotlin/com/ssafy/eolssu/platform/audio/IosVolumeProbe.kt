package com.ssafy.eolssu.platform.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume

/**
 * 디버그 빌드 전용 측정 로그(S15P31E102-907). 평가 녹음 중 약 1초마다 시스템 볼륨과 마이크 입력 크기를 남긴다.
 * 조용히 둔 채 장단만 켜고 볼륨 단계별로 녹음하면 iPhone 스피커 곡선(볼륨 → dB)을 앱 녹음기로 잴 수 있다.
 * `rms`는 1초 평균 크기, `peak`는 그 1초 안에서 가장 큰 창(장단 타격)의 크기다.
 */
internal class IosVolumeProbe(private val windowsPerReport: Int = 50, private val log: (String) -> Unit = ::println) {
    private var count = 0
    private var power = 0.0
    private var peak = Double.NEGATIVE_INFINITY

    fun add(dbfs: Double) {
        if (!dbfs.isFinite()) return
        power += 10.0.pow(dbfs / 10)
        peak = max(peak, dbfs)
        if (++count < windowsPerReport) return
        val volume = AVAudioSession.sharedInstance().outputVolume
        log("EolssuVolumeProbe volume=${volume.format(3)} rms=${(10 * log10(power / count)).format(1)} peak=${peak.format(1)}")
        count = 0
        power = 0.0
        peak = Double.NEGATIVE_INFINITY
    }

    private fun Number.format(digits: Int): String {
        val scale = 10.0.pow(digits)
        return (kotlin.math.round(toDouble() * scale) / scale).toString()
    }
}

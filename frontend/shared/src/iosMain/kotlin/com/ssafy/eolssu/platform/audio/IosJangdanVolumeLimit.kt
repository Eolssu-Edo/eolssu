package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanVolumeLimit
import com.ssafy.eolssu.core.audio.evaluationJangdanGain
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume

/** Android와 같은 문턱·상한(S15P31E102-878 D-2): 최대 볼륨에 대한 비율 40/150, 60/150. */
private const val KNEE_RATIO = 40f / 150
private const val CEILING_RATIO = 60f / 150

/**
 * iPhone 스피커 볼륨 곡선. `outputVolume`(0~1) 위치마다 최대 볼륨 대비 크기(dB, 최대 = 0)를 두고 사이는 선형 보간한다.
 * iOS는 볼륨 단계의 dB를 공개하지 않아 실기기에서 앱 녹음기로 잰 값을 쓴다(S15P31E102-907).
 */
internal class IosVolumeCurve(points: List<Pair<Float, Float>>) {
    private val points = points.sortedBy { it.first }

    init {
        require(this.points.size >= 2 && this.points.all { it.first in 0f..1f && it.second.isFinite() })
    }

    fun db(volume: Float): Float {
        val v = volume.coerceIn(points.first().first, points.last().first)
        val upper = points.indexOfFirst { it.first >= v }
        if (upper <= 0) return points.first().second
        val (x0, y0) = points[upper - 1]
        val (x1, y1) = points[upper]
        return if (x1 == x0) y1 else y0 + (y1 - y0) * (v - x0) / (x1 - x0)
    }
}

/** 지금 볼륨 [volume]과 곡선 [curve]로 평가 중 장단 배율을 정한다. 무음(0)·범위 밖이면 1이다. */
internal fun iosEvaluationGain(volume: Float, curve: IosVolumeCurve): Float {
    if (!volume.isFinite() || volume <= 0f || volume > 1f) return 1f
    return evaluationJangdanGain(curve.db(volume), curve.db(KNEE_RATIO), curve.db(CEILING_RATIO), curve.db(1f))
}

/** 타격마다 시스템 볼륨을 읽어 배율을 준다. 읽기에 실패하면 제한하지 않는다. */
internal class IosJangdanVolumeLimit(private val curve: IosVolumeCurve) : JangdanVolumeLimit {
    override fun gain(): Float = runCatching {
        iosEvaluationGain(AVAudioSession.sharedInstance().outputVolume, curve)
    }.getOrDefault(1f)
}

package com.ssafy.eolssu.core.audio

import kotlin.coroutines.CoroutineContext
import kotlin.math.pow

/**
 * 평가 녹음 중 장단 타격에 곱할 배율(0~1). 스피커 장단이 녹음에 크게 섞이면 채점 점수가 떨어진다(S15P31E102-878).
 * 플랫폼이 시스템 볼륨을 읽어 계산하고, 읽을 수 없으면 1을 준다.
 */
fun interface JangdanVolumeLimit {
    fun gain(): Float
}

/** 제한하지 않는다. 웹과 테스트의 기본값. */
val NoJangdanVolumeLimit = JangdanVolumeLimit { 1f }

/**
 * 시스템 볼륨이 [kneeDb]를 넘으면 [kneeDb]~[topDb]를 [kneeDb]~[ceilingDb]로 dB에서 고르게 압축하는 배율.
 * 볼륨을 올리면 장단도 조금씩 커지고, 최대 볼륨에서 [ceilingDb] 크기가 된다. 곡선이 이상하면 1이다.
 */
fun evaluationJangdanGain(systemDb: Float, kneeDb: Float, ceilingDb: Float, topDb: Float): Float {
    val valid = listOf(systemDb, kneeDb, ceilingDb, topDb).all { it.isFinite() } && kneeDb < ceilingDb && ceilingDb <= topDb
    if (!valid || systemDb <= kneeDb) return 1f
    val heard = kneeDb + (systemDb - kneeDb) * (ceilingDb - kneeDb) / (topDb - kneeDb)
    return 10f.pow((heard - systemDb) / 20f).coerceIn(0f, 1f)
}

/** [active]일 때만 [limit] 배율을 타격 gain에 곱한다. 나머지는 [inner] 그대로다. */
internal class LimitedJangdanSamplePlayer(
    private val inner: JangdanSamplePlayer,
    private val limit: JangdanVolumeLimit,
    private val active: () -> Boolean,
) : JangdanSamplePlayer by inner {
    override val failed: Boolean get() = inner.failed
    override val timingContext: CoroutineContext get() = inner.timingContext
    override fun play(clip: JangdanClip, gain: Float): Boolean = inner.play(clip, if (active()) gain * limit.gain() else gain)
}

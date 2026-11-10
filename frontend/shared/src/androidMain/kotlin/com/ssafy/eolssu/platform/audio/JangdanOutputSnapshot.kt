package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanClip

internal data class RenderedJangdanEvent(
    val clip: JangdanClip,
    val gain: Float,
    val frame: Long,
)

/** frameZeroNanos는 AudioTrack 하드웨어 시각. 호출 시각/버퍼 쓰기 완료 시각이 아니다. */
internal data class JangdanOutputSnapshot(
    val frameZeroNanos: Long?,
    val epoch: Long,
    val volume: Int,
    val deviceId: Int?,
    val deviceType: Int?,
    val underruns: Int,
    val clipped: Boolean,
    val events: List<RenderedJangdanEvent>,
)

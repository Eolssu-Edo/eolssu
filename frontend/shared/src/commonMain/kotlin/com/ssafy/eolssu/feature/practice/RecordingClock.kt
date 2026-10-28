package com.ssafy.eolssu.feature.practice

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 실제로 녹음된 시간. 일시정지한 동안(연주 중단 확인 창·이어 연주 카운트다운)은 세지 않는다. */
class RecordingClock(private val timeSource: TimeSource = TimeSource.Monotonic) {
    private var done: Duration = Duration.ZERO
    private var runningSince: TimeMark? = null

    val running: Boolean get() = runningSince != null

    fun start() {
        done = Duration.ZERO
        runningSince = timeSource.markNow()
    }

    fun pause() {
        val since = runningSince ?: return
        done += since.elapsedNow()
        runningSince = null
    }

    fun resume() {
        if (runningSince == null) runningSince = timeSource.markNow()
    }

    fun reset() {
        done = Duration.ZERO
        runningSince = null
    }

    fun elapsed(): Duration = done + (runningSince?.elapsedNow() ?: Duration.ZERO)
}

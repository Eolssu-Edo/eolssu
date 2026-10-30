package com.ssafy.eolssu.feature.practice

import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 표시/위치 시뮬레이션 시계. 실제 오디오 재생 시계나 마이크 녹음 완료의 증거가 아니다. */
internal class PracticeTimelineClock(
    timeSource: TimeSource,
    private val carriedEventMs: Double,
    private val carriedElapsedMs: Double,
) {
    private val startedAt = timeSource.markNow()
    private var completedEventMs = 0.0

    val eventStartedAt: TimeMark get() = startedAt + (completedEventMs - carriedEventMs).milliseconds

    fun sample(): TimelineSample {
        val activeMs = startedAt.elapsedNow().inWholeNanoseconds / 1_000_000.0
        return TimelineSample(carriedEventMs + activeMs - completedEventMs, carriedElapsedMs + activeMs)
    }

    /** 늦게 깨어나도 다음 이벤트의 초과 경과분을 버리지 않아 매 음마다 지연이 쌓이지 않는다. */
    fun completeEvent(durationMs: Double) { completedEventMs += durationMs }
}

internal data class TimelineSample(val eventElapsedMs: Double, val elapsedMs: Double)

/** 50ms 이하로 진행 시간을 갱신하되, 박·정간 경계가 먼저 오면 정확히 그 경계에 깨어난다. */
internal fun PracticeUiState.nextTimelineSampleMs(): Double {
    val song = song ?: return 50.0
    val event = song.timeline.getOrNull(position) ?: return 50.0
    val duration = eventDurationMs()
    val eventMs = eventElapsedMs.coerceIn(0.0, duration)
    val remaining = duration - eventMs
    val cellMs = song.cellDurationMs(bpm)
    val unit = event.unitStart + event.unitDuration * (eventMs / duration)
    val untilCell = (floor(unit) + 1 - unit) * cellMs
    val untilBeat = song.score.timing?.let { timing ->
        val beatMs = 60_000.0 / bpm
        val beat = requireNotNull(event.startTick).toDouble() / timing.ticksPerBeat + eventMs / beatMs
        (floor(beat) + 1 - beat) * beatMs
    } ?: untilCell
    return minOf(50.0, remaining, untilCell, untilBeat).coerceAtLeast(0.001)
}

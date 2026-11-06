package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.nanoseconds

/** 화면과 녹음 제어를 바꾸지 않고, 현재 연주의 음높이 결과 구독과 만료만 관리한다. */
internal class PracticePitchSession(
    private val recorder: AudioRecorder?,
    private val scope: CoroutineScope,
    private val currentIndex: () -> Int,
) {
    private val output = MutableStateFlow(PracticePitchFeedback())
    val state = output.asStateFlow()
    private var collector: Job? = null
    private var expiry: Job? = null
    private var tracker: PracticePitchTracker? = null
    private var initial: CaptureBoundary? = null
    private var scoreStartMs = 0.0

    fun start(song: Song, bpm: Int, elapsedMs: Double) {
        stop()
        val source = recorder?.pitchReadings ?: return
        val timing = song.score.timing ?: return
        val boundary = recorder.captureBoundary ?: return
        if (bpm <= 0 || timing.ticksPerBeat <= 0 || !timing.hwangHz.isFinite() || timing.hwangHz <= 0) return
        initial = boundary
        scoreStartMs = elapsedMs
        val next = PracticePitchTracker(song.timeline, timing.hwangHz, 60_000.0 / bpm / timing.ticksPerBeat)
        tracker = next
        next.anchor(boundary, elapsedMs)
        collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.collect { reading ->
                val observed = anchorCurrent() ?: return@collect
                publish(next.accept(reading, currentIndex(), observed.observedNanos))
            }
        }
    }

    /** 악보 위치가 이동해도 현재·직전 결과는 원본 이벤트 ID에 연결한다. */
    fun refresh() {
        val active = tracker ?: return
        val observed = anchorCurrent() ?: return
        publish(active.expire(currentIndex(), observed.observedNanos))
    }

    private fun anchorCurrent(): CaptureBoundary? {
        val base = initial ?: return null
        val observed = recorder?.captureBoundary
        if (observed == null || base.recordingId != observed.recordingId || base.segmentId != observed.segmentId ||
            base.samplePosition != observed.samplePosition || base.sampleRate != observed.sampleRate) {
            output.value = PracticePitchFeedback()
            expiry?.cancel()
            return null
        }
        tracker?.anchor(observed, scoreStartMs)
        return observed
    }

    private fun publish(value: PracticePitchFeedback) {
        output.value = value
        expiry?.cancel()
        val expiresAt = listOfNotNull(value.current, value.previous).minOfOrNull { it.capturedEndNanos + 300_000_000L } ?: return
        val now = recorder?.captureBoundary?.observedNanos ?: return
        expiry = scope.launch {
            delay((expiresAt - now).coerceAtLeast(1).nanoseconds)
            refresh()
        }
    }

    fun stop() {
        collector?.cancel()
        expiry?.cancel()
        collector = null
        expiry = null
        tracker = null
        initial = null
        output.value = PracticePitchFeedback()
    }
}

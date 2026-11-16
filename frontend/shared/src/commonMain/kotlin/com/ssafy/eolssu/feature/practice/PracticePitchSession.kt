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

/**
 * 화면과 녹음 제어를 바꾸지 않고, 현재 연주의 음높이 결과 구독과 만료만 관리한다.
 * [onPublish]는 내보내는 결과를 빠짐없이 받는다. 상태 흐름은 빠르게 바뀐 값을 건너뛸 수 있어 음별 집계는 이쪽을 쓴다.
 */
internal class PracticePitchSession(
    private val recorder: AudioRecorder?,
    private val scope: CoroutineScope,
    private val onPublish: (PracticePitchFeedback) -> Unit = {},
    private val currentIndex: () -> Int,
) {
    private val output = MutableStateFlow(PracticePitchFeedback())
    val state = output.asStateFlow()
    private var collector: Job? = null
    private var expiry: Job? = null
    private var tracker: PracticePitchTracker? = null
    private var initial: CaptureBoundary? = null
    private var scoreStartMs = 0.0

    /** [section]은 평가 구간의 연주 순서 범위다. 대금 낮은 옥타브 보정의 하한을 그 구간 최저음으로 정한다(S15P31E102-892). */
    fun start(song: Song, bpm: Int, elapsedMs: Double, section: IntRange? = null) {
        stop()
        val source = recorder?.pitchReadings ?: return
        val timing = song.score.timing ?: return
        val boundary = recorder.captureBoundary ?: return
        if (bpm <= 0 || timing.ticksPerBeat <= 0 || !timing.hwangHz.isFinite() || timing.hwangHz <= 0) return
        initial = boundary
        scoreStartMs = elapsedMs
        val next = PracticePitchTracker(song.timeline, timing.hwangHz, 60_000.0 / bpm / timing.ticksPerBeat,
            evaluationLowOctaveFloorHz(song, section))
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
        onPublish(value)
        expiry?.cancel()
        val expiresAt = listOfNotNull(value.current, value.previous).minOfOrNull { it.capturedEndNanos + PitchFeedbackLifetimeNanos } ?: return
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

/** 평가 구간([section], null이면 곡 전체)의 음과 곡 악기로 정한 대금 낮은 옥타브 하한. 대금이 아니면 null이다. */
internal fun evaluationLowOctaveFloorHz(song: Song, section: IntRange?): Double? {
    val hwangHz = song.score.timing?.hwangHz ?: return null
    val events = section?.let { range -> song.timeline.filter { it.index in range } } ?: song.timeline
    return lowOctaveFloorHz(events, hwangHz, song.score.source?.instrument)
}

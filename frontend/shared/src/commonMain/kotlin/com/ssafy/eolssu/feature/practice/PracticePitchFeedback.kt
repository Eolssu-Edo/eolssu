package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchReading
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

enum class PitchDirection { Low, Match, High }

private val yulNames = listOf("황", "대", "태", "협", "고", "중", "유", "임", "이", "남", "무", "응")

private fun targetHz(note: ScoreNote, hwangHz: Double): Double? =
    (note as? ScoreNote.SourcePitch)?.let { hwangHz * 2.0.pow((yulNames.indexOf(it.yul) + 12 * it.octave) / 12.0) }

/** 곡에서 가장 낮은 목표 음(Hz). 음이 없으면 0이라 아래 보류(D-5)가 꺼진다. */
internal fun lowestTargetHz(events: List<ScoreEvent>, hwangHz: Double): Double =
    events.mapNotNull { targetHz(it.note, hwangHz) }.minOrNull() ?: 0.0

/**
 * [lowestHz]는 곡의 가장 낮은 목표 음이다. 그보다 200센트 넘게 낮으면 악기가 낼 수 없는 높이라 보류한다(S15P31E102-556 D-5).
 * 목표와 600센트 넘게 떨어진 값은 보류한다(555). 다만 높이 추정이 주기 배수 오인식을 먼저 고치므로
 * 1,200센트 ±100 안의 차이는 역취·평취 같은 실제 실수로 보고 낮음·높음으로 안내한다(556 D-4).
 */
internal fun comparePitch(note: ScoreNote, hwangHz: Double, heardHz: Double?, lowestHz: Double = 0.0): PitchDirection? {
    if (!hwangHz.isFinite() || hwangHz <= 0 || heardHz == null || !heardHz.isFinite() || heardHz <= 0) return null
    val target = targetHz(note, hwangHz) ?: return null
    if (lowestHz > 0 && 1200 * log2(heardHz / lowestHz) < -200) return null
    val cents = 1200 * log2(heardHz / target)
    return when {
        cents in -50.0..50.0 -> PitchDirection.Match
        cents in -600.0..600.0 || abs(abs(cents) - 1200) <= 100 -> if (cents < 0) PitchDirection.Low else PitchDirection.High
        else -> null
    }
}

/** 안내는 원본 이벤트 ID와 입력 시각을 끝까지 유지한다. */
data class PitchFeedback(
    val eventId: String,
    val targetName: String,
    val heardHz: Double?,
    val direction: PitchDirection?,
    val capturedEndNanos: Long,
    val rest: Boolean = false,
)

data class PracticePitchFeedback(val current: PitchFeedback? = null, val previous: PitchFeedback? = null)

internal class PracticePitchTracker(
    private val events: List<ScoreEvent>,
    private val hwangHz: Double,
    private val msPerTick: Double,
) {
    private var boundary: CaptureBoundary? = null
    private var scoreStartMs = 0.0
    private var lastSequence = -1L
    private var latest = PracticePitchFeedback()
    private val lowestHz = lowestTargetHz(events, hwangHz)

    fun anchor(value: CaptureBoundary, scoreMs: Double) {
        if (boundary?.recordingId != value.recordingId || boundary?.segmentId != value.segmentId) {
            latest = PracticePitchFeedback()
            lastSequence = -1
        }
        if (!value.timestampValid || !value.analysisAvailable) latest = PracticePitchFeedback()
        boundary = value
        scoreStartMs = scoreMs
    }

    fun positionAt(value: CaptureBoundary): Double? {
        val base = boundary ?: return null
        if (base.recordingId != value.recordingId || base.segmentId != value.segmentId || base.sampleRate != value.sampleRate) return null
        return scoreStartMs + (value.samplePosition - base.samplePosition) * 1000.0 / base.sampleRate
    }

    fun accept(reading: PitchReading, currentIndex: Int, nowNanos: Long): PracticePitchFeedback {
        expire(currentIndex, nowNanos)
        val base = boundary ?: return latest
        if (!base.timestampValid || !base.analysisAvailable || reading.recordingId != base.recordingId || reading.segmentId != base.segmentId ||
            reading.sampleRate != base.sampleRate || reading.sequence <= lastSequence || reading.startSample < base.samplePosition) return latest
        if (lastSequence >= 0 && reading.sequence != lastSequence + 1) latest = PracticePitchFeedback()
        lastSequence = reading.sequence
        if (!reading.analysisAvailable) { latest = PracticePitchFeedback(); return latest }
        if (nowNanos < reading.capturedEndNanos || nowNanos - reading.capturedEndNanos >= 300_000_000L) return latest
        val start = scoreStartMs + (reading.startSample - base.samplePosition) * 1000.0 / base.sampleRate
        val end = scoreStartMs + (reading.endSample - base.samplePosition) * 1000.0 / base.sampleRate
        val index = events.indexOfLast { (it.startTick ?: Long.MAX_VALUE) * msPerTick <= start }
        val event = events.getOrNull(index) ?: return latest
        val eventEnd = ((event.startTick ?: return latest) + (event.durationTick ?: return latest)) * msPerTick
        if (end > eventEnd + 0.000001 || index !in (currentIndex - 1)..currentIndex) return latest
        val pitch = event.note as? ScoreNote.SourcePitch
        val feedback = PitchFeedback(event.id, pitch?.hangul ?: event.note.symbol, reading.frequencyHz.takeUnless { event.note == ScoreNote.Rest },
            if (event.sigimsae.isEmpty()) comparePitch(event.note, hwangHz, reading.frequencyHz, lowestHz) else null,
            reading.capturedEndNanos, event.note == ScoreNote.Rest)
        latest = if (index == currentIndex) latest.copy(current = feedback) else latest.copy(previous = feedback)
        return latest
    }

    fun expire(currentIndex: Int, nowNanos: Long): PracticePitchFeedback {
        val values = listOfNotNull(latest.current, latest.previous)
            .filter { nowNanos >= it.capturedEndNanos && nowNanos - it.capturedEndNanos < 300_000_000L }
        latest = PracticePitchFeedback(values.firstOrNull { it.eventId == events.getOrNull(currentIndex)?.id },
            values.firstOrNull { it.eventId == events.getOrNull(currentIndex - 1)?.id })
        return latest
    }
}

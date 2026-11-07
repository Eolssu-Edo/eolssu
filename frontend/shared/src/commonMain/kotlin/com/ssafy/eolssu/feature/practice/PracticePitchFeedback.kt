package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchReading
import kotlin.math.log2
import kotlin.math.pow

enum class PitchDirection { Low, Match, High }

internal fun comparePitch(note: ScoreNote, hwangHz: Double, heardHz: Double?): PitchDirection? {
    if (note !is ScoreNote.SourcePitch || !hwangHz.isFinite() || hwangHz <= 0 ||
        heardHz == null || !heardHz.isFinite() || heardHz <= 0) return null
    val names = listOf("황", "대", "태", "협", "고", "중", "유", "임", "이", "남", "무", "응")
    val semitones = names.indexOf(note.yul) + 12 * note.octave
    val target = hwangHz * 2.0.pow(semitones / 12.0)
    val cents = 1200 * log2(heardHz / target)
    // 반 옥타브 넘게 떨어진 값은 판단을 보류한다. YIN이 음 경계에서 기본음의 1/3·1/5로 잡는 값과 말소리를
    // 낮음·높음으로 안내하지 않기 위해서다. 한 옥타브 틀리게 분 음도 보류된다(S15P31E102-555).
    return when {
        cents < -600 || cents > 600 -> null
        cents < -50 -> PitchDirection.Low
        cents > 50 -> PitchDirection.High
        else -> PitchDirection.Match
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
            if (event.sigimsae.isEmpty()) comparePitch(event.note, hwangHz, reading.frequencyHz) else null,
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

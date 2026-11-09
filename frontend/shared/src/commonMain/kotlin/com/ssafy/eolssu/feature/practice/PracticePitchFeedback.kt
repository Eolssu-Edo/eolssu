package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreEvent
import com.ssafy.eolssu.core.audio.CaptureBoundary
import com.ssafy.eolssu.core.audio.PitchReading
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

private val yulNames = listOf("황", "대", "태", "협", "고", "중", "유", "임", "이", "남", "무", "응")

/** 원본 악보 음의 목표 높이(Hz). 율명·옥타브가 없는 음이면 null. */
internal fun targetHz(note: ScoreNote, hwangHz: Double): Double? =
    (note as? ScoreNote.SourcePitch)?.let { pitch ->
        yulNames.indexOf(pitch.yul).takeIf { it >= 0 }?.let { hwangHz * 2.0.pow((it + 12 * pitch.octave) / 12.0) }
    }

/** 곡에 나오는 서로 다른 목표 음(Hz)을 낮은 순으로. */
internal fun songTargetHz(events: List<ScoreEvent>, hwangHz: Double): List<Double> =
    events.mapNotNull { targetHz(it.note, hwangHz) }.distinct().sorted()

private fun cents(from: Double, to: Double) = 1200 * log2(to / from)

/**
 * 맞는 음을 불었는지 본다(S15P31E102-566). true 맞음, false 틀림, null 보류다. 음정이 몇 센트 어긋났는지가 아니라
 * 다른 음을 불었는지를 묻는다. 조율 보정이 없으므로 서버(조율을 뺀 뒤 ±50센트)와 어긋날 수 있다(D-0).
 *
 * - 맞음: 곡에서 목표 바로 위·아래 음까지 거리의 절반 안이다. 각 쪽은 100센트를 넘지 않고, 이웃이 없으면 100센트다(D-1·D-2).
 * - 보류: 곡의 가장 낮은 음보다 200센트 넘게 낮으면 악기가 낼 수 없는 높이다(556 D-5). 600센트를 넘고 1,200센트 ±100 밖이면
 *   주기 배수 오인식·말소리일 수 있다(555).
 * - 틀림: 그 밖. 옥타브 띠는 높이 추정이 배수 오인식을 먼저 고치므로 실제 실수로 본다(D-3).
 */
internal fun comparePitch(note: ScoreNote, hwangHz: Double, heardHz: Double?, songHz: List<Double> = emptyList()): Boolean? {
    if (!hwangHz.isFinite() || hwangHz <= 0 || heardHz == null || !heardHz.isFinite() || heardHz <= 0) return null
    val target = targetHz(note, hwangHz) ?: return null
    if (songHz.isNotEmpty() && cents(songHz.first(), heardHz) < -200) return null
    val off = cents(target, heardHz)
    if (abs(off) > 600 && abs(abs(off) - 1200) > 100) return null
    // 같은 음이 부동소수 오차로 목록에 남아도 이웃으로 치지 않게 1센트 여유를 둔다. 상한 100센트는 곡에 없는 음을
    // 불었을 때 넓은 범위에 들어가 맞음이 되는 것을 막는다(D-1 상한, 2026-11-08).
    val up = minOf(100.0, songHz.firstOrNull { cents(target, it) > 1 }?.let { cents(target, it) / 2 } ?: 100.0)
    val down = minOf(100.0, songHz.lastOrNull { cents(target, it) < -1 }?.let { -cents(target, it) / 2 } ?: 100.0)
    return off in -down..up
}

/** 안내는 원본 이벤트 ID와 입력 시각을 끝까지 유지한다. [correct]는 [comparePitch]의 결과로 null이면 보류다. */
data class PitchFeedback(
    val eventId: String,
    val targetName: String,
    val heardHz: Double?,
    val correct: Boolean?,
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
    private val songHz = songTargetHz(events, hwangHz)

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
            if (event.sigimsae.isEmpty()) comparePitch(event.note, hwangHz, reading.frequencyHz, songHz) else null,
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

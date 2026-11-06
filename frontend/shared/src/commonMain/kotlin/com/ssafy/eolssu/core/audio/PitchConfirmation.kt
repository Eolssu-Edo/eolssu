package com.ssafy.eolssu.core.audio

import kotlin.math.abs
import kotlin.math.ln

/** 연속 두 입력의 차이가 반음 이내일 때만 현재 음높이를 전달한다. */
internal class PitchConfirmation {
    private var previous: PitchReading? = null

    fun apply(reading: PitchReading): PitchReading {
        val before = previous
        val current = reading.rawFrequencyHz
        val prior = before?.rawFrequencyHz
        val confirmed = reading.analysisAvailable && current != null && prior != null && before.analysisAvailable &&
            before.recordingId == reading.recordingId && before.segmentId == reading.segmentId &&
            before.sequence + 1 == reading.sequence && before.sampleRate == reading.sampleRate &&
            reading.startSample > before.startSample &&
            abs(1200 * ln(current / prior) / ln(2.0)) <= 100
        previous = reading.takeIf { it.analysisAvailable && current != null }
        return reading.copy(frequencyHz = current.takeIf { confirmed })
    }
}

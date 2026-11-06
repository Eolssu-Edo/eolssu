package com.ssafy.eolssu.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PitchConfirmationTest {
    private fun reading(sequence: Long, hz: Double?, segment: Long = 1, available: Boolean = true) =
        PitchReading(1, segment, sequence, sequence * 882, sequence * 882 + 2048, 44100, hz, sequence * 20_000_000,
            normalizedDifference = 0.01, analysisAvailable = available)

    @Test fun confirmsTwoSimilarReadingsAndKeepsRawFrequency() {
        val filter = PitchConfirmation()
        val first = filter.apply(reading(0, 800.0))
        assertNull(first.frequencyHz)
        assertEquals(800.0, first.rawFrequencyHz)
        assertEquals(802.0, filter.apply(reading(1, 802.0)).frequencyHz)
        val spike = filter.apply(reading(2, 401.0))
        assertNull(spike.frequencyHz)
        assertEquals(401.0, spike.rawFrequencyHz)
        assertEquals(402.0, filter.apply(reading(3, 402.0)).frequencyHz)
    }

    @Test fun doesNotConfirmAcrossSilenceUnavailableDataSegmentsOrMissingSequence() {
        for (boundary in listOf(reading(1, null), reading(1, 800.0, available = false),
            reading(1, 800.0, segment = 2), reading(2, 800.0))) {
            val filter = PitchConfirmation()
            filter.apply(reading(0, 800.0))
            assertNull(filter.apply(boundary).frequencyHz)
        }
        val filter = PitchConfirmation()
        filter.apply(reading(0, 800.0))
        filter.apply(reading(1, null))
        assertNull(filter.apply(reading(2, 800.0)).frequencyHz)
        assertEquals(801.0, filter.apply(reading(3, 801.0)).frequencyHz)
    }
}

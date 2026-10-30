package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ScoreErrorKindTest {
    @Test
    fun serverVerdictsKeepTheirMeaning() {
        assertNull(ScoreErrorKind.fromVerdict("CORRECT"))
        assertEquals(ScoreErrorKind.Pitch, ScoreErrorKind.fromVerdict("PITCH"))
        assertEquals(ScoreErrorKind.Rhythm, ScoreErrorKind.fromVerdict("TIMING"))
        assertEquals(ScoreErrorKind.Missed, ScoreErrorKind.fromVerdict("MISSED"))
    }

    @Test
    fun unsupportedVerdictsCannotBecomeCorrectNotes() {
        listOf("", "UNKNOWN", "DURATION", "missed").forEach { verdict ->
            assertFailsWith<IllegalArgumentException> { ScoreErrorKind.fromVerdict(verdict) }
        }
    }
}

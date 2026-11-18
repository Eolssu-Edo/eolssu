package com.ssafy.eolssu.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreErrorKindTest {
    @Test
    fun serverVerdictsKeepTheirMeaning() {
        assertNull(ScoreErrorKind.fromVerdict("CORRECT"))
        assertEquals(ScoreErrorKind.Pitch, ScoreErrorKind.fromVerdict("PITCH"))
        assertEquals(ScoreErrorKind.Rhythm, ScoreErrorKind.fromVerdict("TIMING"))
        assertEquals(ScoreErrorKind.Missed, ScoreErrorKind.fromVerdict("MISSED"))
        assertEquals(ScoreErrorKind.Length, ScoreErrorKind.fromVerdict("LENGTH"))
    }

    @Test
    fun unknownVerdictsAreSkippedInsteadOfBreakingTheResult() {
        // 서버에 새 판정이 먼저 생겨도 결과 화면이 열려야 한다. 그 음만 표시하지 않는다(S15P31E102-912).
        listOf("", "UNKNOWN", "DURATION", "missed").forEach { verdict ->
            assertNull(ScoreErrorKind.fromVerdict(verdict))
        }
    }
}

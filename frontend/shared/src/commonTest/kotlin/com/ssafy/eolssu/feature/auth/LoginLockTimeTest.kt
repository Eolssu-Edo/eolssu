package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.KoreanDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginLockTimeTest {

    // 2026-11-02T05:30:00Z = 한국 14:30
    private val at1430 = 1_793_597_400_000L

    @Test
    fun retryTimeIsShownInKoreanTime() {
        val time = lockRetryTime(at1430)
        assertEquals(KoreanDateTime(2026, 11, 2, 14, 30), time)
        assertEquals("14:30", clockLabel(time!!))
    }

    @Test
    fun leftoverSecondsRoundUpToNextMinute() {
        assertEquals("14:31", clockLabel(lockRetryTime(at1430 + 1_000L)!!))
        assertEquals("14:31", clockLabel(lockRetryTime(at1430 + 59_999L)!!))
    }

    @Test
    fun clockLabelPadsToTwoDigits() {
        assertEquals("09:05", clockLabel(KoreanDateTime(2026, 11, 2, 9, 5)))
        assertEquals("00:00", clockLabel(lockRetryTime(at1430 + 9 * 3_600_000L + 30 * 60_000L)!!), "자정을 넘기면 다음 날 0시")
    }

    @Test
    fun missingRetryTimeKeepsGeneralMessage() {
        assertNull(lockRetryTime(null))
    }
}

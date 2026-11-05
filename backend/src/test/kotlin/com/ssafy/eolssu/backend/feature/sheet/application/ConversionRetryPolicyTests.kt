package com.ssafy.eolssu.backend.feature.sheet.application

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ConversionRetryPolicyTests {
    @Test
    fun `engine and permanent input failures never retry`() {
        for (cause in listOf("ENGINE_ERROR", "NO_SCORE_FOUND", "UNSUPPORTED_FILE")) {
            assertFalse(ConversionRetryPolicy.allowed("FAILED", cause, 1, 0))
        }
    }

    @Test
    fun `timeout has its own budget across mixed failures`() {
        assertTrue(ConversionRetryPolicy.allowed("FAILED", "TIMEOUT", 1, 1))
        assertTrue(ConversionRetryPolicy.allowed("FAILED", "SERVER_ERROR", 2, 1))
        assertFalse(ConversionRetryPolicy.allowed("FAILED", "TIMEOUT", 3, 2))
    }

    @Test
    fun `only failed conversions within the total attempt budget retry`() {
        assertTrue(ConversionRetryPolicy.allowed("FAILED", "SERVER_ERROR", 3, 0))
        assertFalse(ConversionRetryPolicy.allowed("FAILED", "SERVER_ERROR", 4, 0))
        for (status in listOf("QUEUED", "RUNNING", "DONE")) {
            assertFalse(ConversionRetryPolicy.allowed(status, "SERVER_ERROR", 1, 0))
        }
    }
}

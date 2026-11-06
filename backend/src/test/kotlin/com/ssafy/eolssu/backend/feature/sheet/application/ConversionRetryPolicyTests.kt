package com.ssafy.eolssu.backend.feature.sheet.application

import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class ConversionRetryPolicyTests {
    @Test
    fun `discarded source cannot be retried regardless of failure or attempt`() {
        for (status in listOf("QUEUED", "RUNNING", "DONE", "FAILED"))
            for (cause in listOf("TIMEOUT", "SERVER_ERROR", "ENGINE_ERROR", "NO_SCORE_FOUND", "UNSUPPORTED_FILE"))
                for (attempt in 1..4)
                    assertFalse(ConversionRetryPolicy.allowed(status, cause, attempt, 1))
    }
}

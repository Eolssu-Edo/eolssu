package com.ssafy.eolssu.backend.config

import java.time.Duration
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SheetAudioConfigTest {
    @Test
    fun `the abandoned job deadline follows the configured generation timeout`() {
        assertEquals(Duration.ofSeconds(400), staleRunningAfter(Duration.ofSeconds(100)))
        assertEquals(Duration.ofSeconds(500), staleRunningAfter(Duration.ofSeconds(200)))
    }
}

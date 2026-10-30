package com.ssafy.eolssu.backend.common.security

import com.ssafy.eolssu.backend.config.AndroidAppLinksProperties
import kotlin.test.*
import org.junit.jupiter.api.Test

class AndroidAppLinksPropertiesTests {
    @Test fun `rotation fingerprints support compact or colon notation without duplicate associations`() {
        val compact="ab".repeat(32)
        val colon=List(32){"AB"}.joinToString(":")
        val second=List(32){"CD"}.joinToString(":")
        assertEquals(listOf(colon,second),AndroidAppLinksProperties(listOf(compact,colon,second)).normalizedFingerprints)
        assertTrue(AndroidAppLinksProperties(listOf("", " ")).normalizedFingerprints.isEmpty())
    }
    @Test fun `invalid certificate values are rejected instead of publishing unverifiable links`() {
        for(value in listOf("placeholder","ab".repeat(31),"ab".repeat(33),"gg".repeat(32),"a:"+"ab".repeat(31)+"b")) {
            assertFailsWith<IllegalArgumentException>(value){AndroidAppLinksProperties(listOf(value))}
        }
    }
}

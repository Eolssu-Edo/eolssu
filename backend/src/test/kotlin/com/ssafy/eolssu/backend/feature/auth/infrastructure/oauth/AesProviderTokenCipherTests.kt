package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import kotlin.test.*
import org.junit.jupiter.api.Test

class AesProviderTokenCipherTests {
    private val cipher = AesProviderTokenCipher("apple-private-key-material")

    @Test fun `sealed tokens open only with the same key and never contain the original`() {
        val sealed = cipher.seal("apple-refresh-token")
        assertFalse(sealed.contains("apple-refresh-token"))
        assertNotEquals(sealed, cipher.seal("apple-refresh-token"))
        assertEquals("apple-refresh-token", cipher.open(sealed))
        assertNull(AesProviderTokenCipher("rotated-key").open(sealed))
    }

    @Test fun `tampered or malformed values open as null`() {
        val sealed = cipher.seal("apple-refresh-token")
        val middle = sealed.length - 2
        val flipped = sealed.substring(0, middle) + (if (sealed[middle] == 'A') 'B' else 'A') + sealed.substring(middle + 1)
        assertNull(cipher.open(flipped))
        assertNull(cipher.open("not-sealed"))
        assertNull(cipher.open(""))
    }
}

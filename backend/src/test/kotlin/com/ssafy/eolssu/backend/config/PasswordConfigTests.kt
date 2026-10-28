package com.ssafy.eolssu.backend.config

import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PasswordConfigTests {
    private val encoder = PasswordConfig().passwordEncoder()

    @Test
    fun `hashes with Argon2id behind an id prefix and verifies the longest allowed password`() {
        val password = "a1!" + "x".repeat(125)
        val hash = encoder.encode(password)!!
        assertTrue(hash.startsWith("{argon2}\$argon2id\$"), hash.take(20))
        assertTrue(encoder.matches(password, hash))
        assertTrue(!encoder.matches(password.dropLast(1), hash))
        assertNotEquals(hash, encoder.encode(password))
    }
}

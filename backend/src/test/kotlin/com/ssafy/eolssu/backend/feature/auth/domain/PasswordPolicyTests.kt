package com.ssafy.eolssu.backend.feature.auth.domain

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PasswordPolicyTests {
    @Test
    fun `accepts 8 to 128 characters with an English letter a digit and a special character`() {
        assertTrue(PasswordPolicy.isValid("Abcdef1!"))
        assertTrue(PasswordPolicy.isValid("a1!" + "x".repeat(125)))
        assertTrue(PasswordPolicy.isValid("연습a1!연습연습"))
    }

    @Test
    fun `rejects wrong length or a missing character class`() {
        for (password in listOf("Abcde1!", "a1!" + "x".repeat(126), "abcdefg!", "12345678!", "abcdefg1", "가나다라마1!!", "abcd 1234")) {
            assertFalse(PasswordPolicy.isValid(password), password)
        }
    }
}

package com.ssafy.eolssu.backend.feature.auth.domain

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EmailPolicyTests {
    @Test
    fun `accepts common addresses with dotted domains and a letter TLD`() {
        val local64 = "a".repeat(64)
        val maxLength = local64 + "@" + listOf("b".repeat(63), "c".repeat(63), "d".repeat(63), "e".repeat(63)).joinToString(".")
        for (email in listOf(
            "user@naver.com", "first.last+tag@sub.example.co.kr", "User.Name@Example.COM", "a_b%c-d@my-domain.io",
            "1234@daum.net", local64 + "@example.com", maxLength,
        )) {
            assertTrue(EmailPolicy.isValid(email), email)
        }
        check(maxLength.length == 320)
    }

    @Test
    fun `rejects addresses without a usable local part domain or TLD`() {
        for (email in listOf(
            "", "a", "a@b", "a@b.", "@b.com", "a@.com", "a..b@c.com", ".a@b.com", "a.@b.com", "a@b..com",
            "a b@c.com", "a@-b.com", "a@b-.com", "a@b.c", "a@b.c0m", "a@b.123", "a,b@c.com", "a@b@c.com",
            "a@b_c.com", " a@b.com", "a@b.com ", "a@b.com\n", "mémber@example.com", "user@도메인.com",
            "a".repeat(65) + "@example.com", "a@" + "b".repeat(64) + ".com",
            "a".repeat(64) + "@" + listOf("b".repeat(63), "c".repeat(63), "d".repeat(63), "e".repeat(61), "fg").joinToString("."),
        )) {
            assertFalse(EmailPolicy.isValid(email), email)
        }
    }
}

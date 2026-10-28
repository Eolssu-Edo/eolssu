package com.ssafy.eolssu.backend.feature.auth.infrastructure.security

import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.security.SecureRandom
import java.util.Base64
import org.junit.jupiter.api.Test

class EmailVerificationSecretsTests {
    @Test
    fun `stores an HMAC bound to the email purpose and full six digit code`() {
        val secrets = EmailVerificationSecrets(ByteArray(32) { it.toByte() })
        // Python 표준 hmac/hashlib로 독립 계산한 고정 벡터.
        assertEquals(
            "32c4068e0c200f59b9fd7d230c509bfec606d1ec322964e57b57338c904468f9",
            secrets.hashCode("learner@example.com", VerificationPurpose.SIGN_UP, "012345"),
        )
    }

    @Test
    fun `generates zero padded six digit codes and 256 bit URL safe tokens`() {
        val random = object : SecureRandom() {
            override fun nextInt(bound: Int): Int {
                assertEquals(1_000_000, bound)
                return 12
            }
            override fun nextBytes(bytes: ByteArray) {
                bytes.indices.forEach { bytes[it] = it.toByte() }
            }
        }
        val secrets = EmailVerificationSecrets(ByteArray(32), random)
        assertEquals("000012", secrets.generateCode())
        val token = secrets.generateToken()
        assertTrue(token.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals(32, Base64.getUrlDecoder().decode(token).size)
    }

    @Test
    fun `token hashes use SHA256 and code comparison rejects mismatches`() {
        val secrets = EmailVerificationSecrets(ByteArray(32) { it.toByte() })
        assertEquals("38d9790f517c7fa46073aca90821d1cc5b5bebc05b815eba92bda097051ee8c9", secrets.hashToken("test-verification-token"))
        val expected = "32c4068e0c200f59b9fd7d230c509bfec606d1ec322964e57b57338c904468f9"
        assertTrue(secrets.matchesCode("learner@example.com", VerificationPurpose.SIGN_UP, "012345", expected))
        assertFalse(secrets.matchesCode("learner@example.com", VerificationPurpose.SIGN_UP, "112345", expected))
    }

    @Test
    fun `rejects HMAC secrets shorter than 256 bits`() {
        assertFailsWith<IllegalArgumentException> { EmailVerificationSecrets(ByteArray(31)) }
        val failure = assertFailsWith<IllegalArgumentException> { EmailVerificationSecrets(ByteArray(0)) }
        assertTrue(failure.message.orEmpty().contains("AUTH_CODE_HMAC_SECRET"))
    }
}

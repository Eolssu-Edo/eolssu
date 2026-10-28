package com.ssafy.eolssu.backend.feature.auth.infrastructure.security

import com.ssafy.eolssu.backend.feature.auth.domain.VerificationPurpose
import com.ssafy.eolssu.backend.feature.auth.domain.VerificationSecrets
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class EmailVerificationSecrets(secret: ByteArray, private val random: SecureRandom = SecureRandom()) : VerificationSecrets {
    private val key = SecretKeySpec(secret.copyOf(), "HmacSHA256")

    init {
        require(secret.size >= 32) { "AUTH_CODE_HMAC_SECRET must contain at least 32 decoded bytes" }
    }

    override fun generateCode(): String = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))

    override fun matchesCode(email: String, purpose: VerificationPurpose, code: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hashCode(email, purpose, code).toByteArray(Charsets.US_ASCII), expectedHash.toByteArray(Charsets.US_ASCII))

    override fun generateToken(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also(random::nextBytes))

    override fun hashToken(token: String): String = HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))

    override fun hashCode(email: String, purpose: VerificationPurpose, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return HexFormat.of().formatHex(mac.doFinal("$email\n${purpose.name}\n$code".toByteArray(Charsets.UTF_8)))
    }
}

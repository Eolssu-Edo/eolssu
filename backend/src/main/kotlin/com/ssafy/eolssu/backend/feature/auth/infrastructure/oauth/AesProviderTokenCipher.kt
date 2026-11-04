package com.ssafy.eolssu.backend.feature.auth.infrastructure.oauth

import com.ssafy.eolssu.backend.feature.auth.domain.ProviderTokenCipher
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 제공자 토큰(Apple refresh token)을 AES-256-GCM으로 봉인한다. 키는 [keyMaterial](Apple .p8 개인키)에서 유도해 별도 비밀값을 두지 않는다.
 * DB만 유출돼서는 토큰을 꺼낼 수 없다. 개인키를 바꾸면 이전 값은 열리지 않고(null), 해지는 건너뛴다.
 * 형식: `v1.` + base64url(12바이트 IV + 암호문·태그).
 */
class AesProviderTokenCipher(keyMaterial: String) : ProviderTokenCipher {
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("eolssu-provider-token:$keyMaterial".toByteArray(UTF_8)), "AES")
    private val random = SecureRandom()

    override fun seal(token: String): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(iv + cipher.doFinal(token.toByteArray(UTF_8)))
    }

    override fun open(sealed: String): String? = runCatching {
        if (!sealed.startsWith(PREFIX)) return null
        val bytes = Base64.getUrlDecoder().decode(sealed.removePrefix(PREFIX))
        if (bytes.size <= 12 + 16) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12)) }
        String(cipher.doFinal(bytes, 12, bytes.size - 12), UTF_8)
    }.getOrNull()

    private companion object { const val PREFIX = "v1." }
}

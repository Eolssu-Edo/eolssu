package com.ssafy.eolssu.backend.config

import java.util.Locale
import org.springframework.boot.context.properties.ConfigurationProperties

/** 각 패키지의 실제 APK 공개 인증서만 설정한다. 미설정 패키지는 발행하지 않는다. */
@ConfigurationProperties("eolssu.auth.android-app-links")
class AndroidAppLinksProperties(
    val sha256CertFingerprints: List<String> = emptyList(),
    val qaSha256CertFingerprints: List<String> = emptyList(),
) {
    val normalizedFingerprints = normalize(sha256CertFingerprints, "AUTH_ANDROID_APP_SHA256_FINGERPRINTS")
    val normalizedQaFingerprints = normalize(qaSha256CertFingerprints, "AUTH_ANDROID_QA_APP_SHA256_FINGERPRINTS")

    private fun normalize(values: List<String>, setting: String) = values.filter { it.isNotBlank() }.map { raw ->
        val value = raw.trim()
        require(Regex("[0-9a-fA-F]{64}").matches(value) || Regex("(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}").matches(value)) {
            "$setting must contain SHA-256 certificate fingerprints"
        }
        value.replace(":", "").uppercase(Locale.ROOT).chunked(2).joinToString(":")
    }.distinct()
}

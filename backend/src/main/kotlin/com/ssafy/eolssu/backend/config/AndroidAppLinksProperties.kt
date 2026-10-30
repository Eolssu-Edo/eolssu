package com.ssafy.eolssu.backend.config

import java.util.Locale
import org.springframework.boot.context.properties.ConfigurationProperties

/** 실제 설치 APK의 공개 인증서 지문만 운영자가 설정한다. 비어 있으면 association을 발행하지 않는다. */
@ConfigurationProperties("eolssu.auth.android-app-links")
class AndroidAppLinksProperties(val sha256CertFingerprints: List<String> = emptyList()) {
    val normalizedFingerprints = sha256CertFingerprints.filter { it.isNotBlank() }.map { raw ->
        val value = raw.trim()
        require(Regex("[0-9a-fA-F]{64}").matches(value) || Regex("(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}").matches(value)) {
            "AUTH_ANDROID_APP_SHA256_FINGERPRINTS must contain SHA-256 certificate fingerprints"
        }
        value.replace(":", "").uppercase(Locale.ROOT).chunked(2).joinToString(":")
    }.distinct()
}

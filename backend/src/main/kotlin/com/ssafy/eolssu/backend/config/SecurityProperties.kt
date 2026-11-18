package com.ssafy.eolssu.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** allowedOrigins: 같은 사이트 밖에서 API를 부를 수 있는 개발용 주소(예: FE 개발 서버). 운영은 비워 둔다. */
@ConfigurationProperties("eolssu.security")
data class SecurityProperties(
    val allowedOrigins: List<String> = emptyList(),
    val jwtSecret: String = "",
    val nonExpiringUserIds: Set<Long> = emptySet(),
) {
    val normalizedAllowedOrigins: Set<String>
        get() = allowedOrigins.map { it.trim().trimEnd('/').lowercase() }.filter { it.isNotEmpty() }.toSet()
}

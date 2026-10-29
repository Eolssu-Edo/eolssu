package com.ssafy.eolssu.backend.feature.auth.infrastructure.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.ssafy.eolssu.backend.feature.auth.domain.AccessTokenSigner
import com.ssafy.eolssu.backend.feature.auth.domain.TokenIssuingUnavailable
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/** AccessTokenConfig의 검증과 같은 AUTH_JWT_SECRET(UTF-8 32바이트 이상)·HS256으로 서명한다. 키가 없으면 발급만 거절한다. */
class JwtAccessTokenSigner(secret: String) : AccessTokenSigner {
    private val encoder = secret.takeIf { it.isNotBlank() }?.let {
        val key = it.toByteArray(Charsets.UTF_8)
        require(key.size >= 32) { "AUTH_JWT_SECRET must contain at least 32 UTF-8 bytes" }
        NimbusJwtEncoder(ImmutableSecret(SecretKeySpec(key, "HmacSHA256")))
    }

    override fun signNonExpiring(userId: Long, issuedAt: Instant, sessionHash: String): String =
        encode(JwtClaimsSet.builder().subject(userId.toString()).issuedAt(issuedAt).claim("session_hash", sessionHash).build())

    override fun sign(userId: Long, issuedAt: Instant, expiresAt: Instant): String =
        encode(JwtClaimsSet.builder().subject(userId.toString()).issuedAt(issuedAt).expiresAt(expiresAt).build())

    private fun encode(claims: JwtClaimsSet): String {
        val jwtEncoder = encoder ?: throw TokenIssuingUnavailable()
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }
}

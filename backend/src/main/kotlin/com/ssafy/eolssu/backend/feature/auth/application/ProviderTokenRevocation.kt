package com.ssafy.eolssu.backend.feature.auth.application

import com.ssafy.eolssu.backend.feature.auth.domain.*
import org.slf4j.LoggerFactory

/**
 * 연결 해제·탈퇴 신청이 커밋된 뒤 제공자 토큰(Apple)을 해지한다(App Store 5.1.1(v)).
 * 해지 실패는 해제·탈퇴를 되돌리지 않는다. 토큰은 남겨 두고(탈퇴는 일괄 삭제가 지운다) 제공자·연결 ID만 기록한다. 토큰 값은 로그에 남기지 않는다.
 */
class ProviderTokenRevocation(
    private val repository: SocialAuthRepository,
    private val gateway: OAuthGateway,
    private val cipher: ProviderTokenCipher?,
) {
    private val log = LoggerFactory.getLogger(ProviderTokenRevocation::class.java)

    /** 이 회원의 연결에 남은 토큰을 모두 해지하고 지운다. */
    fun revokeAll(userId: Long) {
        for (stored in runCatching { repository.providerTokens(userId) }.getOrElse { emptyList() }) {
            if (revoke(stored.provider, stored.sealed, stored.linkId)) runCatching { repository.clearProviderToken(stored.linkId, stored.sealed) }
        }
    }

    /** 이미 지운 연결의 토큰을 해지한다. 성공하면 true. */
    fun revoke(provider: String, sealed: String, linkId: Long? = null): Boolean {
        val target = SocialProvider.entries.firstOrNull { it.name == provider } ?: return false
        val token = cipher?.open(sealed)
        if (token == null) {
            log.warn("Provider token could not be opened for revocation provider={} link={}", provider, linkId)
            return false
        }
        return try {
            gateway.revoke(target, token)
            true
        } catch (_: Exception) {
            log.warn("Provider token revocation failed provider={} link={}", provider, linkId)
            false
        }
    }
}

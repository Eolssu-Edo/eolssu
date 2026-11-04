package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

/** 일회용 증명값의 해시와 서버가 확인한 문맥. 토큰 원문은 저장하지 않는다. */
class OAuthProof(
    val hash: String,
    val kind: String,
    val provider: String,
    val purpose: String,
    val bindingHash: String,
    val userId: Long?,
    val redirectUri: String?,
    val subject: String?,
    val email: String?,
    val emailVerified: Boolean,
    val expiresAt: Instant,
    val consumedAt: Instant? = null,
    val flowStartedAt: Instant? = null,
    val socialAccountId: Long? = null,
    /** 가입·연결 티켓이 연결을 만들 때 넘길 봉인된 제공자 토큰(Apple). */
    val providerToken: String? = null,
)

interface OAuthProofRepository {
    fun insert(proof: OAuthProof)
    fun find(hash: String): OAuthProof?
    /** 같은 트랜잭션에서 회원 잠금 뒤 수행한다. 이미 소비되었거나 만료됐으면 false. */
    fun consume(hash: String, now: Instant): Boolean
    fun deleteExpired(now: Instant): Int
}

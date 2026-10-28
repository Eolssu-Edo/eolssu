package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Instant

enum class MemberStatus { ACTIVE, WITHDRAWAL_PENDING, DELETED }

/** 로그인 대상 회원. 이메일이 비는 DELETED는 이메일로 찾을 수 없다. */
class SignInMember(
    val id: Long,
    val passwordHash: String,
    val nickname: String,
    val instrumentId: Long,
    val notationMode: String,
    val status: MemberStatus,
)

interface SignInRepository {
    fun <T : Any> inTransaction(action: () -> T): T
    fun findMemberForUpdate(email: String): SignInMember?
    fun cancelWithdrawal(userId: Long, now: Instant)
    fun recordLogin(userId: Long, now: Instant)
}

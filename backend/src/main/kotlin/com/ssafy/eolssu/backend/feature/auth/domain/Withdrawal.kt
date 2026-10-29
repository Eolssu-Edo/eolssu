package com.ssafy.eolssu.backend.feature.auth.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** USR-05: 탈퇴 요청 뒤 30일이 지나고 처음 오는 04:00(Asia/Seoul) 일괄 처리에서 개인정보를 지운다. */
object WithdrawalPolicy {
    val RETENTION: Duration = Duration.ofDays(30)
    val PURGE_TIME: LocalTime = LocalTime.of(4, 0)
    val PURGE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    fun purgeAt(requestedAt: Instant): Instant {
        val earliest = requestedAt.plus(RETENTION).atZone(PURGE_ZONE)
        val sameDay = earliest.toLocalDate().atTime(PURGE_TIME).atZone(PURGE_ZONE)
        return (if (sameDay.isBefore(earliest)) sameDay.plusDays(1) else sameDay).toInstant()
    }
}

/** 회원 탈퇴(USR-05) 저장소. ACTIVE 회원만 대상이다. */
interface WithdrawalRepository {
    fun <T : Any> inTransaction(action: () -> T): T
    fun findActivePasswordHash(userId: Long): String?
    fun findActivePasswordHashForUpdate(userId: Long): String?
    fun markWithdrawalPending(userId: Long, requestedAt: Instant, purgeAt: Instant)
    /** 이 회원의 쓸 수 있는 리프레시 토큰을 모두 WITHDRAWN으로 폐기한다. */
    fun revokeAllRefreshTokens(userId: Long, now: Instant)
}

/** 탈퇴 일괄 삭제 저장소. 삭제 순서는 저장소 DB 스키마 계약 '탈퇴 일괄 삭제 대상'을 따른다. */
interface WithdrawalPurgeRepository {
    /** 삭제 예정 시각이 지난 탈퇴 대기 회원 ID를 afterUserId 다음부터 오름차순으로 limit개. */
    fun dueMembers(now: Instant, afterUserId: Long, limit: Int): List<Long>
    /** 이 회원 연습 세션의 녹음 원본 키. */
    fun recordingKeys(userId: Long): List<String>
    /** 한 트랜잭션으로 회원 데이터를 지우고 개인정보를 비운다. 그 사이 탈퇴를 취소했거나 이미 지웠으면 false. */
    fun purge(userId: Long, now: Instant): Boolean
}

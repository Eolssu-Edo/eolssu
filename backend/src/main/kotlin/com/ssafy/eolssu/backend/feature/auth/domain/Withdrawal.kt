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
    /**
     * 회원 행을 잠가 진행 중인 취소가 끝나기를 기다리고 삭제 대상을 재확인한다.
     * 대상이면 지울 저장소 객체 키(녹음 원본과 `sheet-sources/` 내 악보 원본, 없으면 빈 목록), 취소·삭제됐거나 예정 전이면 null.
     * 대상의 취소 기한은 이미 지났으므로 이후 로그인도 취소할 수 없다. 반환 전에 잠금을 해제한다.
     */
    fun preparePurge(userId: Long, now: Instant): List<String>?
    /** 한 트랜잭션으로 회원 데이터를 지우고 개인정보를 비운다. 재확인 시 삭제 대상이 아니면 false. */
    fun purge(userId: Long, now: Instant): Boolean
}

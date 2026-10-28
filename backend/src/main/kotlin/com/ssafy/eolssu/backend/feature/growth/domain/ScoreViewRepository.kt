package com.ssafy.eolssu.backend.feature.growth.domain

interface ScoreViewRepository {
    fun songExists(songId: Long): Boolean

    fun findByKey(userId: Long, idempotencyKey: String): ScoreViewSegment?

    /**
     * 다른 요청이 커밋한 행까지 보는 조회. 트랜잭션 안의 일반 조회는 처음 읽은 시점의 스냅샷만 보여서
     * (MySQL REPEATABLE READ) 중복 키로 저장이 막힌 뒤 그 행을 찾지 못할 수 있다.
     */
    fun findLatestByKey(userId: Long, idempotencyKey: String): ScoreViewSegment?

    /** 같은 회원·키의 구간이 이미 있으면 저장하지 않고 false를 돌려준다. */
    fun insertIfAbsent(segment: ScoreViewSegment): Boolean
}

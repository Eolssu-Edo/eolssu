package com.ssafy.eolssu.backend.feature.growth.domain

interface ScoreViewRepository {
    fun songExists(songId: Long): Boolean

    fun findByKey(userId: Long, idempotencyKey: String): ScoreViewSegment?

    /** 같은 회원·키의 구간이 이미 있으면 저장하지 않고 false를 돌려준다. */
    fun insertIfAbsent(segment: ScoreViewSegment): Boolean
}

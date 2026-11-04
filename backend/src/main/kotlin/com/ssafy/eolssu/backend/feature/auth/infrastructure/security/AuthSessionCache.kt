package com.ssafy.eolssu.backend.feature.auth.infrastructure.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import java.time.Duration
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 466: 인증 필터가 요청마다 하던 회원 상태·로그인 세션 확인 결과를 [ttl] 동안 둔다.
 * refresh 폐기·회원 상태 변경이 커밋되면 그 회원의 항목을 지운다. 커밋 전에 지우면 동시 요청이 옛 값을 다시 채울 수 있다.
 * 커밋 직전에 시작한 조회가 지운 뒤 옛 값을 넣는 좁은 경우와 다른 서버의 캐시는 [ttl] 안에 바로잡힌다.
 */
class AuthSessionCache(ttl: Duration = Duration.ofSeconds(10), maxSize: Long = 10_000, ticker: Ticker = Ticker.systemTicker()) {
    private data class Key(val userId: Long, val sessionId: String?)

    private val entries: Cache<Key, Boolean> = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).ticker(ticker).build()

    fun isActive(userId: Long, sessionId: String?, load: () -> Boolean): Boolean = entries.get(Key(userId, sessionId)) { load() }

    /** 트랜잭션 안이면 커밋 뒤에, 밖이면 바로 그 회원의 모든 세션 항목을 지운다. */
    fun evictAfterCommit(userId: Long) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return evict(userId)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = evict(userId)
        })
    }

    private fun evict(userId: Long) {
        entries.asMap().keys.removeIf { it.userId == userId }
    }
}

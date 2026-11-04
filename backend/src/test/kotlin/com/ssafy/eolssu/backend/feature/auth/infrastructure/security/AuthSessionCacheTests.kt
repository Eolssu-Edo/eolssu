package com.ssafy.eolssu.backend.feature.auth.infrastructure.security

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.transaction.support.TransactionSynchronizationManager

class AuthSessionCacheTests {
    private var nanos = 0L
    private val cache = AuthSessionCache(Duration.ofSeconds(10), ticker = { nanos })
    private val loads = AtomicInteger()

    private fun check(userId: Long, sessionId: String?, active: Boolean = true) = cache.isActive(userId, sessionId) { loads.incrementAndGet(); active }

    @Test
    fun `the same member and session is looked up once until the entry expires`() {
        assertTrue(check(7, "s1"))
        assertTrue(check(7, "s1"))
        assertEquals(1, loads.get())
        check(7, "s2")
        check(7, null)
        assertEquals(3, loads.get(), "세션마다, 세션 없는 토큰도 따로 확인한다")

        nanos += Duration.ofSeconds(10).toNanos()
        check(7, "s1")
        assertEquals(4, loads.get())
    }

    @Test
    fun `evicting a member drops every session of that member only`() {
        check(7, "s1"); check(7, null); check(8, "s9")
        cache.evictAfterCommit(7)
        check(7, "s1"); check(7, null); check(8, "s9")
        assertEquals(5, loads.get())
    }

    @Test
    fun `inside a transaction the entry is dropped only after commit`() {
        check(7, "s1")
        TransactionSynchronizationManager.initSynchronization()
        try {
            cache.evictAfterCommit(7)
            assertTrue(check(7, "s1", active = false), "커밋 전에는 지우지 않아 캐시 값 그대로다")
            assertEquals(1, loads.get())
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
        assertFalse(check(7, "s1", active = false))
        assertEquals(2, loads.get())
    }
}

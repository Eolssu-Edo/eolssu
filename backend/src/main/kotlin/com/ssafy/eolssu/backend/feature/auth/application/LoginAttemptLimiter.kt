package com.ssafy.eolssu.backend.feature.auth.application

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * AUTH-01: 같은 이메일로 15분 안에 5번 실패하면 그때부터 15분 동안 잠근다.
 * 1차는 서버 한 대라 서버 메모리에 두고, 재시작하면 초기화된다(2026-10-27 결정).
 */
class LoginAttemptLimiter(private val maxEmails: Int = 10_000) {
    private class Attempts {
        val failures = ArrayDeque<Instant>()
        var lockedUntil: Instant? = null
    }

    private val attempts = ConcurrentHashMap<String, Attempts>()

    fun lockedUntil(email: String, now: Instant): Instant? =
        attempts[email]?.let { synchronized(it) { it.lockedUntil?.takeIf { until -> now.isBefore(until) } } }

    /** 실패를 기록하고, 이번 실패로 잠겼으면 풀리는 시각을 돌려준다. */
    fun recordFailure(email: String, now: Instant): Instant? {
        if (attempts.size >= maxEmails) prune(now)
        val state = attempts.computeIfAbsent(email) { Attempts() }
        synchronized(state) {
            state.failures.removeAll { !it.isAfter(now.minus(WINDOW)) }
            state.failures.addLast(now)
            if (state.failures.size < MAX_FAILURES) return null
            state.failures.clear()
            return now.plus(LOCK).also { state.lockedUntil = it }
        }
    }

    fun reset(email: String) {
        attempts.remove(email)
    }

    // 여러 이메일로 실패를 쌓아 메모리를 늘리는 경우를 막는다. 잠금도 실패 기록도 끝난 이메일만 지운다.
    private fun prune(now: Instant) {
        attempts.entries.removeIf { (_, state) ->
            synchronized(state) {
                state.lockedUntil?.isAfter(now) != true && state.failures.lastOrNull()?.isAfter(now.minus(WINDOW)) != true
            }
        }
    }

    private companion object {
        const val MAX_FAILURES = 5
        val WINDOW: Duration = Duration.ofMinutes(15)
        val LOCK: Duration = Duration.ofMinutes(15)
    }
}

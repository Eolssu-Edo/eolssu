package com.ssafy.eolssu.backend.feature.growth.domain

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 악보 화면이 앞에 보인 구간 하나. 회원의 연습 시간은 [countedSeconds]의 합이다.
 * 기준: docs/specs/practice-time.md
 */
data class ScoreViewSegment(
    val userId: Long,
    val songId: Long,
    val idempotencyKey: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val visibleSeconds: Int,
    val countedSeconds: Int,
) {
    /** `score_views`는 마이크로초까지 저장하므로 같은 본문인지 비교하기 전에 정밀도를 맞춘다. */
    fun truncatedToStoredPrecision() = copy(
        startedAt = startedAt.truncatedTo(ChronoUnit.MICROS),
        endedAt = endedAt.truncatedTo(ChronoUnit.MICROS),
    )

    fun sameContentAs(other: ScoreViewSegment) =
        songId == other.songId &&
            startedAt == other.startedAt &&
            endedAt == other.endedAt &&
            visibleSeconds == other.visibleSeconds &&
            countedSeconds == other.countedSeconds

    /** 저장할 수 없는 첫 이유. 초 단위 반올림 오차로 보인 시간은 1초까지 더 길어도 받는다. */
    fun violation(receivedAt: Instant): ScoreViewViolation? = when {
        !isValidKey(idempotencyKey) -> ScoreViewViolation.INVALID_KEY
        visibleSeconds < 0 || countedSeconds < 0 -> ScoreViewViolation.NEGATIVE_SECONDS
        countedSeconds > visibleSeconds -> ScoreViewViolation.COUNTED_EXCEEDS_VISIBLE
        startedAt !in STORABLE_TIMES || endedAt !in STORABLE_TIMES -> ScoreViewViolation.TIME_OUT_OF_RANGE
        endedAt < startedAt -> ScoreViewViolation.ENDED_BEFORE_STARTED
        visibleLongerThan(Duration.between(startedAt, endedAt)) -> ScoreViewViolation.VISIBLE_EXCEEDS_PERIOD
        visibleLongerThan(Duration.between(startedAt, receivedAt)) -> ScoreViewViolation.VISIBLE_EXCEEDS_ELAPSED
        else -> null
    }

    private fun visibleLongerThan(period: Duration) =
        visibleSeconds * 1000L > period.toMillis() + ROUNDING_ALLOWANCE_MILLIS

    private companion object {
        const val ROUNDING_ALLOWANCE_MILLIS = 1000L
        const val MAX_KEY_LENGTH = 100

        // MySQL DATETIME이 저장하는 범위. 밖의 값은 저장 오류(5xx)가 되어 앱이 끝없이 다시 보내므로 400으로 막는다.
        val STORABLE_TIMES = Instant.parse("1000-01-01T00:00:00Z")..Instant.parse("9999-12-31T23:59:59.999999Z")

        // idempotency_key 열은 ascii_bin이다. 공백·제어 문자는 받지 않는다.
        fun isValidKey(key: String) = key.length in 1..MAX_KEY_LENGTH && key.all { it in '!'..'~' }
    }
}

enum class ScoreViewViolation {
    INVALID_KEY,
    NEGATIVE_SECONDS,
    COUNTED_EXCEEDS_VISIBLE,
    TIME_OUT_OF_RANGE,
    ENDED_BEFORE_STARTED,
    VISIBLE_EXCEEDS_PERIOD,
    VISIBLE_EXCEEDS_ELAPSED,
}

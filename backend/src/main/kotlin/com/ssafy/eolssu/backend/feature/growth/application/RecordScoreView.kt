package com.ssafy.eolssu.backend.feature.growth.application

import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewRepository
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewSegment
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewViolation
import java.time.Clock

/** 악보 화면 체류 구간 기록(GRO-04). 같은 키는 한 번만 저장하고, 다시 오면 저장된 구간과 비교한다. */
class RecordScoreView(
    private val repository: ScoreViewRepository,
    private val clock: Clock,
) {
    fun record(segment: ScoreViewSegment): RecordScoreViewResult {
        val requested = segment.truncatedToStoredPrecision()
        requested.violation(clock.instant())?.let { return RecordScoreViewResult.Invalid(it) }

        repository.findByKey(requested.userId, requested.idempotencyKey)?.let { return compare(requested, it) }
        if (!repository.songExists(requested.songId)) return RecordScoreViewResult.SongNotFound
        if (repository.insertIfAbsent(requested)) return RecordScoreViewResult.Created(requested)

        // 같은 키의 다른 요청이 조회와 저장 사이에 먼저 저장했다.
        val stored = checkNotNull(repository.findByKey(requested.userId, requested.idempotencyKey)) {
            "score view key was reported as duplicate but no row was found"
        }
        return compare(requested, stored)
    }

    private fun compare(requested: ScoreViewSegment, stored: ScoreViewSegment) =
        if (stored.sameContentAs(requested)) RecordScoreViewResult.AlreadyRecorded(stored) else RecordScoreViewResult.Conflict
}

sealed interface RecordScoreViewResult {
    data class Created(val segment: ScoreViewSegment) : RecordScoreViewResult

    data class AlreadyRecorded(val segment: ScoreViewSegment) : RecordScoreViewResult

    data object Conflict : RecordScoreViewResult

    data class Invalid(val violation: ScoreViewViolation) : RecordScoreViewResult

    data object SongNotFound : RecordScoreViewResult
}

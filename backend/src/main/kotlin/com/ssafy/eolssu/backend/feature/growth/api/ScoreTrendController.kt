package com.ssafy.eolssu.backend.feature.growth.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.growth.application.GetScoreTrend
import com.ssafy.eolssu.backend.feature.growth.application.ScoreTrend
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreScope
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 연습 기록지 '점수 변화'(GRO-03). 계약은 docs/specs/growth-score-trend.md. */
@RestController
@RequestMapping("/api/users/me/growth")
@Profile("db | ops")
class ScoreTrendController(private val scoreTrend: GetScoreTrend) {
    // 형식이 틀린 값은 생략과 구분해 400으로 돌려준다. 곡 ID·개수는 앞자리 0 없는 양의 정수만 받는다.
    @GetMapping("/scores")
    fun scores(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) songId: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) limit: String?,
    ): ResponseEntity<*> {
        val song = songId?.let { it.positiveNumber()?.toLongOrNull() ?: return invalidRequest() }
        val scoreScope = scope?.let { raw -> ScoreScope.entries.firstOrNull { it.name == raw } ?: return invalidRequest() } ?: ScoreScope.FULL
        val count = limit?.let { it.positiveNumber()?.toIntOrNull()?.takeIf { n -> n <= MAX_LIMIT } ?: return invalidRequest() } ?: DEFAULT_LIMIT
        val trend = scoreTrend.trend(requireNotNull(jwt.subject).toLong(), song, scoreScope, count)
        return ResponseEntity.ok(GrowthResponse("OK", true, "최근 점수 변화 조회 성공", 200, trend.toResult()))
    }

    private fun String.positiveNumber() = takeIf { POSITIVE_NUMBER.matches(it) }

    private fun ScoreTrend.toResult() = ScoreTrendResult(
        songId?.toString(),
        scope.name,
        points.map { (play, columns) ->
            ScoreTrendItem(
                play.sessionId.toString(), play.startedAt, play.bpm, play.bpmUnit, play.startNoteIdx, play.endNoteIdx,
                columns?.start, columns?.end, play.scoreVersion, play.modelVersion,
                play.totalScore, play.pitchScore, play.timingScore, play.durationScore,
            )
        },
        comparable,
        songs.map { PlayedSongResult(it.songId.toString(), it.title, it.lastPlayedAt, it.fullPlayCount, it.sectionPlayCount) },
    )

    private fun invalidRequest() = ResponseEntity.status(ErrorCode.INVALID_REQUEST.status).body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))

    private companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 100
        val POSITIVE_NUMBER = Regex("[1-9][0-9]*")
    }
}

data class ScoreTrendResult(
    val songId: String?,
    val scope: String,
    val items: List<ScoreTrendItem>,
    val comparable: Boolean,
    val songs: List<PlayedSongResult>,
)

data class ScoreTrendItem(
    val sessionId: String,
    val startedAt: Instant,
    val bpm: Int,
    val bpmUnit: String,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val startColumnIdx: Int?,
    val endColumnIdx: Int?,
    val scoreVersion: Int,
    val modelVersion: String,
    val totalScore: Int,
    val pitchScore: Int,
    val timingScore: Int,
    val durationScore: Int,
)

data class PlayedSongResult(
    val songId: String,
    val title: String,
    val lastPlayedAt: Instant,
    val fullPlayCount: Long,
    val sectionPlayCount: Long,
)

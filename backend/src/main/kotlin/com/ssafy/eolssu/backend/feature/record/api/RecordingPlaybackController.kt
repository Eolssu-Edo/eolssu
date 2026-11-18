package com.ssafy.eolssu.backend.feature.record.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.record.application.GetRecordingPlayback
import com.ssafy.eolssu.backend.feature.record.application.PlaybackOutcome
import com.ssafy.eolssu.backend.feature.record.application.RecordingPlayback
import jakarta.validation.constraints.Positive
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/** 녹음 다시 듣기(REC-03). 기록이 아니라 세션의 녹음을 다루므로 기록 API와 나눴다. 계약은 `docs/specs/practice-records.md`. */
@RestController
@Profile("db | ops")
class RecordingPlaybackController(private val getPlayback: GetRecordingPlayback) {
    /** REC-03 `GET /practice-sessions/{sessionId}/recording`. 만료되면 앱이 다시 받아 같은 위치부터 잇는다. */
    @GetMapping("/api/practice-sessions/{sessionId}/recording")
    fun playback(@AuthenticationPrincipal jwt: Jwt, @PathVariable @Positive sessionId: Long): ResponseEntity<*> =
        when (val outcome = getPlayback.execute(requireNotNull(jwt.subject).toLong(), sessionId)) {
            PlaybackOutcome.NotFound -> error(ErrorCode.RESOURCE_NOT_FOUND)
            PlaybackOutcome.Unavailable -> error(ErrorCode.SERVICE_UNAVAILABLE)
            is PlaybackOutcome.Ready -> ResponseEntity.ok(
                SuccessResponse.of(HttpStatus.OK, "녹음 재생 주소 발급 성공", RecordingPlaybackResult.of(outcome.playback)),
            )
        }

    private fun error(code: ErrorCode) = ResponseEntity.status(code.status).body(ErrorResponse.of(code))
}

data class RecordingPlaybackResult(
    val sessionId: String,
    val playbackUrl: String,
    val urlExpiresAt: Instant,
    val durationMs: Int?,
    val contentType: String,
) {
    companion object {
        // 녹음 원본은 업로드 검사(PRA-02)를 통과한 MP4 컨테이너 AAC다.
        fun of(playback: RecordingPlayback) =
            RecordingPlaybackResult(playback.sessionId.toString(), playback.url, playback.expiresAt, playback.durationMs, "audio/mp4")
    }
}

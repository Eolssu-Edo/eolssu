package com.ssafy.eolssu.backend.feature.record.application

import com.ssafy.eolssu.backend.feature.record.domain.PracticeRecords
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory

/**
 * 녹음을 다시 듣는 서명 URL을 발급한다(REC-03). 본인 세션에 녹음이 있으면 저장 여부와 관계없이 준다.
 * 저장소에 파일이 실제로 있는지는 확인하지 않는다. URL은 녹음 접근 토큰이라 로그에 남기지 않는다.
 */
class GetRecordingPlayback(
    private val records: PracticeRecords,
    /** 저장소 키를 `ttl` 동안 읽는 서명 URL. 설정이 녹음 저장소(PRA-02)를 연결한다. */
    private val signedUrl: (key: String, ttl: Duration) -> String,
    private val ttl: Duration,
    private val clock: Clock,
) {
    fun execute(userId: Long, sessionId: Long): PlaybackOutcome {
        val recording = records.findRecording(sessionId, userId) ?: return PlaybackOutcome.NotFound
        // 서명 전 시각을 초 단위로 내려 알린다. 저장소가 서명할 때의 만료보다 늦지 않게 하려는 것이다.
        val expiresAt = clock.instant().truncatedTo(ChronoUnit.SECONDS).plus(ttl)
        val url = try {
            signedUrl(recording.key, ttl)
        } catch (e: RuntimeException) {
            log.warn("Recording playback URL for session {} is unavailable ({})", sessionId, e.javaClass.simpleName)
            return PlaybackOutcome.Unavailable
        }
        return PlaybackOutcome.Ready(RecordingPlayback(sessionId, url, expiresAt, recording.durationMs))
    }

    private companion object {
        val log = LoggerFactory.getLogger(GetRecordingPlayback::class.java)
    }
}

sealed interface PlaybackOutcome {
    data object NotFound : PlaybackOutcome

    /** 저장소가 설정되지 않았거나 주소를 만들지 못했다. */
    data object Unavailable : PlaybackOutcome

    data class Ready(val playback: RecordingPlayback) : PlaybackOutcome
}

data class RecordingPlayback(val sessionId: Long, val url: String, val expiresAt: Instant, val durationMs: Int?) {
    // 서명 URL이 로그·예외 메시지로 새지 않게 한다.
    override fun toString() = "RecordingPlayback(sessionId=$sessionId, expiresAt=$expiresAt, durationMs=$durationMs)"
}

package com.ssafy.eolssu.backend.feature.practice.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * 저장하지 않은 녹음의 보관 기간. 기준은 업로드 성공 시각(`ended_at`)이다.
 * 저장한 기록의 녹음은 회원이 지우거나 탈퇴할 때까지 보관한다(S15P31E102-375).
 */
val UNSAVED_RECORDING_RETENTION: Duration = Duration.ofDays(30)

/** 정리할 세션과 그 원본 키. */
data class ExpiredRecording(val sessionId: Long, val key: String)

interface UnsavedRecordingRepository {
    /** 저장하지 않았고 `ended_at`이 cutoff(UTC)보다 이른 녹음. 세션 ID 오름차순으로 afterSessionId 다음부터 limit개. */
    fun findExpired(cutoff: LocalDateTime, afterSessionId: Long, limit: Int): List<ExpiredRecording>

    /** 아직 저장하지 않았고 키와 cutoff 조건이 그대로일 때만 `recording_url`을 비운다. 비웠으면 true. */
    fun clear(recording: ExpiredRecording, cutoff: LocalDateTime): Boolean

    /** `recording_url`이 아직 비어 있을 때만 키를 되돌린다. 그사이 저장됐어도 되돌려 녹음을 보관한다. 되돌렸으면 true. */
    fun restore(recording: ExpiredRecording): Boolean
}

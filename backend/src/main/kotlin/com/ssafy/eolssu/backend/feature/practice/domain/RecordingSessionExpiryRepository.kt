package com.ssafy.eolssu.backend.feature.practice.domain

import java.time.Duration
import java.time.LocalDateTime

// 녹음 시작 후 업로드를 받는 시간: 최대 녹음 10분 + 업로드·재시도 여유 30분(PRA-02)
val RECORDING_UPLOAD_WINDOW: Duration = Duration.ofMinutes(40)

const val UPLOAD_TIMEOUT = "UPLOAD_TIMEOUT"

interface RecordingSessionExpiryRepository {
    // started_at이 cutoff(UTC)보다 이른 RECORDING 세션을 FAILED로 바꾸고 바뀐 행 수를 돌려준다
    fun failRecordingStartedBefore(cutoff: LocalDateTime, failReason: String): Int
}

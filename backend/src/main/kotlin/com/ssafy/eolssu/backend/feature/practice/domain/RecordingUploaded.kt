package com.ssafy.eolssu.backend.feature.practice.domain

import com.ssafy.eolssu.backend.common.EvaluationTimingId

/**
 * 녹음 업로드(PRA-02)가 세션을 `PENDING`으로 바꾼 트랜잭션 안에서 발행한다.
 * 분석(PRA-03)은 커밋 뒤에만 시작하므로, 롤백된 업로드는 분석되지 않는다.
 */
data class RecordingUploaded(val sessionId: Long, val timingId: EvaluationTimingId? = null)

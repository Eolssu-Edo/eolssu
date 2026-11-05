package com.ssafy.eolssu.backend.feature.practice.domain

import java.math.BigDecimal

enum class NotePracticeTuning { NONE, FIRST_NOTE }

enum class NotePracticeSigimsaeMode { MAIN_PITCH, SKIP }

/** 기기가 한 음씩 연습을 판정할 때 쓰는 기준(PRA-06). 숫자는 설정에 적은 표기 그대로 내려주려고 BigDecimal로 둔다. */
data class NotePracticeCriteria(
    val criteriaVersion: String,
    val pitchToleranceCent: BigDecimal,
    val holdMs: Int,
    val minConfidence: BigDecimal,
    val minLevelDbfs: BigDecimal,
    val octaveMustMatch: Boolean,
    val tuning: NotePracticeTuning,
    val maxTuningOffsetCent: BigDecimal,
    val sigimsaeMode: NotePracticeSigimsaeMode,
    val hintAfterMs: Int,
) {
    init {
        require(criteriaVersion.isNotBlank()) { "criteriaVersion must not be blank" }
        require(pitchToleranceCent.signum() >= 0) { "pitchToleranceCent must not be negative" }
        require(holdMs >= 0) { "holdMs must not be negative" }
        require(minConfidence.signum() >= 0 && minConfidence <= BigDecimal.ONE) { "minConfidence must be between 0 and 1" }
        require(minLevelDbfs.signum() <= 0) { "minLevelDbfs must not be positive" }
        require(maxTuningOffsetCent.signum() >= 0) { "maxTuningOffsetCent must not be negative" }
        require(hintAfterMs >= 0) { "hintAfterMs must not be negative" }
    }
}

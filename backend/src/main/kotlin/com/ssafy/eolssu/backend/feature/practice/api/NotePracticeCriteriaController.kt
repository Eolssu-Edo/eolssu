package com.ssafy.eolssu.backend.feature.practice.api

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.common.response.SuccessResponse
import com.ssafy.eolssu.backend.feature.practice.application.NotePracticeCriteriaQuery
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeCriteria
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeSigimsaeMode
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeTuning
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED
import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val INSTRUMENT_ID = Regex("[1-9][0-9]{0,17}")

@RestController
@RequestMapping("/api/note-practice")
@Profile("db | ops")
class NotePracticeCriteriaController(private val query: NotePracticeCriteriaQuery) {
    @GetMapping("/criteria")
    fun criteria(@RequestParam(required = false) instrumentId: String?): ResponseEntity<*> {
        val id = instrumentId?.takeIf(INSTRUMENT_ID::matches)?.toLong()
            ?: return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST))
        val criteria = query.find(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
        return ResponseEntity.ok(SuccessResponse.of(HttpStatus.OK, "한 음씩 연습 판정 기준 조회 성공", criteria.toResult(id)))
    }

    private fun NotePracticeCriteria.toResult(instrumentId: Long) = NotePracticeCriteriaResult(
        instrumentId.toString(), criteriaVersion, pitchToleranceCent, holdMs, minConfidence, minLevelDbfs,
        octaveMustMatch, tuning, maxTuningOffsetCent, sigimsaeMode, hintAfterMs,
    )
}

data class NotePracticeCriteriaResult(
    @field:Schema(description = "악기 ID", requiredMode = REQUIRED) val instrumentId: String,
    @field:Schema(description = "판정 기준 버전. 값이 바뀌면 올린다", requiredMode = REQUIRED) val criteriaVersion: String,
    @field:Schema(description = "|측정 - 목표|가 이 값 이하면 맞은 음(cent)", requiredMode = REQUIRED) val pitchToleranceCent: BigDecimal,
    @field:Schema(description = "허용 범위 안에서 이어져야 하는 시간(ms)", requiredMode = REQUIRED) val holdMs: Int,
    @field:Schema(description = "이보다 낮은 프레임은 판정에서 뺀다(0~1)", requiredMode = REQUIRED) val minConfidence: BigDecimal,
    @field:Schema(description = "이보다 작으면 소리가 작다고 안내(dBFS)", requiredMode = REQUIRED) val minLevelDbfs: BigDecimal,
    @field:Schema(description = "true면 같은 율명의 다른 옥타브는 틀린 음", requiredMode = REQUIRED) val octaveMustMatch: Boolean,
    @field:Schema(description = "FIRST_NOTE면 첫 음에서 잰 편차를 뒤 음 목표에 더한다", requiredMode = REQUIRED) val tuning: NotePracticeTuning,
    @field:Schema(description = "편차가 이 값을 넘으면 조율 보정을 하지 않는다(cent)", requiredMode = REQUIRED) val maxTuningOffsetCent: BigDecimal,
    @field:Schema(description = "MAIN_PITCH는 주음만 판정, SKIP은 판정 없이 넘긴다", requiredMode = REQUIRED) val sigimsaeMode: NotePracticeSigimsaeMode,
    @field:Schema(description = "한 음에서 이 시간이 지나면 도움말을 권한다(ms)", requiredMode = REQUIRED) val hintAfterMs: Int,
)

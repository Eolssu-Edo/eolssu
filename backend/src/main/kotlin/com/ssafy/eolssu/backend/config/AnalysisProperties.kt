package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.analysis.domain.DEFAULT_ANALYSIS_TIME_LIMIT
import java.time.Duration
import java.time.temporal.ChronoUnit
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit

/**
 * 연주 분석(PRA-03) 설정.
 * aiBaseUrl: AI 서비스 주소. 비어 있으면 업로드 뒤 분석을 시작하지 않는다.
 * timeLimit: 분석 시도 제한 시간. 서버에서 처리 시간을 다시 잰 뒤 조정한다.
 * aiResponseTimeout: AI 응답 대기 시간. 기본은 제한 시간 + 5초. 제한 시간보다 길어야 느린 응답이 `SERVER_ERROR`가 아니라 `TIMEOUT`으로 끝난다.
 * 두 시간은 단위 없이 숫자만 쓰면 초로 읽는다.
 * workers·queueCapacity: 동시에 분석할 세션 수와 기다릴 수 있는 세션 수.
 */
@ConfigurationProperties("eolssu.analysis")
data class AnalysisProperties(
    val aiBaseUrl: String = "",
    @DurationUnit(ChronoUnit.SECONDS) val timeLimit: Duration = DEFAULT_ANALYSIS_TIME_LIMIT,
    @DurationUnit(ChronoUnit.SECONDS) val aiResponseTimeout: Duration = timeLimit.plusSeconds(5),
    val workers: Int = 2,
    val queueCapacity: Int = 50,
) {
    init {
        require(aiResponseTimeout > timeLimit) { "eolssu.analysis.ai-response-timeout must be longer than time-limit" }
        require(workers > 0 && queueCapacity >= 0) { "eolssu.analysis.workers must be positive and queue-capacity must not be negative" }
    }
}

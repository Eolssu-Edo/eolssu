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
 * shutdownWait: 앱 종료 때 이미 받은 분석(실행 중·대기열)을 기다리는 최대 시간. 기본은 제한 시간 + 5초다.
 * 종료 전에 시작된 시도는 이 시간 안에 모두 마감을 넘기므로 더 기다려도 저장되는 결과가 없다.
 * 이 기다림은 웹 서버의 요청 마무리(최대 `spring.lifecycle.timeout-per-shutdown-phase`) 뒤에 시작하므로,
 * 컨테이너 종료 대기(`stop_grace_period`)에서 그 시간을 뺀 것보다 짧아야 강제 종료 전에 끝난다(운영 250초 − 120초).
 * 세 시간은 단위 없이 숫자만 쓰면 초로 읽는다.
 * workers·queueCapacity: 동시에 분석할 세션 수와 기다릴 수 있는 세션 수.
 */
@ConfigurationProperties("eolssu.analysis")
data class AnalysisProperties(
    val aiBaseUrl: String = "",
    @DurationUnit(ChronoUnit.SECONDS) val timeLimit: Duration = DEFAULT_ANALYSIS_TIME_LIMIT,
    @DurationUnit(ChronoUnit.SECONDS) val aiResponseTimeout: Duration = timeLimit.plusSeconds(5),
    val workers: Int = 2,
    val queueCapacity: Int = 50,
    @DurationUnit(ChronoUnit.SECONDS) val shutdownWait: Duration = timeLimit.plusSeconds(5),
) {
    init {
        require(aiResponseTimeout > timeLimit) { "eolssu.analysis.ai-response-timeout must be longer than time-limit" }
        require(workers > 0 && queueCapacity >= 0) { "eolssu.analysis.workers must be positive and queue-capacity must not be negative" }
        require(!shutdownWait.isNegative) { "eolssu.analysis.shutdown-wait must not be negative" }
    }
}

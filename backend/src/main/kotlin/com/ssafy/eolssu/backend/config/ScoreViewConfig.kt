package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.growth.application.RecordScoreView
import com.ssafy.eolssu.backend.feature.growth.domain.ScoreViewRepository
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.MyBatisScoreViewRepository
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.ScoreViewMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// 체류 구간 기록(GRO-04). 서버 수신 시각 검사는 앱 공통 UTC 시계(TermConfig)를 쓴다.
@Configuration(proxyBeanMethods = false)
@Profile("db")
class ScoreViewConfig {
    @Bean
    fun scoreViewRepository(mapper: ScoreViewMapper): ScoreViewRepository = MyBatisScoreViewRepository(mapper)

    @Bean
    fun recordScoreView(repository: ScoreViewRepository, clock: Clock) = RecordScoreView(repository, clock)
}

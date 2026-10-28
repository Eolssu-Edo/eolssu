package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.growth.application.GetGrowthStats
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.MyBatisPracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence.PracticeStatsMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// 체류 기록 저장(GRO-04)은 별도 설정에서 연결할 예정이다. 같은 growth 기능이지만 담당 작업이 달라 파일을 나눴다.
@Configuration(proxyBeanMethods = false)
@Profile("db")
class GrowthStatsConfig {
    @Bean
    fun practiceStatsRepository(mapper: PracticeStatsMapper): PracticeStatsRepository = MyBatisPracticeStatsRepository(mapper)

    // 공유 Clock Bean(termClock)과 섞이지 않게 직접 넘긴다. 오늘(KST)은 연속 일수에만 쓴다.
    @Bean
    fun getGrowthStats(repository: PracticeStatsRepository) = GetGrowthStats(repository, Clock.systemUTC())
}

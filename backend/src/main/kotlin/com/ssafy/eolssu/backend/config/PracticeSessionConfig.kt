package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.practice.application.GetPracticeSession
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionDetails
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.MyBatisPracticeSessionDetails
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// 상태 조회가 제한 시간을 넘긴 PENDING을 TIMEOUT으로 바꾸는 쓰기 작업이라 db 프로필에서만 만든다.
@Configuration(proxyBeanMethods = false)
@Profile("db")
class PracticeSessionConfig {
    @Bean
    fun practiceSessionDetails(mapper: PracticeSessionMapper): PracticeSessionDetails = MyBatisPracticeSessionDetails(mapper)

    @Bean
    fun getPracticeSession(details: PracticeSessionDetails, analyses: PracticeAnalysisRepository, properties: AnalysisProperties) =
        GetPracticeSession(details, analyses::expireOverdue, Clock.systemUTC(), properties.timeLimit)
}

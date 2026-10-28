package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.practice.application.CreatePracticeSession
import com.ssafy.eolssu.backend.feature.practice.domain.PracticeSessionCreationRepository
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.MyBatisPracticeSessionCreationRepository
import com.ssafy.eolssu.backend.feature.practice.infrastructure.persistence.PracticeSessionMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db")
class PracticeSessionConfig {
    @Bean
    fun practiceSessionCreationRepository(mapper: PracticeSessionMapper): PracticeSessionCreationRepository =
        MyBatisPracticeSessionCreationRepository(mapper)

    @Bean
    fun createPracticeSession(repository: PracticeSessionCreationRepository, clock: Clock) = CreatePracticeSession(repository, clock)
}

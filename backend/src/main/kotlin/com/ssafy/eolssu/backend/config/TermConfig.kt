package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.term.application.ListTerms
import com.ssafy.eolssu.backend.feature.term.domain.TermRepository
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.MyBatisTermRepository
import com.ssafy.eolssu.backend.feature.term.infrastructure.persistence.TermMapper
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db")
class TermConfig {
    @Bean
    fun termClock(): Clock = Clock.systemUTC()

    @Bean
    fun termRepository(mapper: TermMapper): TermRepository = MyBatisTermRepository(mapper)

    @Bean
    fun listTerms(repository: TermRepository, clock: Clock) = ListTerms(repository, clock)
}

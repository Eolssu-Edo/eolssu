package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogReferenceRepository
import com.ssafy.eolssu.backend.feature.practice.application.NotePracticeCriteriaQuery
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeCriteria
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** 악기 code별 한 음씩 연습 기준. 값은 AI 미확정 초안이다(docs/specs/note-practice-criteria.md). */
@ConfigurationProperties("eolssu.note-practice")
data class NotePracticeProperties(val criteria: Map<String, NotePracticeCriteria> = emptyMap())

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class NotePracticeConfig {
    @Bean
    fun notePracticeCriteriaQuery(instruments: CatalogReferenceRepository, properties: NotePracticeProperties) =
        NotePracticeCriteriaQuery({ instruments.activeInstrument(it)?.code }, properties.criteria)
}
